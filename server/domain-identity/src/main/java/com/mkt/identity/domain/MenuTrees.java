package com.mkt.identity.domain;

import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.response.MenuNodeResponse;
import com.mkt.identity.response.PermissionTreeNodeResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the full permission tree and the current-user menu tree (R2.4 / §4.10). */
public final class MenuTrees {

    private static final Comparator<PermissionEntity> ORDER =
            Comparator.comparingInt((PermissionEntity p) -> p.getSort() == null ? 0 : p.getSort())
                    .thenComparingLong(p -> p.getId() == null ? 0L : p.getId());

    private MenuTrees() {}

    public static List<PermissionTreeNodeResponse> fullTree(List<PermissionEntity> nodes) {
        return buildPermissionTree(nodes, null);
    }

    public static List<MenuNodeResponse> authorizedMenus(List<PermissionEntity> nodes, Set<Long> heldIds) {
        Set<Long> visibleMenus = visibleMenuIds(nodes, heldIds == null ? Set.of() : heldIds);
        List<PermissionEntity> menus = new ArrayList<>();
        for (PermissionEntity node : nodes) {
            if (node != null
                    && PermissionTypes.isMenu(node.getType())
                    && visibleMenus.contains(node.getId())
                    && RoleStatuses.ENABLED.equals(node.getStatus())) {
                menus.add(node);
            }
        }
        return buildMenuTree(menus);
    }

    static Set<Long> visibleMenuIds(List<PermissionEntity> nodes, Set<Long> heldIds) {
        Map<Long, PermissionEntity> byId = index(nodes);
        Set<Long> visible = new HashSet<>();
        for (Long held : heldIds) {
            Long current = held;
            while (current != null && current != 0L) {
                PermissionEntity node = byId.get(current);
                if (node == null) {
                    break;
                }
                if (PermissionTypes.isMenu(node.getType())) {
                    visible.add(node.getId());
                }
                current = node.getParentId();
            }
        }
        return visible;
    }

    private static List<PermissionTreeNodeResponse> buildPermissionTree(
            List<PermissionEntity> nodes, Set<Long> include) {
        Map<Long, List<PermissionEntity>> children = childrenByParent(nodes, include);
        return toPermissionNodes(children.getOrDefault(0L, List.of()), children);
    }

    private static List<MenuNodeResponse> buildMenuTree(List<PermissionEntity> menus) {
        Map<Long, List<PermissionEntity>> children = childrenByParent(menus, null);
        return toMenuNodes(children.getOrDefault(0L, List.of()), children);
    }

    private static List<PermissionTreeNodeResponse> toPermissionNodes(
            List<PermissionEntity> level, Map<Long, List<PermissionEntity>> children) {
        List<PermissionTreeNodeResponse> out = new ArrayList<>(level.size());
        for (PermissionEntity node : level) {
            List<PermissionTreeNodeResponse> kids =
                    toPermissionNodes(children.getOrDefault(node.getId(), List.of()), children);
            out.add(new PermissionTreeNodeResponse(
                    node.getId(),
                    node.getParentId() == null ? 0L : node.getParentId(),
                    node.getType(),
                    node.getCode(),
                    node.getName(),
                    node.getRoute(),
                    node.getComponent(),
                    node.getIcon(),
                    node.getSort() == null ? 0 : node.getSort(),
                    node.getStatus(),
                    kids));
        }
        return List.copyOf(out);
    }

    private static List<MenuNodeResponse> toMenuNodes(
            List<PermissionEntity> level, Map<Long, List<PermissionEntity>> children) {
        List<MenuNodeResponse> out = new ArrayList<>(level.size());
        for (PermissionEntity node : level) {
            List<MenuNodeResponse> kids = toMenuNodes(children.getOrDefault(node.getId(), List.of()), children);
            out.add(new MenuNodeResponse(
                    node.getId(),
                    node.getName(),
                    node.getRoute(),
                    node.getComponent(),
                    node.getIcon(),
                    node.getSort() == null ? 0 : node.getSort(),
                    kids));
        }
        return List.copyOf(out);
    }

    private static Map<Long, List<PermissionEntity>> childrenByParent(
            List<PermissionEntity> nodes, Set<Long> include) {
        List<PermissionEntity> copy = new ArrayList<>();
        for (PermissionEntity node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            if (include != null && !include.contains(node.getId())) {
                continue;
            }
            copy.add(node);
        }
        copy.sort(ORDER);
        Map<Long, List<PermissionEntity>> children = new HashMap<>();
        for (PermissionEntity node : copy) {
            long parent = node.getParentId() == null ? 0L : node.getParentId();
            children.computeIfAbsent(parent, key -> new ArrayList<>()).add(node);
        }
        return children;
    }

    private static Map<Long, PermissionEntity> index(List<PermissionEntity> nodes) {
        Map<Long, PermissionEntity> byId = new HashMap<>();
        for (PermissionEntity node : nodes) {
            if (node != null && node.getId() != null) {
                byId.put(node.getId(), node);
            }
        }
        return byId;
    }
}
