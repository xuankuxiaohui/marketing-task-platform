package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.response.MenuNodeResponse;
import com.mkt.identity.response.PermissionTreeNodeResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuTreesTest {

    @Test
    void authorizedMenusKeepAncestorsAndDropUnauthorizedSiblings() {
        PermissionEntity root = menu(1L, 0L, "系统", 1);
        PermissionEntity child = menu(2L, 1L, "角色", 1);
        PermissionEntity other = menu(3L, 1L, "用户", 2);
        PermissionEntity op = operation(10L, 2L, "identity:role:query");
        List<MenuNodeResponse> menus = MenuTrees.authorizedMenus(List.of(root, child, other, op), Set.of(10L));
        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).name()).isEqualTo("系统");
        assertThat(menus.get(0).children()).extracting(MenuNodeResponse::name).containsExactly("角色");
    }

    @Test
    void fullTreeNestsOperationsUnderMenus() {
        PermissionEntity root = menu(1L, 0L, "系统", 1);
        PermissionEntity op = operation(10L, 1L, "identity:role:query");
        List<PermissionTreeNodeResponse> tree = MenuTrees.fullTree(List.of(root, op));
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).code()).isEqualTo("identity:role:query");
    }

    private static PermissionEntity menu(long id, long parent, String name, int sort) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(id);
        entity.setParentId(parent);
        entity.setType(PermissionTypes.MENU);
        entity.setName(name);
        entity.setRoute("/r/" + id);
        entity.setComponent("c/" + id);
        entity.setSort(sort);
        entity.setStatus(RoleStatuses.ENABLED);
        return entity;
    }

    private static PermissionEntity operation(long id, long parent, String code) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(id);
        entity.setParentId(parent);
        entity.setType(PermissionTypes.OPERATION);
        entity.setCode(code);
        entity.setName(code);
        entity.setSort(0);
        entity.setStatus(RoleStatuses.ENABLED);
        return entity;
    }
}
