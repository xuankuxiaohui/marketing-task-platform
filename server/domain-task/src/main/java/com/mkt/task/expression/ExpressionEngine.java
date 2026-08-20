package com.mkt.task.expression;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expression sandbox: length / AST whitelist / 7 functions / long domain / node cap (R11.9).
 */
public final class ExpressionEngine {

    private ExpressionEngine() {}

    public static CompiledExpression compile(String source) {
        String expr = source == null ? "" : source;
        if (expr.isBlank()) {
            throw new ExpressionCompileException(ExpressionError.at(1, 1, "表达式为空"));
        }
        if (expr.length() > ExpressionLimits.MAX_LENGTH) {
            throw new ExpressionCompileException(ExpressionError.at(1, 1, "表达式长度超过 " + ExpressionLimits.MAX_LENGTH));
        }
        rejectDeepParens(expr);
        List<Token> tokens = new ExpressionLexer(expr).tokenize();
        ExpressionParser parser = new ExpressionParser(tokens);
        ExprNode root = parser.parse();
        return new CompiledExpression(expr, root, parser.nodeCount());
    }

    public static boolean evaluate(CompiledExpression compiled, EvalContext context) {
        return new ExpressionEvaluator(context).evaluate(compiled.root());
    }

    public static ExpressionValidateResult validate(String source, ExpressionType type, Clock clock, CrowdResolver crowds) {
        if (type == null) {
            return ExpressionValidateResult.fail(ExpressionError.at(1, 1, "type 仅允许 FILTER|BRANCH"));
        }
        try {
            CompiledExpression compiled = compile(source);
            Instant now = clock == null ? Instant.EPOCH : clock.instant();
            ExpressionEvaluator evaluator =
                    new ExpressionEvaluator(EvalContext.missingAttributes(now, crowds));
            evaluator.evaluate(compiled.root());
            return ExpressionValidateResult.ok(evaluator.nullAttrHits());
        } catch (ExpressionCompileException ex) {
            return ExpressionValidateResult.fail(ex.detail());
        }
    }

    public static void requireValid(String source) {
        compile(source);
    }

    private static void rejectDeepParens(String expr) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') {
                depth++;
                if (depth > max) {
                    max = depth;
                }
                if (max > ExpressionLimits.MAX_NODES) {
                    throw new ExpressionCompileException(ExpressionError.at(1, i + 1, "括号嵌套超过节点上限"));
                }
            } else if (c == ')') {
                depth--;
            }
        }
    }
}
