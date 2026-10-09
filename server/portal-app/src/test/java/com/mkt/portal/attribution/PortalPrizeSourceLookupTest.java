package com.mkt.portal.attribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.activity.application.ActivityPortalAppService;
import com.mkt.activity.convert.ActivityOwnership;
import com.mkt.reward.application.PrizeSourceLookup;
import com.mkt.signin.application.SigninPortalAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.response.TaskStepSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalPrizeSourceLookupTest {

    @Test
    void taskStepJoinsTaskAndOwningActivities() {
        TaskPortalAppService tasks = mock(TaskPortalAppService.class);
        ActivityPortalAppService activities = mock(ActivityPortalAppService.class);
        SigninPortalAppService signin = mock(SigninPortalAppService.class);
        when(tasks.sourceForStep(77L)).thenReturn(new TaskStepSource(22L, "每日浏览"));
        when(activities.ownershipForTask(22L))
                .thenReturn(List.of(new ActivityOwnership.Ref(3L, "夏季专题")));
        PortalPrizeSourceLookup lookup = new PortalPrizeSourceLookup(tasks, activities, signin);
        PrizeSourceLookup.SourceRef ref = lookup.resolve("TASK_STEP", "77");
        assertThat(ref.sourceTaskId()).isEqualTo(22L);
        assertThat(ref.sourceTaskName()).isEqualTo("每日浏览");
        assertThat(ref.activityId()).isEqualTo(3L);
        assertThat(ref.activityName()).isEqualTo("夏季专题");
    }

    @Test
    void participationAndSigninResolveActivityWithoutTask() {
        TaskPortalAppService tasks = mock(TaskPortalAppService.class);
        ActivityPortalAppService activities = mock(ActivityPortalAppService.class);
        SigninPortalAppService signin = mock(SigninPortalAppService.class);
        when(activities.ownershipForParticipation(15L)).thenReturn(new ActivityOwnership.Ref(4L, "参与礼"));
        when(signin.activityName(8L)).thenReturn("每日签到");
        PortalPrizeSourceLookup lookup = new PortalPrizeSourceLookup(tasks, activities, signin);
        PrizeSourceLookup.SourceRef fromAct = lookup.resolve("ACTIVITY_PARTICIPATION", "15");
        assertThat(fromAct.activityId()).isEqualTo(4L);
        assertThat(fromAct.activityName()).isEqualTo("参与礼");
        assertThat(fromAct.sourceTaskId()).isNull();
        PrizeSourceLookup.SourceRef fromSign = lookup.resolve("SIGNIN_DAY", "8:9:1");
        assertThat(fromSign.activityId()).isEqualTo(8L);
        assertThat(fromSign.activityName()).isEqualTo("每日签到");
    }
}
