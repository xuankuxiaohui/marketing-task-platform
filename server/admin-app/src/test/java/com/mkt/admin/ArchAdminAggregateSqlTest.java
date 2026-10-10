package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.admin.arch.AdminAggregateSqlInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * F06 / DEC-003: inventory + static gate for admin-app cross-domain SQL.
 *
 * <p>Scans string literals in {@code admin-app/src/main} for SQL table refs (FROM/INTO/JOIN/UPDATE /
 * DELETE FROM, or a literal that is exactly a prefixed table name). Unregistered classes or tables
 * outside a registered whitelist fail. Identifier names and OpenAPI prose are ignored.
 */
class ArchAdminAggregateSqlTest {

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

    private static final Pattern SQL_TABLE = Pattern.compile(
            "(?i)(?:\\bFROM|\\bINTO|\\bJOIN|\\bUPDATE|DELETE\\s+FROM)\\s+((?:task|rwd|pnt|risk|evt|sgn|act|ad|mtr)_[a-z0-9_]+)");

    /** Exact table-name literals (deleteOlder("mtr_...")); must be lowercase like Flyway names. */
    private static final Pattern EXACT_TABLE = Pattern.compile(
            "^((?:task|rwd|pnt|risk|evt|sgn|act|ad|mtr)_[a-z0-9_]+)$");

    @Test
    void inventoryRegistersExactlyTheThreeAggregateExceptions() {
        assertThat(AdminAggregateSqlInventory.ENTRIES)
                .extracting(AdminAggregateSqlInventory.Entry::simpleClassName)
                .containsExactly(
                        "MetricsAggregateService", "MetricsQueryService", "JdbcSimulateGrantLookup");
        for (AdminAggregateSqlInventory.Entry entry : AdminAggregateSqlInventory.ENTRIES) {
            for (String write : entry.writeTables()) {
                assertThat(write).startsWith("mtr_");
            }
        }
    }

    @Test
    void registeredClassesOnlyTouchWhitelistedTables() throws IOException {
        Map<String, Set<String>> found = scanAdminMainTables();
        Map<String, AdminAggregateSqlInventory.Entry> inventory = AdminAggregateSqlInventory.bySimpleClassName();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> hit : found.entrySet()) {
            String simple = hit.getKey();
            AdminAggregateSqlInventory.Entry entry = inventory.get(simple);
            if (entry == null) {
                violations.add(simple + " touches " + hit.getValue() + " but is not registered");
                continue;
            }
            Set<String> allowed = entry.allowedTables();
            Set<String> extra = new LinkedHashSet<>(hit.getValue());
            extra.removeAll(allowed);
            if (!extra.isEmpty()) {
                violations.add(simple + " uses unlisted tables " + extra + "; allowed=" + allowed);
            }
        }
        for (String registered : inventory.keySet()) {
            if (!found.containsKey(registered)) {
                violations.add(registered + " is registered but no SQL table tokens found in sources");
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void unregisteredCrossDomainSqlFailsGate() {
        String fakeSource =
                "package demo;\nclass RogueAdminReader {\n  String sql = \"SELECT id FROM rwd_grant_record\";\n}\n";
        Map<String, Set<String>> found = tablesBySimpleClass(Map.of("RogueAdminReader.java", fakeSource));
        assertThat(found).containsKey("RogueAdminReader");
        assertThat(found.get("RogueAdminReader")).contains("rwd_grant_record");
        assertThatThrownBy(() -> assertRegistered(found))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueAdminReader");
    }

    @Test
    void proseAndEnumLikeStringsAreIgnored() {
        String prose = "class MetricsAdminController {\n  String d = \"读 mtr_task_funnel_d\";\n}\n";
        String enumLike =
                "class GrantResumeConfiguration {\n  String s = \"TASK_STEP\";\n  String sql = \"WHERE grant_source = 'TASK_STEP'\";\n}\n";
        assertThat(tablesBySimpleClass(Map.of("MetricsAdminController.java", prose))).isEmpty();
        assertThat(tablesBySimpleClass(Map.of("GrantResumeConfiguration.java", enumLike))).isEmpty();
    }

    private static void assertRegistered(Map<String, Set<String>> found) {
        Map<String, AdminAggregateSqlInventory.Entry> inventory = AdminAggregateSqlInventory.bySimpleClassName();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> hit : found.entrySet()) {
            if (!inventory.containsKey(hit.getKey())) {
                violations.add(hit.getKey() + " touches " + hit.getValue() + " but is not registered");
            }
        }
        assertThat(violations).as("unregistered cross-domain SQL").isEmpty();
    }

    private static Map<String, Set<String>> scanAdminMainTables() throws IOException {
        Path root = serverRoot().resolve("admin-app/src/main/java");
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    sources.put(path.getFileName().toString(), Files.readString(path));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        return tablesBySimpleClass(sources);
    }

    static Map<String, Set<String>> tablesBySimpleClass(Map<String, String> sourcesByFileName) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : sourcesByFileName.entrySet()) {
            String simple = file.getKey().replace(".java", "");
            if ("AdminAggregateSqlInventory".equals(simple)) {
                continue;
            }
            Set<String> tables = tablesInSource(file.getValue());
            if (!tables.isEmpty()) {
                out.put(simple, tables);
            }
        }
        return out;
    }

    static Set<String> tablesInSource(String source) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher strings = STRING_LITERAL.matcher(source);
        while (strings.find()) {
            String literal = strings.group(1).replace("\\\"", "\"");
            Matcher sql = SQL_TABLE.matcher(literal);
            while (sql.find()) {
                tables.add(sql.group(1).toLowerCase());
            }
            Matcher exact = EXACT_TABLE.matcher(literal.trim());
            if (exact.matches()) {
                tables.add(exact.group(1).toLowerCase());
            }
        }
        return tables;
    }

    private static Path serverRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : List.of(cwd, cwd.getParent())) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("admin-app/pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate server/ from " + cwd);
    }
}
