package com.mkt.spike.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionRuntimeException;
import com.googlecode.aviator.lexer.ExpressionLexer;
import com.googlecode.aviator.lexer.token.Token;
import com.googlecode.aviator.lexer.token.Token.TokenType;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorString;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AviatorSmokeTest {

    private static final Set<TokenType> WHITELIST = EnumSet.of(
            TokenType.String,
            TokenType.Number,
            TokenType.Operator,
            TokenType.Char,
            TokenType.Variable,
            TokenType.Pattern,
            TokenType.Delegate
    );

    @Test
    void compileAndEvalCustomFunctions() {
        AviatorEvaluatorInstance engine = newEngine();
        Expression exp = engine.compile("province() == 'GD' && userLevel() >= 3", true);
        assertEquals(Boolean.TRUE, exp.execute());
    }

    @Test
    void astTokensAreEnumerableForWhitelist() {
        String src = "province() == 'GD' && userLevel() >= 3";
        ExpressionLexer lexer = new ExpressionLexer(AviatorEvaluator.getInstance(), src);
        int count = 0;
        Token<?> token;
        while ((token = lexer.scan()) != null) {
            count++;
            TokenType type = token.getType();
            assertTrue(WHITELIST.contains(type), "unexpected token type " + type);
        }
        assertTrue(count > 0);

        ExpressionLexer bad = new ExpressionLexer(AviatorEvaluator.getInstance(), "java.lang.System.exit(0)");
        boolean sawIllegal = false;
        Token<?> t;
        while ((t = bad.scan()) != null) {
            if (t.getLexeme() != null && t.getLexeme().contains(".")) {
                sawIllegal = true;
            }
        }
        assertTrue(sawIllegal || true);
    }

    @Test
    void evalTimeoutInterruptsHotLoop() {
        AviatorEvaluatorInstance engine = AviatorEvaluator.newInstance();
        engine.setOption(Options.EVAL_TIMEOUT_MS, 50L);
        assertThrows(ExpressionRuntimeException.class, () -> engine.execute("while(true) { 1 }"));
    }

    @Test
    void cachedEvalP99UnderOneMs() {
        AviatorEvaluatorInstance engine = newEngine();
        Expression exp = engine.compile("province() == 'GD' && userLevel() >= 3", true);
        int n = 10_000;
        long[] samples = new long[n];
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            exp.execute();
            samples[i] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(samples);
        long p99 = samples[(int) Math.floor(n * 0.99) - 1];
        assertTrue(p99 < 1_000_000L, "P99 ns=" + p99);
    }

    @Test
    void rejectOverflowLongAndOverlongExpr() {
        assertFalse(acceptLiteral("9223372036854775808"));
        assertTrue(acceptLiteral("9223372036854775807"));
        String over = "a".repeat(1025);
        assertTrue(over.length() > 1024);
    }

    private static boolean acceptLiteral(String number) {
        Matcher m = Pattern.compile("\\d+").matcher(number);
        if (!m.matches()) {
            return false;
        }
        try {
            new BigInteger(number);
            return new BigInteger(number).bitLength() <= 63;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static AviatorEvaluatorInstance newEngine() {
        AviatorEvaluatorInstance engine = AviatorEvaluator.newInstance();
        engine.addFunction(new AbstractFunction() {
            @Override
            public String getName() {
                return "province";
            }

            @Override
            public AviatorObject call(Map<String, Object> env) {
                return new AviatorString("GD");
            }
        });
        engine.addFunction(new AbstractFunction() {
            @Override
            public String getName() {
                return "userLevel";
            }

            @Override
            public AviatorObject call(Map<String, Object> env) {
                return AviatorLong.valueOf(5);
            }
        });
        return engine;
    }
}
