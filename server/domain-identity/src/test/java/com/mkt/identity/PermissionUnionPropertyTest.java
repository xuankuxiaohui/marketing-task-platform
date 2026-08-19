package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.domain.PermissionUnion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R2.1: union == any enabled role holds the code; disabled role codes are excluded and bindings kept.
 */
class PermissionUnionPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(PermissionUnionPropertyTest.class);

    @Property(tries = 200)
    void unionEqualsAnyEnabledRole(@ForAll("graphs") Graph graph) {
        log.debug(
                "PermissionUnionPropertyTest roles={} codes={} disabled={}",
                graph.roles().size(),
                graph.permissionCodes().size(),
                graph.roles().stream().filter(r -> !r.enabled()).count());
        Set<String> union = PermissionUnion.ofEnabledRoles(graph.roles());
        for (String code : graph.permissionCodes()) {
            boolean anyEnabled = false;
            for (PermissionUnion.RoleSlice role : graph.roles()) {
                if (role.enabled() && role.operationCodes().contains(code)) {
                    anyEnabled = true;
                    break;
                }
            }
            assertThat(PermissionUnion.allows(graph.roles(), code)).isEqualTo(anyEnabled);
            assertThat(union.contains(code)).isEqualTo(anyEnabled);
        }
        for (PermissionUnion.RoleSlice role : graph.roles()) {
            if (!role.enabled()) {
                assertThat(graph.bindings().get(role)).containsAll(role.operationCodes());
            }
        }
        Set<String> disabledOnly = new HashSet<>();
        for (PermissionUnion.RoleSlice role : graph.roles()) {
            if (!role.enabled()) {
                disabledOnly.addAll(role.operationCodes());
            }
        }
        for (PermissionUnion.RoleSlice role : graph.roles()) {
            if (role.enabled()) {
                disabledOnly.removeAll(role.operationCodes());
            }
        }
        if (!disabledOnly.isEmpty()) {
            assertThat(union).doesNotContainAnyElementsOf(disabledOnly);
        }
    }

    @Provide
    Arbitrary<Graph> graphs() {
        Arbitrary<List<String>> codes = Arbitraries.integers()
                .between(1, 20)
                .map(n -> {
                    List<String> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add("identity:role:p" + i);
                    }
                    return list;
                });
        return codes.flatMap(permissionCodes -> Arbitraries.integers()
                .between(1, 8)
                .flatMap(roleCount -> Combinators.combine(
                                Arbitraries.of(true, false).list().ofSize(roleCount),
                                subsets(permissionCodes).list().ofSize(roleCount))
                        .as((enabledFlags, bindings) -> {
                            List<PermissionUnion.RoleSlice> roles = new ArrayList<>(roleCount);
                            java.util.IdentityHashMap<PermissionUnion.RoleSlice, Set<String>> kept =
                                    new java.util.IdentityHashMap<>();
                            for (int i = 0; i < roleCount; i++) {
                                Set<String> bound = new LinkedHashSet<>(bindings.get(i));
                                PermissionUnion.RoleSlice slice =
                                        new PermissionUnion.RoleSlice(enabledFlags.get(i), false, bound);
                                roles.add(slice);
                                kept.put(slice, Set.copyOf(bound));
                            }
                            return new Graph(roles, permissionCodes, kept);
                        })));
    }

    private static Arbitrary<Set<String>> subsets(List<String> universe) {
        return Arbitraries.of(universe).set().ofMinSize(0).ofMaxSize(universe.size());
    }

    record Graph(
            List<PermissionUnion.RoleSlice> roles,
            List<String> permissionCodes,
            java.util.IdentityHashMap<PermissionUnion.RoleSlice, Set<String>> bindings) {}
}
