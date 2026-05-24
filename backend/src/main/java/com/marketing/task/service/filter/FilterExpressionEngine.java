package com.marketing.task.service.filter;

import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.EventType;
import com.marketing.task.context.UserContext;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.MetricsService;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.Operator;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FilterExpressionEngine {
    private static final int MAX_LENGTH = 1024;
    private static final Pattern FORBIDDEN = Pattern.compile("System|Runtime|Process|Thread|Class\\.forName|import\\s|new\\s|exec|eval", Pattern.CASE_INSENSITIVE);
    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();
    private final ExpressRunner runner;

    private final ListDataService listDataService;
    private final EventTrackingService eventTrackingService;
    private final MetricsService metricsService;

    public FilterExpressionEngine(ListDataService listDataService,
                                  EventTrackingService eventTrackingService,
                                  MetricsService metricsService) throws Exception {
        this.runner = new ExpressRunner(false, false);
        this.listDataService = listDataService;
        this.eventTrackingService = eventTrackingService;
        this.metricsService = metricsService;
        registerFunctions();
    }

    public boolean evaluate(String expression, UserContext userContext) {
        validate(expression);
        long startTime = System.currentTimeMillis();
        try {
            boolean result = CompletableFuture.supplyAsync(() -> execute(expression, userContext))
                    .get(100, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;
            metricsService.recordFilterTime(elapsed);
            eventTrackingService.track(EventType.FILTER_EVALUATED, null, null, null,
                    userContext.getUserId(), null,
                    Map.of("expression", expression, "result", result));
            return result;
        } catch (TimeoutException ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            metricsService.recordFilterTime(elapsed);
            return false;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            metricsService.recordFilterTime(elapsed);
            log.warn("Unexpected error evaluating filter expression: {}", expression, ex);
            return false;
        }
    }

    public void validate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new BusinessException(ErrorCode.FILTER_EXPRESSION_EMPTY);
        }
        if (expression.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.FILTER_EXPRESSION_TOO_LONG);
        }
        if (FORBIDDEN.matcher(expression).find()) {
            throw new BusinessException(ErrorCode.FILTER_EXPRESSION_DISALLOWED);
        }
    }

    private boolean execute(String expression, UserContext userContext) {
        try {
            CURRENT.set(userContext);
            Object result = runner.execute(expression, new DefaultContext<>(), null, false, false);
            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            log.warn("Filter evaluation error: expression={}, userId={}", expression, userContext.getUserId(), ex);
            return false;
        } finally {
            CURRENT.remove();
        }
    }

    private void registerFunctions() throws Exception {
        runner.addFunction("inProvince", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return containsArg(current().getProvince(), arg(list, 0));
            }
        });
        runner.addFunction("hasTag", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return current().getTags().contains(arg(list, 0));
            }
        });
        runner.addFunction("hasAnyTag", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return intersects(current().getTags(), arg(list, 0));
            }
        });
        runner.addFunction("roleEquals", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return Objects.equals(current().getRole(), arg(list, 0));
            }
        });
        runner.addFunction("roleIn", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return containsArg(current().getRole(), arg(list, 0));
            }
        });
        runner.addFunction("inAllowlist", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return listDataService.isInList("ALLOWLIST", String.valueOf(arg(list, 0)), current().getUserId());
            }
        });
        runner.addFunction("notInDenylist", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return !listDataService.isInList("DENYLIST", String.valueOf(arg(list, 0)), current().getUserId());
            }
        });
        runner.addFunction("orgEquals", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return Objects.equals(current().getOrgId(), arg(list, 0));
            }
        });
        runner.addFunction("orgIn", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                return containsArg(current().getOrgId(), arg(list, 0));
            }
        });
        runner.addFunction("levelGte", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                Integer level = current().getLevel();
                Object target = arg(list, 0);
                return level != null && target instanceof Number number && level >= number.intValue();
            }
        });
        runner.addFunction("levelEq", new Operator() {
            @Override
            public Object executeInner(Object[] list) {
                Object target = arg(list, 0);
                return target instanceof Number number && Objects.equals(current().getLevel(), number.intValue());
            }
        });
    }

    private static UserContext current() {
        return CURRENT.get();
    }

    private static Object arg(Object[] list, int index) {
        return list.length <= index ? null : list[index];
    }

    private static boolean containsArg(Object value, Object candidate) {
        if (candidate instanceof Collection<?> collection) {
            return collection.contains(value);
        }
        if (candidate instanceof Object[] array) {
            return List.of(array).contains(value);
        }
        return Objects.equals(value, candidate);
    }

    private static boolean intersects(Collection<String> values, Object candidate) {
        if (candidate instanceof Collection<?> collection) {
            return collection.stream().anyMatch(values::contains);
        }
        if (candidate instanceof Object[] array) {
            return List.of(array).stream().anyMatch(values::contains);
        }
        return values.contains(candidate);
    }
}
