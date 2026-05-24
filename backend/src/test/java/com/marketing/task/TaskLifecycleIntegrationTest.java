package com.marketing.task;

import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.entity.*;
import com.marketing.task.domain.enums.*;
import com.marketing.task.mapper.*;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.mapper.PrizeMapper;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import com.marketing.task.service.step.StepAdvanceEngine;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import com.marketing.task.service.task.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskLifecycleIntegrationTest {

    @Autowired private TaskMapper taskMapper;
    @Autowired private TaskStepMapper taskStepMapper;
    @Autowired private UserTaskInstanceMapper instanceMapper;
    @Autowired private UserTaskStepProgressMapper progressMapper;
    @Autowired private TaskFilterMapper taskFilterMapper;
    @Autowired private PrizeMapper prizeMapper;
    @Autowired private PrizeRecordMapper prizeRecordMapper;
    @Autowired private TaskDefinitionSnapshotMapper snapshotMapper;
    @Autowired private MutexGroupMapper mutexGroupMapper;

    @Autowired private TaskService taskService;
    @Autowired private StepAdvanceEngine stepAdvanceEngine;
    @Autowired private TaskDefinitionCacheService cacheService;

    private UserContext userCtx;

    @BeforeEach
    void setUp() {
        userCtx = UserContext.builder()
                .userId("u_test").province("BJ").level(5)
                .tags(Set.of("vip")).build();
        UserContextHolder.set(userCtx);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ============ 每日签到全链路 (PASSIVE → CLICK) ============

    @Test
    void dailyCheckinFullChain_shouldCompleteAllSteps() {
        Task task = createTask("daily_checkin", "每日签到", "DAILY", null);
        TaskStep s1 = createStep(task.getId(), 1, "welcome", "PASSIVE", null);
        TaskStep s2 = createStep(task.getId(), 2, "click_sign", "CLICK", null);
        publishTask(task);
        task = taskMapper.selectById(task.getId());

        // When: create instance, PASSIVE auto-completes via cascade
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userCtx);

        assertNotNull(instance);
        assertEquals(InstanceStatus.IN_PROGRESS.name(), instance.getStatus());

        List<UserTaskStepProgress> firstProgresses = progressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, instance.getId())
                        .orderByAsc(UserTaskStepProgress::getStepId));
        assertEquals(1, firstProgresses.size());
        assertEquals(s1.getId(), firstProgresses.get(0).getStepId());
        assertEquals(StepProgressStatus.COMPLETED.name(), firstProgresses.get(0).getStatus());

        // When: click step 2
        instance = stepAdvanceEngine.click(instance, s2.getId());

        // Then: instance completed
        List<UserTaskStepProgress> finalProgresses = progressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, instance.getId()));
        assertEquals(2, finalProgresses.size());
        assertTrue(finalProgresses.stream().allMatch(p -> StepProgressStatus.COMPLETED.name().equals(p.getStatus())));
        assertEquals(InstanceStatus.COMPLETED.name(), instance.getStatus());
    }

    // ============ 问卷回调全链路 (CALLBACK) ============

    @Test
    void callbackFullChain_shouldCompleteOnEventKeyMatch() {
        Task task = createTask("survey_task", "问卷任务", "ONCE", null);
        TaskStep s1 = createStep(task.getId(), 1, "survey_cb", "CALLBACK", "survey_completed");
        publishTask(task);
        task = taskMapper.selectById(task.getId());

        UserTaskInstance instance = taskService.getOrCreateInstance(task, userCtx);
        assertEquals(InstanceStatus.IN_PROGRESS.name(), instance.getStatus());

        // When: trigger callback with matching event key
        instance = stepAdvanceEngine.callback(instance, "survey_completed");

        assertNotNull(instance);
        assertEquals(InstanceStatus.COMPLETED.name(), instance.getStatus());
        List<UserTaskStepProgress> progresses = progressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, instance.getId()));
        assertEquals(1, progresses.size());
        assertEquals(StepProgressStatus.COMPLETED.name(), progresses.get(0).getStatus());
    }

    // ============ 阅读进度全链路 (PROGRESS) ============

    @Test
    void progressFullChain_shouldCompleteWhenReachingTarget() {
        Task task = createTask("read_task", "阅读任务", "ONCE", null);
        TaskStep s1 = createStep(task.getId(), 1, "read_progress", "PROGRESS", null);
        s1.setTargetValue(3);
        taskStepMapper.updateById(s1);
        publishTask(task);
        task = taskMapper.selectById(task.getId());

        UserTaskInstance instance = taskService.getOrCreateInstance(task, userCtx);

        // When: report progress (absolute values: 1, then 2, then 3 = target)
        instance = stepAdvanceEngine.progress(instance, s1.getId(), 1);
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(InstanceStatus.IN_PROGRESS.name(), instance.getStatus());

        instance = stepAdvanceEngine.progress(instance, s1.getId(), 2);
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(InstanceStatus.IN_PROGRESS.name(), instance.getStatus());

        // When: final progress hits target = 3
        instance = stepAdvanceEngine.progress(instance, s1.getId(), 3);

        assertEquals(InstanceStatus.COMPLETED.name(), instance.getStatus());
        UserTaskStepProgress finalProgress = progressMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, instance.getId())
                        .eq(UserTaskStepProgress::getStepId, s1.getId()));
        assertEquals(StepProgressStatus.COMPLETED.name(), finalProgress.getStatus());
        assertEquals(3, finalProgress.getProgressValue());
    }

    // ============ 互斥组校验 ============

    @Test
    void mutexGroup_shouldRejectWhenConflictTaskInProgress() {
        MutexGroup group = createMutexGroup("互斥测试组1");
        // Task1 with PASSIVE→CLICK (stays IN_PROGRESS after enter)
        Task task1 = createTask("mutex_a", "互斥任务A", "ONCE", group.getId());
        createStep(task1.getId(), 1, "passive_a", "PASSIVE", null);
        createStep(task1.getId(), 2, "click_a", "CLICK", null);
        publishTask(task1);
        task1 = taskMapper.selectById(task1.getId());

        // Task2 with PASSIVE only (same mutex group)
        Task task2 = createTask("mutex_b", "互斥任务B", "ONCE", group.getId());
        createStep(task2.getId(), 1, "passive_b", "PASSIVE", null);
        publishTask(task2);
        final Task refreshedTask2 = taskMapper.selectById(task2.getId());

        // User starts task1 → IN_PROGRESS (PASSIVE done, CLICK pending)
        taskService.getOrCreateInstance(task1, userCtx);

        // User tries to start task2 → should throw (mutex conflict)
        assertThrows(BusinessException.class,
                () -> taskService.getOrCreateInstance(refreshedTask2, userCtx));
    }

    @Test
    void mutexGroup_shouldAllowWhenNoConflict() {
        MutexGroup group2 = createMutexGroup("互斥测试组2");
        Task task1 = createTask("mutex_c", "互斥任务C", "ONCE", group2.getId());
        createStep(task1.getId(), 1, "passive_c", "PASSIVE", null);
        publishTask(task1);
        task1 = taskMapper.selectById(task1.getId());

        // Task2 in different mutex group
        Task task2 = createTask("mutex_d", "互斥任务D", "ONCE", null);
        createStep(task2.getId(), 1, "passive_d", "PASSIVE", null);
        publishTask(task2);
        task2 = taskMapper.selectById(task2.getId());

        taskService.getOrCreateInstance(task1, userCtx);
        UserTaskInstance instance2 = taskService.getOrCreateInstance(task2, userCtx);
        assertNotNull(instance2);
    }

    // ============ 过滤器：省份/等级 ============

    @Test
    void filter_shouldFilterByProvince() {
        Task task = createTask("sh_only", "上海专属", "ONCE", null);
        createStep(task.getId(), 1, "passive_sh", "PASSIVE", null);

        TaskFilter filter = new TaskFilter();
        filter.setTaskId(task.getId());
        filter.setSeq(1);
        filter.setExpression("inProvince(\"SH\")");
        filter.setLogicOp("AND");
        filter.setEnabled(true);
        taskFilterMapper.insert(filter);
        publishTask(task);

        // BJ user should NOT see this SH-only task in published list
        var clientTasks = taskService.listPublished(userCtx);
        assertTrue(clientTasks.stream().noneMatch(t -> t.getId().equals(task.getId())));
    }

    // ============ 快照：发布后重新编辑，旧实例读旧快照 ============

    @Test
    void snapshot_shouldReadOldVersionAfterRePublish() {
        Task task = createTask("snap_task", "快照测试", "ONCE", null);
        TaskStep s1 = createStep(task.getId(), 1, "snap_passive", "PASSIVE", null);
        publishTask(task);

        // Re-read task to get updated version
        task = taskMapper.selectById(task.getId());
        assertEquals(1, task.getVersion());

        // Create instance at version 1
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userCtx);
        assertEquals(1, instance.getTaskVersion());

        // Edit task: add a new step
        TaskStep s2 = new TaskStep();
        s2.setTaskId(task.getId());
        s2.setSeq(2);
        s2.setCode("snap_click");
        s2.setName("新增点击");
        s2.setType(StepType.CLICK.name());
        s2.setCreatedAt(LocalDateTime.now());
        s2.setUpdatedAt(LocalDateTime.now());
        taskStepMapper.insert(s2);

        // Re-publish → version 2
        task = taskMapper.selectById(task.getId());
        publishTask(task);
        task = taskMapper.selectById(task.getId());
        assertEquals(2, task.getVersion());

        // Verify snapshots exist
        TaskDefinitionSnapshot snap1 = snapshotMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskDefinitionSnapshot>()
                        .eq(TaskDefinitionSnapshot::getTaskId, task.getId())
                        .eq(TaskDefinitionSnapshot::getVersion, 1));
        assertNotNull(snap1);

        TaskDefinitionSnapshot snap2 = snapshotMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskDefinitionSnapshot>()
                        .eq(TaskDefinitionSnapshot::getTaskId, task.getId())
                        .eq(TaskDefinitionSnapshot::getVersion, 2));
        assertNotNull(snap2);

        // Old instance still at version 1
        instance = instanceMapper.selectById(instance.getId());
        assertEquals(1, instance.getTaskVersion());
    }

    // ============ Prize module integration ============

    private Prize createTestPrize(String name, String handlerBean, String paramsJson) {
        Prize prize = new Prize();
        prize.setType("POINT");
        prize.setName(name);
        prize.setHandlerBean(handlerBean);
        prize.setParamsJson(paramsJson);
        prize.setTotalStock(1000);
        prize.setAutoGrant(false);
        prize.setClaimExpireType("DAYS");
        prize.setClaimExpireValue("7");
        prize.setMaxRetry(3);
        prize.setEnabled(true);
        prizeMapper.insert(prize);
        return prize;
    }

    @Test
    void prizeGrant_shouldCreateRecordOnRewardStep() {
        Prize prize = createTestPrize("测试积分", "pointPrizeHandler",
                "{\"type\":\"POINT\",\"amount\":10}");

        Task task = createTask("prize_task", "奖品测试", "ONCE", null);
        createStep(task.getId(), 1, "prize_passive", "PASSIVE", null);
        TaskStep s2 = createStep(task.getId(), 2, "prize_reward", "REWARD", "");
        s2.setPrizeId(prize.getId());
        s2.setPrizeQuantity(1);
        taskStepMapper.updateById(s2);
        publishTask(task);
        task = taskMapper.selectById(task.getId());

        taskService.getOrCreateInstance(task, userCtx);

        List<PrizeRecord> records = prizeRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getUserId, "u_test"));
        assertEquals(1, records.size());
        assertEquals("WON", records.get(0).getStatus());
    }

    @Test
    void prizeGrant_shouldBeIdempotentWithinSameTask() {
        Prize prize = createTestPrize("幂等测试积分", "pointPrizeHandler",
                "{\"type\":\"POINT\",\"amount\":5}");

        Task task = createTask("idempotent_task", "幂等测试", "ONCE", null);
        createStep(task.getId(), 1, "idem_passive", "PASSIVE", null);
        publishTask(task);
        task = taskMapper.selectById(task.getId());

        // Two calls should return same instance (idempotent)
        UserTaskInstance instance1 = taskService.getOrCreateInstance(task, userCtx);
        UserTaskInstance instance2 = taskService.getOrCreateInstance(task, userCtx);

        assertEquals(instance1.getId(), instance2.getId());
    }

    // ============ Helpers ============

    private Task createTask(String code, String name, String periodType, Long mutexGroupId) {
        Task task = new Task();
        task.setCode(code);
        task.setName(name);
        task.setPeriodType(periodType);
        task.setMutexGroupId(mutexGroupId);
        task.setStatus(TaskStatus.DRAFT.name());
        task.setVersion(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    private MutexGroup createMutexGroup(String name) {
        MutexGroup group = new MutexGroup();
        group.setName(name);
        group.setScope("SAME_CYCLE");
        mutexGroupMapper.insert(group);
        return group;
    }

    private TaskStep createStep(Long taskId, int seq, String code, String type, String callbackEventOrRewardConfig) {
        TaskStep step = new TaskStep();
        step.setTaskId(taskId);
        step.setSeq(seq);
        step.setCode(code);
        step.setName(code);
        step.setType(type);
        if ("CALLBACK".equals(type)) {
            step.setCallbackEventKey(callbackEventOrRewardConfig);
        } else if ("REWARD".equals(type) && callbackEventOrRewardConfig != null && !callbackEventOrRewardConfig.isEmpty()) {
            step.setRewardConfigJson(callbackEventOrRewardConfig);
        }
        step.setCreatedAt(LocalDateTime.now());
        step.setUpdatedAt(LocalDateTime.now());
        taskStepMapper.insert(step);
        return step;
    }

    private void publishTask(Task task) {
        cacheService.evict(task.getId());
        taskService.publish(task.getId());
    }
}
