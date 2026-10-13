package com.mkt.identity.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.application.DictAppService;
import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictEntryUpdateCommand;
import com.mkt.identity.entity.DictEntryEntity;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * WebMvc slice (standalone MockMvc) for {@link DictEntryAdminController}: real {@link SaInterceptor} +
 * real {@link SaTokenExceptionHandler}; a test StpLogic grants a fixed permission set.
 */
class DictEntryAdminControllerTest {

    private static final String VALID_CREATE =
            "{\"typeCode\":\"province\",\"label\":\"广东\",\"value\":\"GD\",\"sort\":1}";
    private static final String VALID_UPDATE =
            "{\"label\":\"广东省\",\"value\":\"GD\",\"sort\":2,\"status\":\"DISABLED\"}";

    private final DictAppService appService = mock(DictAppService.class);
    private final GrantedStpLogic stp = new GrantedStpLogic();
    private StpLogic original;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        original = StpUtil.stpLogic;
        StpUtil.setStpLogic(stp);
        mvc = MockMvcBuilders.standaloneSetup(new DictEntryAdminController(appService))
                .addInterceptors(new SaInterceptor())
                .setControllerAdvice(new SaTokenExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(original);
    }

    @Test
    void createWithoutPermissionIs403() throws Exception {
        mvc.perform(post("/admin/system/dict-entries").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void updateAndDeleteRequireTheirOwnPermission() throws Exception {
        stp.grant(IdentityPermissions.DICT_ENTRY_CREATE);
        mvc.perform(put("/admin/system/dict-entries/9").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        mvc.perform(delete("/admin/system/dict-entries/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void createWithMissingFieldsIs400() throws Exception {
        stp.grant(IdentityPermissions.DICT_ENTRY_CREATE);
        mvc.perform(post("/admin/system/dict-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeCode\":\"province\",\"label\":\"\",\"value\":\"GD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void updateWithoutStatusIs400() throws Exception {
        stp.grant(IdentityPermissions.DICT_ENTRY_UPDATE);
        mvc.perform(put("/admin/system/dict-entries/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"广东\",\"value\":\"GD\",\"sort\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void createHappyPathReturnsId() throws Exception {
        stp.grant(IdentityPermissions.DICT_ENTRY_CREATE);
        DictEntryEntity created = new DictEntryEntity();
        created.setId(31L);
        when(appService.createEntry(any())).thenReturn(created);
        mvc.perform(post("/admin/system/dict-entries").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(31));
        ArgumentCaptor<DictEntryCreateCommand> command = ArgumentCaptor.forClass(DictEntryCreateCommand.class);
        verify(appService).createEntry(command.capture());
        assertThat(command.getValue().typeCode()).isEqualTo("province");
        assertThat(command.getValue().value()).isEqualTo("GD");
    }

    @Test
    void updateAndDeleteHappyPath() throws Exception {
        stp.grant(IdentityPermissions.DICT_ENTRY_UPDATE, IdentityPermissions.DICT_ENTRY_DELETE);
        mvc.perform(put("/admin/system/dict-entries/9").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        ArgumentCaptor<DictEntryUpdateCommand> command = ArgumentCaptor.forClass(DictEntryUpdateCommand.class);
        verify(appService).updateEntry(eq(9L), command.capture());
        assertThat(command.getValue().status()).isEqualTo("DISABLED");

        mvc.perform(delete("/admin/system/dict-entries/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(appService).deleteEntry(9L);
    }

    /** Grants a fixed permission set; otherwise throws the real Sa-Token {@link NotPermissionException}. */
    static final class GrantedStpLogic extends StpLogic {

        private final Set<String> granted = new HashSet<>();

        GrantedStpLogic() {
            super("login");
        }

        void grant(String... permissions) {
            granted.addAll(List.of(permissions));
        }

        @Override
        public void checkPermission(String permission) {
            if (!granted.contains(permission)) {
                throw new NotPermissionException(permission);
            }
        }

        @Override
        public void checkPermissionAnd(String... permissions) {
            for (String permission : permissions) {
                checkPermission(permission);
            }
        }

        @Override
        public void checkPermissionOr(String... permissions) {
            for (String permission : permissions) {
                if (granted.contains(permission)) {
                    return;
                }
            }
            throw new NotPermissionException(permissions.length == 0 ? "" : permissions[0]);
        }
    }
}
