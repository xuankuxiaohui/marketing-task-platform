package com.mkt.admin.arch;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F06 / DEC-003: registered admin-app aggregate exceptions for cross-domain SQL.
 *
 * <p>Table ownership stays in data domains; admin does not gain ownership. Source tables are
 * read-only; writes are allowed only to {@code mtr_*} for the aggregate job.
 */
public final class AdminAggregateSqlInventory {

    /** Domain-owned table prefixes that admin-app may not touch unless registered below. */
    public static final List<String> FOREIGN_TABLE_PREFIXES = List.of(
            "task_", "rwd_", "pnt_", "risk_", "evt_", "sgn_", "act_", "ad_", "mtr_");

    public record Entry(String simpleClassName, Set<String> readTables, Set<String> writeTables) {
        public Entry {
            readTables = Set.copyOf(readTables);
            writeTables = Set.copyOf(writeTables);
            for (String table : writeTables) {
                if (!table.startsWith("mtr_")) {
                    throw new IllegalArgumentException("aggregate writes only to mtr_*: " + table);
                }
            }
        }

        public Set<String> allowedTables() {
            return Set.copyOf(java.util.stream.Stream.concat(readTables.stream(), writeTables.stream())
                    .collect(Collectors.toSet()));
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry(
                    "MetricsAggregateService",
                    Set.of("evt_event_log", "rwd_grant_record", "risk_hit_log"),
                    Set.of(
                            "mtr_task_funnel_d",
                            "mtr_reward_spend_d",
                            "mtr_risk_hit_d",
                            "mtr_ad_material_d")),
            new Entry(
                    "MetricsQueryService",
                    Set.of(
                            "mtr_task_funnel_d",
                            "mtr_reward_spend_d",
                            "mtr_risk_hit_d",
                            "mtr_ad_material_d",
                            "rwd_prize"),
                    Set.of()),
            new Entry(
                    "JdbcSimulateGrantLookup",
                    Set.of("rwd_grant_record", "rwd_stock_log", "pnt_transaction"),
                    Set.of()));

    private AdminAggregateSqlInventory() {}

    public static Map<String, Entry> bySimpleClassName() {
        return ENTRIES.stream().collect(Collectors.toUnmodifiableMap(Entry::simpleClassName, e -> e));
    }
}
