package com.mkt.task.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import java.util.Collections;
import java.util.EnumSet;

/**
 * Locked-down Aviator instance (T2 / design §5.10). The closed DSL is parsed by
 * {@link ExpressionParser}; Aviator is not given raw operator text because AND/in are not native.
 */
final class AviatorSandbox {

    private AviatorSandbox() {}

    static AviatorEvaluatorInstance lockedEngine() {
        AviatorEvaluatorInstance engine = AviatorEvaluator.newInstance();
        engine.setOption(Options.FEATURE_SET, EnumSet.noneOf(Feature.class));
        engine.setOption(Options.ALLOWED_CLASS_SET, Collections.emptySet());
        engine.setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, false);
        engine.setOption(Options.EVAL_TIMEOUT_MS, 50L);
        return engine;
    }
}
