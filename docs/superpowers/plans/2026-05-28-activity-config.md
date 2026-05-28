# Activity Configuration & Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a unified Activity entity as an upper container for tasks, sign-in configs, and prizes, with a pluggable rule chain for participation conditions, rate limiting, and anti-fraud.

**Architecture:** Activity shell + pluggable checker chain (strategy pattern). Sub-modules link via `activity_code`. Display rules stored in a separate table for bandwidth optimization. Rate limiting uses a `RateLimiter` interface with Caffeine sliding window (single-node) and optional Redis.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis-Plus 3.5, Caffeine, Flyway

**Spec:** `docs/superpowers/specs/2026-05-28-activity-config-design.md`

---

## File Structure

### New files

| Path | Responsibility |
|---|---|
| `backend/.../db/migration/V18__activity_module.sql` | Tables + sub-module ALTERs |
| `backend/.../activity/domain/entity/Activity.java` | Activity entity |
| `backend/.../activity/domain/entity/ActivityDisplayRule.java` | Display rule entity |
| `backend/.../activity/domain/entity/ActivityStats.java` | Stats entity |
| `backend/.../activity/domain/enums/ActivityStatus.java` | DRAFT/PUBLISHED/ONLINE/OFFLINE |
| `backend/.../activity/domain/enums/GrayType.java` | NONE/RATIO/WHITELIST |
| `backend/.../activity/domain/enums/CheckerType.java` | NEW_USER/WHITELIST/REGION |
| `backend/.../activity/domain/enums/LimitScope.java` | USER_DAILY/USER_TOTAL/GLOBAL_DAILY |
| `backend/.../activity/domain/enums/AntiFraudType.java` | IP_RATE/DEVICE_FINGERPRINT |
| `backend/.../activity/domain/dto/ActivityListVO.java` | C-end list response |
| `backend/.../activity/domain/dto/ActivityDetailVO.java` | Detail response |
| `backend/.../activity/domain/dto/ActivitySubModulesVO.java` | Sub-modules response |
| `backend/.../activity/domain/dto/RuleCheckResult.java` | Rule chain result |
| `backend/.../activity/domain/dto/ParticipationContext.java` | Checker input context |
| `backend/.../activity/domain/dto/CheckerConfig.java` | JSON deserialization DTO |
| `backend/.../activity/domain/dto/LimitConfig.java` | JSON deserialization DTO |
| `backend/.../activity/domain/dto/AntiFraudConfig.java` | JSON deserialization DTO |
| `backend/.../activity/mapper/ActivityMapper.java` | MyBatis-Plus mapper |
| `backend/.../activity/mapper/ActivityDisplayRuleMapper.java` | MyBatis-Plus mapper |
| `backend/.../activity/mapper/ActivityStatsMapper.java` | MyBatis-Plus mapper |
| `backend/.../activity/checker/ParticipationChecker.java` | Checker interface |
| `backend/.../activity/checker/AbstractParticipationChecker.java` | Base checker with JSON parsing |
| `backend/.../activity/checker/AbstractLimitChecker.java` | Base limit checker |
| `backend/.../activity/checker/NewUserChecker.java` | New user check |
| `backend/.../activity/checker/WhitelistChecker.java` | Whitelist check |
| `backend/.../activity/checker/RegionChecker.java` | Region check |
| `backend/.../activity/checker/UserDailyLimitChecker.java` | Per-user daily limit |
| `backend/.../activity/checker/UserTotalLimitChecker.java` | Per-user total limit |
| `backend/.../activity/checker/GlobalDailyLimitChecker.java` | Global daily limit |
| `backend/.../activity/checker/IpRateChecker.java` | IP rate limit |
| `backend/.../activity/checker/DeviceFingerprintChecker.java` | Device rate limit |
| `backend/.../activity/service/RateLimiter.java` | Rate limiter interface |
| `backend/.../activity/service/LocalRateLimiter.java` | Caffeine implementation |
| `backend/.../activity/service/ActivityCacheService.java` | Caffeine cache |
| `backend/.../activity/service/ActivityRuleService.java` | Rule chain orchestration |
| `backend/.../activity/service/ActivityService.java` | CRUD + state machine + gray |
| `backend/.../activity/service/ActivityStatsScheduler.java` | Auto online/offline scheduler |
| `backend/.../activity/controller/AdminActivityController.java` | Admin API |
| `backend/.../activity/controller/ClientActivityController.java` | Client API |

### Modified files

| Path | Change |
|---|---|
| `backend/.../common/ErrorCode.java` | Add ACTIVITY_* constants (10000-10099) |
| `backend/.../domain/entity/Task.java` | Add `activityCode` field |
| `backend/.../signin/domain/entity/SignInConfig.java` | Add `activityCode` field |
| `backend/.../prize/domain/entity/Prize.java` | Add `activityCode` field |
| `backend/.../prize/domain/entity/PrizeRecord.java` | Add `activityCode` field |
| `backend/src/main/resources/application.yml` | Add `activity.rate-limiter.type` config |

> All paths use `backend/src/main/java/com/marketing/task/` as root unless noted.

---

## Task 1: Flyway Migration V18

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__activity_module.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- V18__activity_module.sql

CREATE TABLE activity (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64) NOT NULL COMMENT '活动编码',
    name                VARCHAR(128) NOT NULL COMMENT '活动名称',
    description         VARCHAR(512) NULL COMMENT '简短描述',
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ONLINE/OFFLINE',
    gray_type           VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/RATIO/WHITELIST',
    gray_config         JSON NULL COMMENT '灰度配置',
    start_time          DATETIME NOT NULL COMMENT '活动开始时间',
    end_time            DATETIME NOT NULL COMMENT '活动结束时间',
    participation_rules JSON NULL COMMENT '执行规则配置',
    cache_version       INT NOT NULL DEFAULT 1 COMMENT '缓存版本号',
    created_by          VARCHAR(64) NULL,
    updated_by          VARCHAR(64) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动配置';

CREATE TABLE activity_display_rule (
    activity_id   BIGINT NOT NULL PRIMARY KEY COMMENT '关联活动ID',
    content       MEDIUMTEXT NULL COMMENT '富文本/Markdown内容',
    content_hash  VARCHAR(64) NULL COMMENT '内容hash用于ETag',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动展示规则';

CREATE TABLE activity_stats (
    activity_id       BIGINT NOT NULL,
    stat_date         DATE NOT NULL,
    participant_count INT NOT NULL DEFAULT 0,
    completion_count  INT NOT NULL DEFAULT 0,
    reward_count      INT NOT NULL DEFAULT 0,
    PRIMARY KEY (activity_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动统计数据';

-- Sub-module association
ALTER TABLE task ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE signin_config ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize_record ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';

CREATE INDEX idx_task_activity_code ON task(activity_code);
CREATE INDEX idx_signin_config_activity_code ON signin_config(activity_code);
CREATE INDEX idx_prize_activity_code ON prize(activity_code);
CREATE INDEX idx_prize_record_activity_code ON prize_record(activity_code);
```

- [ ] **Step 2: Verify migration runs**

Run: `cd backend && ./mvnw flyway:info`
Expected: V18 appears as "Pending"

---

## Task 2: ErrorCode Constants

**Files:**
- Modify: `backend/src/main/java/com/marketing/task/common/ErrorCode.java`

- [ ] **Step 1: Add activity error codes**

Add after the Filter section (9000-9999):

```java
// Activity (10000-10099)
ACTIVITY_NOT_FOUND(10000, 404, "ACTIVITY_NOT_FOUND", "活动不存在"),
ACTIVITY_INVALID_STATUS(10001, 400, "ACTIVITY_INVALID_STATUS", "活动状态不允许此操作"),
ACTIVITY_CODE_EXISTS(10002, 400, "ACTIVITY_CODE_EXISTS", "活动编码已存在"),
ACTIVITY_RULE_CHECK_FAILED(10003, 403, "ACTIVITY_RULE_CHECK_FAILED", "活动规则校验失败"),
ACTIVITY_GRAY_NOT_VISIBLE(10004, 403, "ACTIVITY_GRAY_NOT_VISIBLE", "活动灰度不可见"),
ACTIVITY_DISPLAY_RULE_NOT_FOUND(10005, 404, "ACTIVITY_DISPLAY_RULE_NOT_FOUND", "活动展示规则不存在"),
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 3: Enums

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/domain/enums/ActivityStatus.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/enums/GrayType.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/enums/CheckerType.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/enums/LimitScope.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/enums/AntiFraudType.java`

- [ ] **Step 1: Create ActivityStatus**

```java
package com.marketing.task.activity.domain.enums;

public enum ActivityStatus {
    DRAFT,
    PUBLISHED,
    ONLINE,
    OFFLINE
}
```

- [ ] **Step 2: Create GrayType**

```java
package com.marketing.task.activity.domain.enums;

public enum GrayType {
    NONE,
    RATIO,
    WHITELIST
}
```

- [ ] **Step 3: Create CheckerType**

```java
package com.marketing.task.activity.domain.enums;

public enum CheckerType {
    NEW_USER,
    WHITELIST,
    REGION
}
```

- [ ] **Step 4: Create LimitScope**

```java
package com.marketing.task.activity.domain.enums;

public enum LimitScope {
    USER_DAILY,
    USER_TOTAL,
    GLOBAL_DAILY
}
```

- [ ] **Step 5: Create AntiFraudType**

```java
package com.marketing.task.activity.domain.enums;

public enum AntiFraudType {
    IP_RATE,
    DEVICE_FINGERPRINT
}
```

- [ ] **Step 6: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 4: Entities

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/domain/entity/Activity.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/entity/ActivityDisplayRule.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/entity/ActivityStats.java`

- [ ] **Step 1: Create Activity entity**

```java
package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private String grayType;
    private String grayConfig;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String participationRules;
    private Integer cacheVersion;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
```

- [ ] **Step 2: Create ActivityDisplayRule entity**

```java
package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("activity_display_rule")
public class ActivityDisplayRule {
    private Long activityId;
    private String content;
    private String contentHash;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
```

- [ ] **Step 3: Create ActivityStats entity**

```java
package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("activity_stats")
public class ActivityStats {
    private Long activityId;
    private LocalDate statDate;
    private Integer participantCount;
    private Integer completionCount;
    private Integer rewardCount;
}
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 5: DTOs

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/ActivityListVO.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/ActivityDetailVO.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/ActivitySubModulesVO.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/RuleCheckResult.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/ParticipationContext.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/CheckerConfig.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/LimitConfig.java`
- Create: `backend/src/main/java/com/marketing/task/activity/domain/dto/AntiFraudConfig.java`

- [ ] **Step 1: Create RuleCheckResult**

```java
package com.marketing.task.activity.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleCheckResult {
    private boolean passed;
    private String failCode;
    private String failMessage;
    private String checkerType;

    public static RuleCheckResult pass() {
        return new RuleCheckResult(true, null, null, null);
    }

    public static RuleCheckResult fail(String failCode, String failMessage, String checkerType) {
        return new RuleCheckResult(false, failCode, failMessage, checkerType);
    }
}
```

- [ ] **Step 2: Create ParticipationContext**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;

@Data
public class ParticipationContext {
    private Long userId;
    private String clientIp;
    private String deviceId;
    private Integer userDays;
}
```

- [ ] **Step 3: Create CheckerConfig**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CheckerConfig {
    private String type;
    private Map<String, Object> params;
}
```

- [ ] **Step 4: Create LimitConfig**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;

@Data
public class LimitConfig {
    private String scope;
    private int max;
}
```

- [ ] **Step 5: Create AntiFraudConfig**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AntiFraudConfig {
    private String type;
    private Map<String, Object> params;
}
```

- [ ] **Step 6: Create ActivityListVO**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityListVO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean hasDisplayRule;
}
```

- [ ] **Step 7: Create ActivityDetailVO**

```java
package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityDetailVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private String grayType;
    private String grayConfig;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String participationRules;
    private boolean hasDisplayRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 8: Create ActivitySubModulesVO**

```java
package com.marketing.task.activity.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySubModulesVO {
    private List<TaskSummary> tasks;
    private List<SignInConfigSummary> signInConfigs;
    private List<PrizeSummary> prizes;

    @Data
    public static class TaskSummary {
        private Long id;
        private String name;
        private String status;
    }

    @Data
    public static class SignInConfigSummary {
        private Long id;
        private String name;
        private String status;
    }

    @Data
    public static class PrizeSummary {
        private Long id;
        private String name;
        private String type;
    }
}
```

- [ ] **Step 9: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 6: Mappers

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/mapper/ActivityMapper.java`
- Create: `backend/src/main/java/com/marketing/task/activity/mapper/ActivityDisplayRuleMapper.java`
- Create: `backend/src/main/java/com/marketing/task/activity/mapper/ActivityStatsMapper.java`

- [ ] **Step 1: Create ActivityMapper**

```java
package com.marketing.task.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.activity.domain.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
```

- [ ] **Step 2: Create ActivityDisplayRuleMapper**

```java
package com.marketing.task.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityDisplayRuleMapper extends BaseMapper<ActivityDisplayRule> {
}
```

- [ ] **Step 3: Create ActivityStatsMapper**

```java
package com.marketing.task.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.activity.domain.entity.ActivityStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityStatsMapper extends BaseMapper<ActivityStats> {
}
```

---

## Task 7: RateLimiter Interface + LocalRateLimiter

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/service/RateLimiter.java`
- Create: `backend/src/main/java/com/marketing/task/activity/service/LocalRateLimiter.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Create RateLimiter interface**

```java
package com.marketing.task.activity.service;

import java.time.Duration;

public interface RateLimiter {
    boolean tryAcquire(String key, int maxCount, Duration window);
}
```

- [ ] **Step 2: Create LocalRateLimiter**

```java
package com.marketing.task.activity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
@ConditionalOnProperty(name = "activity.rate-limiter.type", havingValue = "local", matchIfMissing = true)
public class LocalRateLimiter implements RateLimiter {

    private final Cache<String, Deque<Long>> counters = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    public boolean tryAcquire(String key, int maxCount, Duration window) {
        Deque<Long> timestamps = counters.get(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxCount) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}
```

- [ ] **Step 3: Add config to application.yml**

Append to `backend/src/main/resources/application.yml`:

```yaml
activity:
  rate-limiter:
    type: local
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 8: ParticipationChecker Interface + Implementations

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/checker/ParticipationChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/AbstractParticipationChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/AbstractLimitChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/NewUserChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/WhitelistChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/RegionChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/UserDailyLimitChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/UserTotalLimitChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/GlobalDailyLimitChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/IpRateChecker.java`
- Create: `backend/src/main/java/com/marketing/task/activity/checker/DeviceFingerprintChecker.java`

- [ ] **Step 1: Create ParticipationChecker interface**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;

public interface ParticipationChecker {
    RuleCheckResult check(Activity activity, ParticipationContext context);
    String checkerType();
}
```

- [ ] **Step 2: Create AbstractParticipationChecker**

```java
package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractParticipationChecker implements ParticipationChecker {

    protected abstract RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config);

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        CheckerConfig config = findConfig(activity);
        if (config == null) {
            return RuleCheckResult.pass();
        }
        try {
            return doCheck(activity, context, config);
        } catch (Exception e) {
            log.warn("Checker执行异常: type={}, activityCode={}, error={}", checkerType(), activity.getCode(), e.getMessage());
            return RuleCheckResult.pass();
        }
    }

    private CheckerConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> checkers = (List<Map<String, Object>>) rules.getOrDefault("checkers", Collections.emptyList());
            for (Map<String, Object> c : checkers) {
                if (checkerType().equals(c.get("type"))) {
                    CheckerConfig config = new CheckerConfig();
                    config.setType((String) c.get("type"));
                    config.setParams((Map<String, Object>) c.get("params"));
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
```

- [ ] **Step 3: Create NewUserChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.CheckerType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewUserChecker extends AbstractParticipationChecker {

    @Override
    public String checkerType() {
        return CheckerType.NEW_USER.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        Map<String, Object> params = config.getParams();
        int maxDays = params.containsKey("days") ? ((Number) params.get("days")).intValue() : 7;
        if (context.getUserDays() != null && context.getUserDays() > maxDays) {
            return RuleCheckResult.fail("NOT_NEW_USER", "仅限新用户参与", checkerType());
        }
        return RuleCheckResult.pass();
    }
}
```

- [ ] **Step 4: Create WhitelistChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.CheckerType;
import com.marketing.task.domain.entity.ListData;
import com.marketing.task.mapper.ListDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhitelistChecker extends AbstractParticipationChecker {

    private final ListDataMapper listDataMapper;

    @Override
    public String checkerType() {
        return CheckerType.WHITELIST.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        String listCode = (String) config.getParams().get("listCode");
        if (listCode == null) return RuleCheckResult.pass();

        long count = listDataMapper.selectCount(
                new LambdaQueryWrapper<ListData>()
                        .eq(ListData::getListCode, listCode)
                        .eq(ListData::getValue, String.valueOf(context.getUserId()))
        );
        if (count == 0) {
            return RuleCheckResult.fail("NOT_IN_WHITELIST", "不在活动白名单中", checkerType());
        }
        return RuleCheckResult.pass();
    }
}
```

- [ ] **Step 5: Create RegionChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.CheckerConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.CheckerType;
import org.springframework.stereotype.Component;

@Component
public class RegionChecker extends AbstractParticipationChecker {

    @Override
    public String checkerType() {
        return CheckerType.REGION.name();
    }

    @Override
    protected RuleCheckResult doCheck(Activity activity, ParticipationContext context, CheckerConfig config) {
        // Region check requires client to pass region info in context
        // For now, always pass - region info needs to be added to ParticipationContext
        return RuleCheckResult.pass();
    }
}
```

- [ ] **Step 6: Create AbstractLimitChecker**

```java
package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.LimitConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.service.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractLimitChecker implements ParticipationChecker {

    protected abstract String buildKey(Activity activity, ParticipationContext ctx);
    protected abstract RateLimiter getRateLimiter();

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        LimitConfig config = findConfig(activity);
        if (config == null) {
            return RuleCheckResult.pass();
        }
        try {
            String key = buildKey(activity, context);
            boolean allowed = getRateLimiter().tryAcquire(key, config.getMax(), Duration.ofDays(1));
            if (!allowed) {
                return RuleCheckResult.fail("LIMIT_EXCEEDED", "参与次数已达上限", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("LimitChecker执行异常: type={}, activityCode={}, error={}", checkerType(), activity.getCode(), e.getMessage());
            return RuleCheckResult.pass();
        }
    }

    private LimitConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> limits = (List<Map<String, Object>>) rules.getOrDefault("limits", Collections.emptyList());
            for (Map<String, Object> l : limits) {
                if (checkerType().equals(l.get("scope"))) {
                    LimitConfig config = new LimitConfig();
                    config.setScope((String) l.get("scope"));
                    config.setMax(((Number) l.get("max")).intValue());
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
```

- [ ] **Step 7: Create UserDailyLimitChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.LimitScope;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class UserDailyLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.USER_DAILY.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "act:uld:%d:%s:%s".formatted(activity.getId(), ctx.getUserId(), date);
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
```

- [ ] **Step 8: Create UserTotalLimitChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.LimitScope;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTotalLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.USER_TOTAL.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        return "act:ut:%d:%s".formatted(activity.getId(), ctx.getUserId());
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
```

- [ ] **Step 9: Create GlobalDailyLimitChecker**

```java
package com.marketing.task.activity.checker;

import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.LimitScope;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class GlobalDailyLimitChecker extends AbstractLimitChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return LimitScope.GLOBAL_DAILY.name();
    }

    @Override
    protected String buildKey(Activity activity, ParticipationContext ctx) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "act:gd:%d:%s".formatted(activity.getId(), date);
    }

    @Override
    protected RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
```

- [ ] **Step 10: Create IpRateChecker**

```java
package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.AntiFraudConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.AntiFraudType;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateChecker implements ParticipationChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return AntiFraudType.IP_RATE.name();
    }

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        AntiFraudConfig config = findConfig(activity);
        if (config == null || context.getClientIp() == null) {
            return RuleCheckResult.pass();
        }
        try {
            int maxPerIp = ((Number) config.getParams().get("maxPerIp")).intValue();
            int windowSec = ((Number) config.getParams().get("windowSeconds")).intValue();
            String key = "act:ip:%d:%s".formatted(activity.getId(), context.getClientIp());
            boolean allowed = rateLimiter.tryAcquire(key, maxPerIp, Duration.ofSeconds(windowSec));
            if (!allowed) {
                return RuleCheckResult.fail("IP_RATE_EXCEEDED", "请求过于频繁，请稍后再试", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("IpRateChecker执行异常: activityCode={}", activity.getCode(), e);
            return RuleCheckResult.pass();
        }
    }

    private AntiFraudConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> antiFraud = (List<Map<String, Object>>) rules.getOrDefault("antiFraud", Collections.emptyList());
            for (Map<String, Object> af : antiFraud) {
                if (checkerType().equals(af.get("type"))) {
                    AntiFraudConfig config = new AntiFraudConfig();
                    config.setType((String) af.get("type"));
                    config.setParams((Map<String, Object>) af.get("params"));
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
```

- [ ] **Step 11: Create DeviceFingerprintChecker**

```java
package com.marketing.task.activity.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.task.activity.domain.dto.AntiFraudConfig;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.AntiFraudType;
import com.marketing.task.activity.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceFingerprintChecker implements ParticipationChecker {

    private final RateLimiter rateLimiter;

    @Override
    public String checkerType() {
        return AntiFraudType.DEVICE_FINGERPRINT.name();
    }

    @Override
    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        AntiFraudConfig config = findConfig(activity);
        if (config == null || context.getDeviceId() == null) {
            return RuleCheckResult.pass();
        }
        try {
            int maxPerDevice = ((Number) config.getParams().get("maxPerDevice")).intValue();
            String key = "act:dev:%d:%s".formatted(activity.getId(), context.getDeviceId());
            boolean allowed = rateLimiter.tryAcquire(key, maxPerDevice, Duration.ofHours(24));
            if (!allowed) {
                return RuleCheckResult.fail("DEVICE_RATE_EXCEEDED", "设备参与次数已达上限", checkerType());
            }
            return RuleCheckResult.pass();
        } catch (Exception e) {
            log.warn("DeviceFingerprintChecker执行异常: activityCode={}", activity.getCode(), e);
            return RuleCheckResult.pass();
        }
    }

    private AntiFraudConfig findConfig(Activity activity) {
        if (activity.getParticipationRules() == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rules = mapper.readValue(activity.getParticipationRules(), new TypeReference<>() {});
            List<Map<String, Object>> antiFraud = (List<Map<String, Object>>) rules.getOrDefault("antiFraud", Collections.emptyList());
            for (Map<String, Object> af : antiFraud) {
                if (checkerType().equals(af.get("type"))) {
                    AntiFraudConfig config = new AntiFraudConfig();
                    config.setType((String) af.get("type"));
                    config.setParams((Map<String, Object>) af.get("params"));
                    return config;
                }
            }
        } catch (Exception e) {
            log.warn("解析participation_rules失败: activityCode={}", activity.getCode(), e);
        }
        return null;
    }
}
```

- [ ] **Step 12: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 9: ActivityCacheService

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/service/ActivityCacheService.java`

- [ ] **Step 1: Create ActivityCacheService**

```java
package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.task.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheService {

    private final ActivityMapper activityMapper;
    private final ActivityDisplayRuleMapper displayRuleMapper;

    private final Cache<Long, Activity> activityCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final Cache<Long, ActivityDisplayRule> ruleCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public Activity getActivity(Long id) {
        return activityCache.get(id, key -> activityMapper.selectById(key));
    }

    public Activity getActivityByCode(String code) {
        return activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getCode, code)
                        .eq(Activity::getDeleted, 0));
    }

    public ActivityDisplayRule getDisplayRule(Long activityId) {
        return ruleCache.get(activityId, key -> displayRuleMapper.selectById(key));
    }

    public void evictActivity(Long id) {
        activityCache.invalidate(id);
        log.info("清除活动缓存: id={}", id);
    }

    public void evictDisplayRule(Long activityId) {
        ruleCache.invalidate(activityId);
        log.info("清除展示规则缓存: activityId={}", activityId);
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 10: ActivityRuleService

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/service/ActivityRuleService.java`

- [ ] **Step 1: Create ActivityRuleService**

```java
package com.marketing.task.activity.service;

import com.marketing.task.activity.checker.ParticipationChecker;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ActivityRuleService {

    private final List<ParticipationChecker> checkers;

    public ActivityRuleService(List<ParticipationChecker> checkers) {
        this.checkers = checkers;
    }

    public RuleCheckResult check(Activity activity, ParticipationContext context) {
        if (!ActivityStatus.ONLINE.name().equals(activity.getStatus())) {
            return RuleCheckResult.fail("ACTIVITY_NOT_ONLINE", "活动未在进行中", "STATUS");
        }

        for (ParticipationChecker checker : checkers) {
            RuleCheckResult result = checker.check(activity, context);
            if (!result.isPassed()) {
                log.info("活动规则校验失败: activityCode={}, checker={}, failCode={}",
                        activity.getCode(), result.getCheckerType(), result.getFailCode());
                return result;
            }
        }

        return RuleCheckResult.pass();
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 11: ActivityService

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/service/ActivityService.java`

- [ ] **Step 1: Create ActivityService**

```java
package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.activity.domain.dto.*;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import com.marketing.task.activity.domain.enums.GrayType;
import com.marketing.task.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.domain.entity.Prize;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.mapper.PrizeMapper;
import com.marketing.task.mapper.SignInConfigMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.signin.domain.entity.SignInConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityDisplayRuleMapper displayRuleMapper;
    private final ActivityCacheService cacheService;
    private final TaskMapper taskMapper;
    private final SignInConfigMapper signInConfigMapper;
    private final PrizeMapper prizeMapper;

    // --- CRUD ---

    public Page<Activity> list(int page, int size, String status) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<Activity>()
                .eq(Activity::getDeleted, 0)
                .orderByDesc(Activity::getCreatedAt);
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        return activityMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ActivityDetailVO getDetail(Long id) {
        Activity activity = requireActivity(id);
        return toDetailVO(activity);
    }

    @Transactional
    public Activity create(Activity activity, String operatorId) {
        Long existing = activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().eq(Activity::getCode, activity.getCode()));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_CODE_EXISTS, "活动编码已存在: " + activity.getCode());
        }
        activity.setId(null);
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setCacheVersion(1);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setCreatedBy(operatorId);
        activity.setUpdatedBy(operatorId);
        activity.setDeleted(0);
        activityMapper.insert(activity);
        log.info("创建活动: code={}, name={}", activity.getCode(), activity.getName());
        return activity;
    }

    @Transactional
    public Activity update(Long id, Activity update, String operatorId) {
        Activity activity = requireActivity(id);
        if (!isEditable(activity)) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅DRAFT和OFFLINE状态可编辑");
        }
        update.setId(id);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(operatorId);
        activityMapper.updateById(update);
        cacheService.evictActivity(id);
        log.info("更新活动: id={}, operator={}", id, operatorId);
        return activityMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        if (!ActivityStatus.DRAFT.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅DRAFT状态可删除");
        }
        activity.setDeleted(1);
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("删除活动: id={}", id);
    }

    // --- State Machine ---

    @Transactional
    public void publish(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        if (!ActivityStatus.DRAFT.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅DRAFT状态可发布");
        }
        activity.setStatus(ActivityStatus.PUBLISHED.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("发布活动: id={}, code={}", id, activity.getCode());
    }

    @Transactional
    public void offline(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        String status = activity.getStatus();
        if (!ActivityStatus.PUBLISHED.name().equals(status) && !ActivityStatus.ONLINE.name().equals(status)) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅PUBLISHED和ONLINE状态可下线");
        }
        activity.setStatus(ActivityStatus.OFFLINE.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("下线活动: id={}, code={}", id, activity.getCode());
    }

    @Transactional
    public void backToDraft(Long id, String operatorId) {
        Activity activity = requireActivity(id);
        if (!ActivityStatus.OFFLINE.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_INVALID_STATUS, "仅OFFLINE状态可退回草稿");
        }
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setUpdatedAt(LocalDateTime.now());
        activity.setUpdatedBy(operatorId);
        activityMapper.updateById(activity);
        cacheService.evictActivity(id);
        log.info("退回草稿: id={}, code={}", id, activity.getCode());
    }

    // --- Display Rule ---

    public ActivityDisplayRule getDisplayRule(Long activityId) {
        return cacheService.getDisplayRule(activityId);
    }

    @Transactional
    public void updateDisplayRule(Long activityId, String content, String operatorId) {
        requireActivity(activityId);
        String contentHash = sha256(content);
        ActivityDisplayRule rule = displayRuleMapper.selectById(activityId);
        if (rule == null) {
            rule = new ActivityDisplayRule();
            rule.setActivityId(activityId);
            rule.setContent(content);
            rule.setContentHash(contentHash);
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(operatorId);
            displayRuleMapper.insert(rule);
        } else {
            rule.setContent(content);
            rule.setContentHash(contentHash);
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(operatorId);
            displayRuleMapper.updateById(rule);
        }
        cacheService.evictDisplayRule(activityId);
        log.info("更新展示规则: activityId={}", activityId);
    }

    // --- Sub-modules ---

    public ActivitySubModulesVO getSubModules(Long activityId) {
        Activity activity = requireActivity(activityId);
        String code = activity.getCode();

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getActivityCode, code).eq(Task::getDeleted, 0));
        List<SignInConfig> configs = signInConfigMapper.selectList(
                new LambdaQueryWrapper<SignInConfig>().eq(SignInConfig::getActivityCode, code));
        List<Prize> prizes = prizeMapper.selectList(
                new LambdaQueryWrapper<Prize>().eq(Prize::getActivityCode, code));

        List<ActivitySubModulesVO.TaskSummary> taskSummaries = tasks.stream().map(t -> {
            ActivitySubModulesVO.TaskSummary s = new ActivitySubModulesVO.TaskSummary();
            s.setId(t.getId());
            s.setName(t.getName());
            s.setStatus(t.getStatus());
            return s;
        }).toList();

        List<ActivitySubModulesVO.SignInConfigSummary> signSummaries = configs.stream().map(c -> {
            ActivitySubModulesVO.SignInConfigSummary s = new ActivitySubModulesVO.SignInConfigSummary();
            s.setId(c.getId());
            s.setName(c.getName());
            s.setStatus(c.getStatus());
            return s;
        }).toList();

        List<ActivitySubModulesVO.PrizeSummary> prizeSummaries = prizes.stream().map(p -> {
            ActivitySubModulesVO.PrizeSummary s = new ActivitySubModulesVO.PrizeSummary();
            s.setId(p.getId());
            s.setName(p.getName());
            s.setType(p.getType());
            return s;
        }).toList();

        return new ActivitySubModulesVO(taskSummaries, signSummaries, prizeSummaries);
    }

    // --- Gray ---

    public boolean isUserInGray(Activity activity, Long userId) {
        if (GrayType.NONE.name().equals(activity.getGrayType())) {
            return true;
        }
        if (GrayType.RATIO.name().equals(activity.getGrayType())) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> config = mapper.readValue(activity.getGrayConfig(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                int ratio = ((Number) config.get("ratio")).intValue();
                return (userId.hashCode() & 0x7FFFFFFF) % 100 < ratio;
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    // --- C-end list ---

    public List<Activity> listOnlineActivities() {
        return activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONLINE.name())
                        .eq(Activity::getDeleted, 0)
                        .orderByAsc(Activity::getStartTime));
    }

    // --- Helpers ---

    private Activity requireActivity(Long id) {
        Activity activity = cacheService.getActivity(id);
        if (activity == null || activity.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND, "活动不存在: " + id);
        }
        return activity;
    }

    private boolean isEditable(Activity activity) {
        String status = activity.getStatus();
        return ActivityStatus.DRAFT.name().equals(status) || ActivityStatus.OFFLINE.name().equals(status);
    }

    private ActivityDetailVO toDetailVO(Activity activity) {
        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(activity.getId());
        vo.setCode(activity.getCode());
        vo.setName(activity.getName());
        vo.setDescription(activity.getDescription());
        vo.setStatus(activity.getStatus());
        vo.setGrayType(activity.getGrayType());
        vo.setGrayConfig(activity.getGrayConfig());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setParticipationRules(activity.getParticipationRules());
        vo.setHasDisplayRule(displayRuleMapper.selectById(activity.getId()) != null);
        vo.setCreatedAt(activity.getCreatedAt());
        vo.setUpdatedAt(activity.getUpdatedAt());
        return vo;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 12: ActivityStatsScheduler

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/service/ActivityStatsScheduler.java`

- [ ] **Step 1: Create ActivityStatsScheduler**

```java
package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStatsScheduler {

    private final ActivityMapper activityMapper;
    private final ActivityStatsMapper statsMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void autoOnline() {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.PUBLISHED.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getStartTime, LocalDateTime.now()));
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatus.ONLINE.name());
            activity.setUpdatedAt(LocalDateTime.now());
            activityMapper.updateById(activity);
            log.info("活动自动上线: id={}, code={}", activity.getId(), activity.getCode());
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void autoOffline() {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ONLINE.name())
                        .eq(Activity::getDeleted, 0)
                        .le(Activity::getEndTime, LocalDateTime.now()));
        for (Activity activity : activities) {
            activity.setStatus(ActivityStatus.OFFLINE.name());
            activity.setUpdatedAt(LocalDateTime.now());
            activityMapper.updateById(activity);
            log.info("活动自动下线: id={}, code={}", activity.getId(), activity.getCode());
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 13: AdminActivityController

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/controller/AdminActivityController.java`

- [ ] **Step 1: Create AdminActivityController**

```java
package com.marketing.task.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.activity.domain.dto.ActivityDetailVO;
import com.marketing.task.activity.domain.dto.ActivitySubModulesVO;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Page<Activity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(activityService.list(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(activityService.getDetail(id));
    }

    @GetMapping("/{id}/display-rule")
    public Result<ActivityDisplayRule> getDisplayRule(@PathVariable Long id) {
        return Result.ok(activityService.getDisplayRule(id));
    }

    @PostMapping
    public Result<Activity> create(@RequestBody Activity activity) {
        String operatorId = UserContextHolder.get().getUserId();
        Activity created = activityService.create(activity, operatorId);
        operationLogService.record(operatorId, "CREATE", "ACTIVITY", created.getId(), created.getName(), null);
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    public Result<Activity> update(@PathVariable Long id, @RequestBody Activity activity) {
        String operatorId = UserContextHolder.get().getUserId();
        Activity updated = activityService.update(id, activity, operatorId);
        operationLogService.record(operatorId, "UPDATE", "ACTIVITY", id, updated.getName(), null);
        return Result.ok(updated);
    }

    @PutMapping("/{id}/display-rule")
    public Result<Void> updateDisplayRule(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String operatorId = UserContextHolder.get().getUserId();
        String content = body.get("content");
        activityService.updateDisplayRule(id, content, operatorId);
        operationLogService.record(operatorId, "UPDATE_DISPLAY_RULE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.delete(id, operatorId);
        operationLogService.record(operatorId, "DELETE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.publish(id, operatorId);
        operationLogService.record(operatorId, "PUBLISH", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.offline(id, operatorId);
        operationLogService.record(operatorId, "OFFLINE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @GetMapping("/{id}/sub-modules")
    public Result<ActivitySubModulesVO> getSubModules(@PathVariable Long id) {
        return Result.ok(activityService.getSubModules(id));
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 14: ClientActivityController

**Files:**
- Create: `backend/src/main/java/com/marketing/task/activity/controller/ClientActivityController.java`

- [ ] **Step 1: Create ClientActivityController**

```java
package com.marketing.task.activity.controller;

import com.marketing.task.activity.domain.dto.ActivityDetailVO;
import com.marketing.task.activity.domain.dto.ActivityListVO;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.service.ActivityCacheService;
import com.marketing.task.activity.service.ActivityRuleService;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/client/activities")
@RequiredArgsConstructor
public class ClientActivityController {

    private final ActivityService activityService;
    private final ActivityCacheService cacheService;
    private final ActivityRuleService ruleService;

    @GetMapping
    public Result<List<ActivityListVO>> list() {
        List<Activity> activities = activityService.listOnlineActivities();
        List<ActivityListVO> vos = activities.stream().map(a -> {
            ActivityListVO vo = new ActivityListVO();
            vo.setId(a.getId());
            vo.setCode(a.getCode());
            vo.setName(a.getName());
            vo.setStatus(a.getStatus());
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            vo.setHasDisplayRule(cacheService.getDisplayRule(a.getId()) != null);
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        ActivityDetailVO detail = activityService.getDetail(id);
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        if (!activityService.isUserInGray(activity, Long.parseLong(userId))) {
            throw new BusinessException(ErrorCode.ACTIVITY_GRAY_NOT_VISIBLE);
        }
        return Result.ok(detail);
    }

    @GetMapping("/{id}/display-rule")
    public ResponseEntity<?> getDisplayRule(@PathVariable Long id, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ActivityDisplayRule rule = cacheService.getDisplayRule(id);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (rule.getContentHash() != null && rule.getContentHash().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(rule.getContentHash())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(rule.getContentHash())
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(30)))
                .body(rule);
    }

    @PostMapping("/{id}/participate")
    public Result<RuleCheckResult> participate(@PathVariable Long id, HttpServletRequest request) {
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        ParticipationContext context = new ParticipationContext();
        context.setUserId(Long.parseLong(userId));
        context.setClientIp(getClientIp(request));

        RuleCheckResult result = ruleService.check(activity, context);
        if (!result.isPassed()) {
            return Result.fail(ErrorCode.ACTIVITY_RULE_CHECK_FAILED, result.getFailMessage());
        }
        return Result.ok(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 15: Sub-module Entity Changes

**Files:**
- Modify: `backend/src/main/java/com/marketing/task/domain/entity/Task.java`
- Modify: `backend/src/main/java/com/marketing/task/signin/domain/entity/SignInConfig.java`
- Modify: `backend/src/main/java/com/marketing/task/prize/domain/entity/Prize.java`
- Modify: `backend/src/main/java/com/marketing/task/prize/domain/entity/PrizeRecord.java`

- [ ] **Step 1: Add activityCode to Task.java**

Add field after existing fields:
```java
private String activityCode;
```

- [ ] **Step 2: Add activityCode to SignInConfig.java**

Add field after existing fields:
```java
private String activityCode;
```

- [ ] **Step 3: Add activityCode to Prize.java**

Add field after existing fields:
```java
private String activityCode;
```

- [ ] **Step 4: Add activityCode to PrizeRecord.java**

Add field after existing fields:
```java
private String activityCode;
```

- [ ] **Step 5: Full compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

## Task 16: Full Build Verification & Commit

- [ ] **Step 1: Run full build**

Run: `cd backend && ./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests**

Run: `cd backend && ./mvnw test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/marketing/task/activity/
git add backend/src/main/java/com/marketing/task/common/ErrorCode.java
git add backend/src/main/java/com/marketing/task/domain/entity/Task.java
git add backend/src/main/java/com/marketing/task/signin/domain/entity/SignInConfig.java
git add backend/src/main/java/com/marketing/task/prize/domain/entity/Prize.java
git add backend/src/main/java/com/marketing/task/prize/domain/entity/PrizeRecord.java
git add backend/src/main/resources/db/migration/V18__activity_module.sql
git add backend/src/main/resources/application.yml
git commit -m "feat: add activity configuration and rules module

- Activity entity with state machine (DRAFT/PUBLISHED/ONLINE/OFFLINE)
- Pluggable participation checker chain (conditions, limits, anti-fraud)
- RateLimiter interface with Caffeine sliding window (single-node)
- Display rules in separate table with ETag caching
- Admin CRUD + publish/offline + gray release
- Client API with rule chain validation
- Sub-module association via activity_code
- Flyway V18 migration"
```
