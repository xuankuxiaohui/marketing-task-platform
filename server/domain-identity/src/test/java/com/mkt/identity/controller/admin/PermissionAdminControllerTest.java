package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.PermissionAppService;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.response.PermissionTreeNodeResponse;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PermissionAdminControllerTest {

    private final PermissionAppService appService = Mockito.mock(PermissionAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PermissionAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void treeCreateUpdateDelete() throws Exception {
        when(appService.tree())
                .thenReturn(List.of(new PermissionTreeNodeResponse(
                        4L, 0L, "MENU", null, "角色权限", "/system/roles", "system/role/index", null, 4, "ENABLED", List.of())));
        mvc.perform(get("/admin/identity/permissions/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].route").value("/system/roles"));

        PermissionEntity created = new PermissionEntity();
        created.setId(40L);
        when(appService.create(any())).thenReturn(created);
        mvc.perform(post("/admin/identity/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"parentId\":4,\"type\":\"OPERATION\",\"code\":\"identity:role:query\",\"name\":\"查询\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40));

        mvc.perform(put("/admin/identity/permissions/40")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"parentId\":4,\"type\":\"OPERATION\",\"code\":\"identity:role:query\",\"name\":\"查询角色\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/identity/permissions/40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }
}
