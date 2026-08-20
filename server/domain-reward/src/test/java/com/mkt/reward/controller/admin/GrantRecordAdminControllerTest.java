package com.mkt.reward.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.command.ManualGrantCommand;
import com.mkt.reward.response.GrantRetryResponse;
import com.mkt.reward.response.ManualGrantResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantRecordAdminControllerTest {

    @Test
    void controllersDelegate() {
        GrantAppService grants = mock(GrantAppService.class);
        when(grants.retry(8L)).thenReturn(new GrantRetryResponse("GRANTED", 0));
        when(grants.manualGrant(any())).thenReturn(new ManualGrantResponse(11L, "GRANTED"));
        GrantRecordAdminController controller = new GrantRecordAdminController(grants);
        assertThat(controller.retry(8L).data().status()).isEqualTo("GRANTED");
        assertThat(controller.manualGrant(new ManualGrantCommand(1L, 2L, "补发", List.of())).data().recordId())
                .isEqualTo(11L);
    }
}
