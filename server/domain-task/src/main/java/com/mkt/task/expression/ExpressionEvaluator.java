package com.mkt.task.expression;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pure-memory eval with NULL_ATTR sentinel (design §5.10). */
final class ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluator.class);

    private final EvalContext context;
    private final List<String> nullAttrHits = new ArrayList<>();

    ExpressionEvaluator(EvalContext context) {
        this.context = context;
    }

    boolean evaluate(ExprNode root) {
        Object value = eval(root);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return false;
    }

    List<String> nullAttrHits() {
        return List.copyOf(nullAttrHits);
    }

    private Object eval(ExprNode node) {
        return switch (node) {
            case ExprNode.Literal literal -> literal.value();
            case ExprNode.Group group -> eval(group.inner());
            case ExprNode.Call call -> call(call);
            case ExprNode.Unary unary -> unary(unary);
            case ExprNode.Binary binary -> binary(binary);
            case ExprNode.In in -> in(in);
        };
    }

    private Object call(ExprNode.Call call) {
        return switch (call.name()) {
            case WhitelistFunctions.PROVINCE -> attr(call.name(), context.province());
            case WhitelistFunctions.USER_ROLE -> attr(call.name(), context.userRole());
            case WhitelistFunctions.ORG_ID -> attr(call.name(), context.orgId());
            case WhitelistFunctions.USER_LEVEL -> attr(call.name(), context.userLevel());
            case WhitelistFunctions.HAS_TAG -> hasTag(call);
            case WhitelistFunctions.REGISTER_WITHIN_DAYS -> registerWithinDays(call);
            case WhitelistFunctions.IN_CROWD -> inCrowd(call);
            default -> Boolean.FALSE;
        };
    }

    private Object attr(String name, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            nullAttrHits.add(name + "()");
            return NullAttr.INSTANCE;
        }
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        return value;
    }

    private Boolean hasTag(ExprNode.Call call) {
        Object arg = eval(call.args().get(0));
        if (!(arg instanceof String tag)) {
            return Boolean.FALSE;
        }
        if (context.tagsMissing()) {
            return Boolean.FALSE;
        }
        return context.tags().contains(tag);
    }

    private Boolean registerWithinDays(ExprNode.Call call) {
        Object arg = eval(call.args().get(0));
        if (!(arg instanceof Long days) || days < 0L) {
            return Boolean.FALSE;
        }
        Instant registeredAt = context.registeredAt();
        Instant now = context.now();
        if (registeredAt == null || now == null) {
            return Boolean.FALSE;
        }
        long elapsedDays = Duration.between(registeredAt, now).toDays();
        return elapsedDays <= days;
    }

    private Boolean inCrowd(ExprNode.Call call) {
        Object arg = eval(call.args().get(0));
        if (!(arg instanceof String code) || code.isBlank()) {
            return Boolean.FALSE;
        }
        CrowdResolver resolver = context.crowds();
        if (resolver == null) {
            log.warn("inCrowd pack missing or disabled: {}", code);
            return Boolean.FALSE;
        }
        return resolver.contains(code);
    }

    private Boolean unary(ExprNode.Unary unary) {
        Object value = eval(unary.operand());
        boolean truth = value instanceof Boolean bool && bool;
        return !truth;
    }

    private Object binary(ExprNode.Binary binary) {
        if (binary.op() == TokenType.AND) {
            return isTrue(eval(binary.left())) && isTrue(eval(binary.right()));
        }
        if (binary.op() == TokenType.OR) {
            return isTrue(eval(binary.left())) || isTrue(eval(binary.right()));
        }
        Object left = eval(binary.left());
        Object right = eval(binary.right());
        if (left instanceof NullAttr || right instanceof NullAttr) {
            return Boolean.FALSE;
        }
        return compare(binary.op(), left, right);
    }

    private Boolean in(ExprNode.In in) {
        Object left = eval(in.left());
        if (left instanceof NullAttr) {
            return Boolean.FALSE;
        }
        boolean hit = false;
        for (ExprNode valueNode : in.values()) {
            Object value = eval(valueNode);
            if (value instanceof NullAttr) {
                continue;
            }
            if (Objects.equals(left, value)) {
                hit = true;
                break;
            }
        }
        return in.negated() ? !hit : hit;
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static Boolean compare(TokenType op, Object left, Object right) {
        if (left instanceof Long leftNum && right instanceof Long rightNum) {
            int cmp = Long.compare(leftNum, rightNum);
            return switch (op) {
                case EQ -> cmp == 0;
                case NE -> cmp != 0;
                case GT -> cmp > 0;
                case GE -> cmp >= 0;
                case LT -> cmp < 0;
                case LE -> cmp <= 0;
                default -> Boolean.FALSE;
            };
        }
        if (left instanceof String leftText && right instanceof String rightText) {
            return switch (op) {
                case EQ -> leftText.equals(rightText);
                case NE -> !leftText.equals(rightText);
                default -> Boolean.FALSE;
            };
        }
        return Boolean.FALSE;
    }
}
