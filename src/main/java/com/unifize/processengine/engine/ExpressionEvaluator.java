package com.unifize.processengine.engine;

import java.util.Map;

public interface ExpressionEvaluator {
    boolean evaluate(String expression, Map<String, Object> context);
}
