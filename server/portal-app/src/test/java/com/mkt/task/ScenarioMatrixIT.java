package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RiskScene;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.SkipReasons;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * feasibility §2 rows 01–23. Scenario 24 belongs to task 41; scenario25 must not exist.
 */
@Testcontainers
class ScenarioMatrixIT {

    /** Row order = feasibility-step-engine.md §2. Values are the 期望行为 column verbatim. */
    static final Map<Integer, String> EXPECTED = Map.ofEntries(
            Map.entry(1, "自动完成并级联"),
            Map.entry(2, "立即发奖并完成实例（纯发奖任务）"),
            Map.entry(3, "激活等待"),
            Map.entry(4, "完成→分支求值→推进"),
            Map.entry(5, "幂等返回，不二次推进"),
            Map.entry(6, "拒绝（步骤状态不符）"),
            Map.entry(7, "幂等"),
            Map.entry(8, "去重丢弃，不重复累加"),
            Map.entry(9, "按步骤状态不符拒绝"),
            Map.entry(10, "串行化为某一先定结局"),
            Map.entry(11, "累加、返回进行中"),
            Map.entry(12, "完成并推进"),
            Map.entry(13, "恰一次生效"),
            Map.entry(14, "恰一次生效"),
            Map.entry(15, "后序步骤被状态检查拒绝"),
            Map.entry(16, "两次累加都不丢"),
            Map.entry(17, "整级联回滚，步骤停在 ACTIVE，可重试"),
            Map.entry(18, "发放记录转永久失败，步骤跳过，实例继续"),
            Map.entry(19, "零副作用，仅命中记录"),
            Map.entry(20, "不存在外部推送，级联纯内存+行更新"),
            Map.entry(21, "拒绝且终态不变"),
            Map.entry(22, "幂等返回终态"),
            Map.entry(23, "按旧快照继续直至终态"));

    static final int CONCURRENT_ROUNDS = 20;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    static ScenarioMatrixSupport env;

    @BeforeAll
    static void open() throws Exception {
        env = new ScenarioMatrixSupport(MYSQL);
    }

    @AfterAll
    static void close() {
        if (env != null) {
            env.close();
        }
    }

    @AfterEach
    void resetFlags() {
        env.risk.rejectGrant = false;
        env.points.timeout = false;
    }

    @Test
    void mappingCovers01to23AndForbids24And25() {
        assertThat(EXPECTED).hasSize(23);
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, 23).boxed().toList());
        assertThat(EXPECTED.keySet()).doesNotContain(24, 25);
        List<String> scenarioMethods = Arrays.stream(ScenarioMatrixIT.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("scenario"))
                .toList();
        assertThat(scenarioMethods).hasSize(23);
        assertThat(scenarioMethods).noneMatch(name -> name.startsWith("scenario24") || name.startsWith("scenario25"));
        assertThat(EXPECTED.get(1)).isEqualTo("自动完成并级联");
        assertThat(EXPECTED.get(17)).contains("步骤停在 ACTIVE");
    }

    @Test
    void scenario01_首步骤PASSIVE自动级联() {
        assertThat(EXPECTED.get(1)).isEqualTo("自动完成并级联");
        long taskId = env.publish(
                "s01",
                List.of(
                        new TaskStepCommand("go", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go", "click", null, 0)));
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.stepStatus(started.instanceId(), "go")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "click")).isEqualTo(StepStatuses.ACTIVE);
    }

    @Test
    void scenario02_首步骤REWARD立即发奖并完成实例() {
        assertThat(EXPECTED.get(2)).contains("立即发奖并完成实例");
        long prizeId = env.enablePoints(5);
        long taskId = env.publish(
                "s02",
                List.of(new TaskStepCommand("rwd", "发奖", 1, "REWARD", null, prizeId)),
                List.of());
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "rwd")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.grantStatus(env.stepId(started.instanceId(), "rwd"))).isEqualTo(GrantRecordStatuses.GRANTED);
        assertThat(env.remainingStock(prizeId)).isEqualTo(4);
    }

    @Test
    void scenario03_首步骤CLICK_CALLBACK_PROGRESS激活等待() {
        assertThat(EXPECTED.get(3)).isEqualTo("激活等待");
        long clickId = env.publish(
                "s03c", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        long cbId = env.publish(
                "s03b", List.of(new TaskStepCommand("cb", "回调", 1, "CALLBACK", null, null)), List.of());
        long pgId = env.publish(
                "s03p", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 10, null)), List.of());
        TaskStartResponse click = env.start(clickId, env.nextUser());
        TaskStartResponse cb = env.start(cbId, env.nextUser());
        TaskStartResponse pg = env.start(pgId, env.nextUser());
        assertThat(env.instanceStatus(click.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.stepStatus(click.instanceId(), "click")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(cb.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.stepStatus(cb.instanceId(), "cb")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(pg.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.stepStatus(pg.instanceId(), "pg")).isEqualTo(StepStatuses.ACTIVE);
    }

    @Test
    void scenario04_click当前步骤完成并推进() {
        assertThat(EXPECTED.get(4)).isEqualTo("完成→分支求值→推进");
        long taskId = env.publish(
                "s04",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "B", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("a", "b", null, 0)));
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        env.click(started.instanceId(), "a", userId);
        assertThat(env.stepStatus(started.instanceId(), "a")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "b")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void scenario05_click已完成步骤幂等返回() {
        assertThat(EXPECTED.get(5)).contains("幂等返回");
        long taskId = env.publish(
                "s05", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        env.click(started.instanceId(), "click", userId);
        env.click(started.instanceId(), "click", userId);
        assertThat(env.stepStatus(started.instanceId(), "click")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void scenario06_click非当前步骤拒绝() {
        assertThat(EXPECTED.get(6)).contains("拒绝");
        long taskId = env.publish(
                "s06",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "B", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("a", "b", null, 0)));
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        assertThatThrownBy(() -> env.click(started.instanceId(), "b", userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
        assertThat(env.stepStatus(started.instanceId(), "a")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.stepStatus(started.instanceId(), "b")).isEqualTo(StepStatuses.INACTIVE);
    }

    @Test
    void scenario07_callback重复投递幂等() {
        assertThat(EXPECTED.get(7)).isEqualTo("幂等");
        long taskId = env.publish(
                "s07", List.of(new TaskStepCommand("cb", "回调", 1, "CALLBACK", null, null)), List.of());
        TaskStartResponse started = env.start(taskId, env.nextUser());
        env.callback(started.instanceId(), "cb", "biz-1");
        env.callback(started.instanceId(), "cb", "biz-2");
        assertThat(env.stepStatus(started.instanceId(), "cb")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void scenario08_progress重复reportId去重丢弃() {
        assertThat(EXPECTED.get(8)).contains("去重丢弃");
        long taskId = env.publish(
                "s08", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 10, null)), List.of());
        TaskStartResponse started = env.start(taskId, env.nextUser());
        env.progress(started.instanceId(), "pg", 3, "same");
        env.progress(started.instanceId(), "pg", 3, "same");
        assertThat(env.progressCurrent(started.instanceId(), "pg")).isEqualTo(3);
        assertThat(env.stepStatus(started.instanceId(), "pg")).isEqualTo(StepStatuses.ACTIVE);
        Integer reports = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                Integer.class,
                started.instanceId());
        assertThat(reports).isEqualTo(1);
    }

    @Test
    void scenario09_progress未激活或已完成步骤拒绝() {
        assertThat(EXPECTED.get(9)).contains("步骤状态不符拒绝");
        long two = env.publish(
                "s09a",
                List.of(
                        new TaskStepCommand("a", "A", 1, "PROGRESS", 5, null),
                        new TaskStepCommand("b", "B", 2, "PROGRESS", 5, null)),
                List.of(new TaskTransitionCommand("a", "b", null, 0)));
        TaskStartResponse started = env.start(two, env.nextUser());
        assertThatThrownBy(() -> env.progress(started.instanceId(), "b", 1, "r-b"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
        long one = env.publish(
                "s09c", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 1, null)), List.of());
        TaskStartResponse done = env.start(one, env.nextUser());
        env.progress(done.instanceId(), "pg", 1, "r1");
        assertThatThrownBy(() -> env.progress(done.instanceId(), "pg", 1, "r2"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
    }

    @Test
    void scenario10_放弃与推进并发串行化() throws Exception {
        assertThat(EXPECTED.get(10)).contains("串行化");
        long taskId = env.publish(
                "s10", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            long userId = env.nextUser();
            TaskStartResponse started = env.start(taskId, userId);
            runConcurrent(List.of(
                    () -> env.click(started.instanceId(), "click", userId),
                    () -> env.abandon(started.instanceId(), userId)));
            String status = env.instanceStatus(started.instanceId());
            assertThat(status).isIn(InstanceStatuses.ABANDONED, InstanceStatuses.COMPLETED);
        }
    }

    @Test
    void scenario11_progress未达标累加进行中() {
        assertThat(EXPECTED.get(11)).contains("累加");
        long taskId = env.publish(
                "s11", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 10, null)), List.of());
        TaskStartResponse started = env.start(taskId, env.nextUser());
        env.progress(started.instanceId(), "pg", 2, "r1");
        assertThat(env.progressCurrent(started.instanceId(), "pg")).isEqualTo(2);
        assertThat(env.stepStatus(started.instanceId(), "pg")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void scenario12_progress一次上报即达标完成并推进() {
        assertThat(EXPECTED.get(12)).isEqualTo("完成并推进");
        long taskId = env.publish(
                "s12", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 1, null)), List.of());
        TaskStartResponse started = env.start(taskId, env.nextUser());
        env.progress(started.instanceId(), "pg", 1, "r1");
        assertThat(env.stepStatus(started.instanceId(), "pg")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void scenario13_同步骤双click恰一次生效() throws Exception {
        assertThat(EXPECTED.get(13)).isEqualTo("恰一次生效");
        long taskId = env.publish(
                "s13", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            long userId = env.nextUser();
            TaskStartResponse started = env.start(taskId, userId);
            List<Runnable> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                jobs.add(() -> env.click(started.instanceId(), "click", userId));
            }
            runConcurrent(jobs);
            Integer completed = env.jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM task_instance_step
                    WHERE instance_id = ? AND step_code = 'click' AND status = 'COMPLETED'
                    """,
                    Integer.class,
                    started.instanceId());
            assertThat(completed).isEqualTo(1);
            assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
        }
    }

    @Test
    void scenario14_click与callback同步骤恰一次生效() throws Exception {
        assertThat(EXPECTED.get(14)).isEqualTo("恰一次生效");
        long taskId = env.publish(
                "s14", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            long userId = env.nextUser();
            TaskStartResponse started = env.start(taskId, userId);
            runConcurrent(List.of(
                    () -> env.click(started.instanceId(), "click", userId),
                    () -> env.callback(started.instanceId(), "click", "biz")));
            Integer completed = env.jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM task_instance_step
                    WHERE instance_id = ? AND step_code = 'click' AND status = 'COMPLETED'
                    """,
                    Integer.class,
                    started.instanceId());
            assertThat(completed).isEqualTo(1);
        }
    }

    @Test
    void scenario15_不同步骤乱序推进拒绝() {
        assertThat(EXPECTED.get(15)).contains("状态检查拒绝");
        long taskId = env.publish(
                "s15",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "B", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("a", "b", null, 0)));
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        assertThatThrownBy(() -> env.click(started.instanceId(), "b", userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
        assertThat(env.stepStatus(started.instanceId(), "b")).isEqualTo(StepStatuses.INACTIVE);
        assertThat(env.stepStatus(started.instanceId(), "a")).isEqualTo(StepStatuses.ACTIVE);
    }

    @Test
    void scenario16_progress双上报不同reportId两次累加都不丢() throws Exception {
        assertThat(EXPECTED.get(16)).contains("两次累加都不丢");
        long taskId = env.publish(
                "s16", List.of(new TaskStepCommand("pg", "进度", 1, "PROGRESS", 100, null)), List.of());
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            int n = round;
            TaskStartResponse started = env.start(taskId, env.nextUser());
            runConcurrent(List.of(
                    () -> env.progress(started.instanceId(), "pg", 1, "r-a-" + n),
                    () -> env.progress(started.instanceId(), "pg", 1, "r-b-" + n)));
            assertThat(env.progressCurrent(started.instanceId(), "pg")).isEqualTo(2);
            Integer reports = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                    Integer.class,
                    started.instanceId());
            assertThat(reports).isEqualTo(2);
        }
    }

    @Test
    void scenario17_REWARD可重试失败整级联回滚() {
        assertThat(EXPECTED.get(17)).contains("步骤停在 ACTIVE");
        long prizeId = env.enablePoints(5);
        int stockBefore = env.remainingStock(prizeId);
        long taskId = env.publish(
                "s17",
                List.of(new TaskStepCommand("rwd", "发奖", 1, "REWARD", null, prizeId)),
                List.of());
        env.points.timeout = true;
        TaskStartResponse started = env.start(taskId, env.nextUser());
        assertThat(env.stepStatus(started.instanceId(), "rwd")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.grantStatus(env.stepId(started.instanceId(), "rwd")))
                .isEqualTo(GrantRecordStatuses.RETRY_PENDING);
        assertThat(env.remainingStock(prizeId)).isEqualTo(stockBefore);
    }

    @Test
    void scenario18_REWARD永久失败步骤跳过实例继续() {
        assertThat(EXPECTED.get(18)).contains("步骤跳过");
        long prizeId = env.enablePoints(5);
        long taskId = env.publish(
                "s18",
                List.of(
                        new TaskStepCommand("rwd", "发奖", 1, "REWARD", null, prizeId),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("rwd", "click", null, 0)));
        env.disablePrize(prizeId);
        TaskStartResponse started = env.start(taskId, env.nextUser());
        assertThat(env.stepStatus(started.instanceId(), "rwd")).isEqualTo(StepStatuses.SKIPPED);
        assertThat(env.skipReason(started.instanceId(), "rwd")).isEqualTo(SkipReasons.GRANT_PERMANENT_FAILED);
        assertThat(env.grantStatus(env.stepId(started.instanceId(), "rwd")))
                .isEqualTo(GrantRecordStatuses.PERMANENT_FAILED);
        assertThat(env.stepStatus(started.instanceId(), "click")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void scenario19_REWARD风控拦截零副作用() {
        assertThat(EXPECTED.get(19)).isEqualTo("零副作用，仅命中记录");
        long prizeId = env.enablePoints(5);
        int stockBefore = env.remainingStock(prizeId);
        int grantsBefore = env.grantCount();
        int hitsBefore = env.risk.hits.size();
        long taskId = env.publish(
                "s19",
                List.of(new TaskStepCommand("rwd", "发奖", 1, "REWARD", null, prizeId)),
                List.of());
        env.risk.rejectGrant = true;
        long userId = env.nextUser();
        assertThatThrownBy(() -> env.start(taskId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RISK_BLOCKED_GENERIC);
        assertThat(env.instanceCount(taskId)).isZero();
        assertThat(env.grantCount()).isEqualTo(grantsBefore);
        assertThat(env.remainingStock(prizeId)).isEqualTo(stockBefore);
        assertThat(env.risk.hits.subList(hitsBefore, env.risk.hits.size())).contains(RiskScene.GRANT);
    }

    @Test
    void scenario20_级联中段PASSIVE无外部推送() {
        assertThat(EXPECTED.get(20)).contains("级联纯内存");
        long taskId = env.publish(
                "s20",
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("mid", "中段", 2, "PASSIVE", null, null),
                        new TaskStepCommand("c", "C", 3, "CLICK", null, null)),
                List.of(
                        new TaskTransitionCommand("a", "mid", null, 0),
                        new TaskTransitionCommand("mid", "c", null, 0)));
        long userId = env.nextUser();
        TaskStartResponse started = env.start(taskId, userId);
        env.click(started.instanceId(), "a", userId);
        assertThat(env.stepStatus(started.instanceId(), "a")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "mid")).isEqualTo(StepStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "c")).isEqualTo(StepStatuses.ACTIVE);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void scenario21_EXPIRED或ABANDONED推进拒绝终态不变() {
        assertThat(EXPECTED.get(21)).contains("拒绝且终态不变");
        long clickTask = env.publish(
                "s21c", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        long userA = env.nextUser();
        TaskStartResponse expired = env.start(clickTask, userA);
        LocalDateTime past = LocalDateTime.ofInstant(env.clock.instant().minusSeconds(1), ZoneOffset.UTC);
        env.jdbc.update("UPDATE task_instance SET expire_at = ? WHERE id = ?", past, expired.instanceId());
        assertThatThrownBy(() -> env.click(expired.instanceId(), "click", userA))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
        assertThat(env.instanceStatus(expired.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(env.stepStatus(expired.instanceId(), "click")).isEqualTo(StepStatuses.ACTIVE);

        long userB = env.nextUser();
        TaskStartResponse abandoned = env.start(clickTask, userB);
        env.abandon(abandoned.instanceId(), userB);
        assertThatThrownBy(() -> env.click(abandoned.instanceId(), "click", userB))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
        assertThat(env.instanceStatus(abandoned.instanceId())).isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(env.stepStatus(abandoned.instanceId(), "click")).isEqualTo(StepStatuses.ACTIVE);
    }

    @Test
    void scenario22_实例刚完成仍收到callback幂等返回终态() {
        assertThat(EXPECTED.get(22)).contains("幂等返回终态");
        long taskId = env.publish(
                "s22", List.of(new TaskStepCommand("cb", "回调", 1, "CALLBACK", null, null)), List.of());
        TaskStartResponse started = env.start(taskId, env.nextUser());
        env.callback(started.instanceId(), "cb", "biz-1");
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
        env.callback(started.instanceId(), "cb", "biz-late");
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(env.stepStatus(started.instanceId(), "cb")).isEqualTo(StepStatuses.COMPLETED);
    }

    @Test
    void scenario23_任务下线后存量实例按快照继续() {
        assertThat(EXPECTED.get(23)).contains("按旧快照继续直至终态");
        long taskId = env.publish(
                "s23", List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)), List.of());
        long owner = env.nextUser();
        TaskStartResponse started = env.start(taskId, owner);
        env.offline(taskId);
        env.click(started.instanceId(), "click", owner);
        assertThat(env.instanceStatus(started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
        assertThatThrownBy(() -> env.start(taskId, env.nextUser()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_NOT_VISIBLE);
    }

    private static void runConcurrent(List<Runnable> jobs) throws Exception {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(jobs.size());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (Runnable job : jobs) {
            pool.submit(() -> {
                try {
                    start.await();
                    job.run();
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        for (Throwable ex : errors) {
            if (ex instanceof Error error) {
                throw error;
            }
        }
    }
}
