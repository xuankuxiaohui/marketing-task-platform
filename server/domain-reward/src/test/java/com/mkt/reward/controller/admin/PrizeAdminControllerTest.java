package com.mkt.reward.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.kernel.PageData;
import com.mkt.reward.application.PrizeAppService;
import com.mkt.reward.application.PrizeCategoryAppService;
import com.mkt.reward.command.PrizeCategorySaveCommand;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.response.OkResponse;
import com.mkt.reward.response.PrizeCategoryResponse;
import com.mkt.reward.response.PrizeImpactResponse;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockReplenishResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrizeAdminControllerTest {

    @Test
    void controllersDelegate() {
        PrizeCategoryAppService categories = mock(PrizeCategoryAppService.class);
        when(categories.page(any())).thenReturn(new PageData<>(0, List.of()));
        when(categories.create(any()))
                .thenReturn(new PrizeCategoryResponse(
                        "TOKEN_A",
                        "n",
                        "PLATFORM",
                        "INSTANT",
                        "NONE",
                        false,
                        "REVIEW",
                        null,
                        Map.of(),
                        false,
                        "ENABLED",
                        Instant.EPOCH));
        PrizeCategoryAdminController catCtl = new PrizeCategoryAdminController(categories);
        assertThat(catCtl.page(1, 20).data().total()).isZero();
        assertThat(catCtl.create(new PrizeCategorySaveCommand(
                        "TOKEN_A", "n", "PLATFORM", "INSTANT", "NONE", false, "REVIEW", null, null))
                .data()
                .code())
                .isEqualTo("TOKEN_A");
        assertThat(catCtl.delete("TOKEN_A").data()).isEqualTo(OkResponse.yes());

        PrizeAppService prizes = mock(PrizeAppService.class);
        when(prizes.create(any()))
                .thenReturn(new PrizeResponse(
                        8L,
                        "pts_a",
                        "n",
                        null,
                        null,
                        "POINTS",
                        Map.of("points", 10),
                        "PLATFORM",
                        "INSTANT",
                        null,
                        10,
                        10,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        "AUTO",
                        null,
                        null,
                        null,
                        "DRAFT",
                        Map.of(),
                        Instant.EPOCH));
        when(prizes.disable(eq(8L), any())).thenReturn(new PrizeImpactResponse(false, 1, 2));
        when(prizes.replenish(eq(8L), any())).thenReturn(new StockReplenishResponse(15));
        PrizeAdminController prizeCtl = new PrizeAdminController(prizes);
        assertThat(prizeCtl.create(new PrizeSaveCommand(
                        "pts_a",
                        "n",
                        null,
                        null,
                        "POINTS",
                        Map.of("points", 10),
                        null,
                        10,
                        0,
                        0,
                        null,
                        null,
                        null,
                        "AUTO",
                        null,
                        null,
                        null,
                        null))
                .data()
                .id())
                .isEqualTo(8L);
        assertThat(prizeCtl.disable(8L, new PrizeConfirmCommand(false)).data().inFlightInstanceCount())
                .isEqualTo(2);
        assertThat(prizeCtl.replenish(8L, new StockReplenishCommand(5, "补")).data().remainingStock())
                .isEqualTo(15);
    }
}
