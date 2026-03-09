package com.unifize.processengine.exception;

/**
 * Thrown when an expression clause cannot be evaluated — typically because the
 * definition contains an unsupported operator or malformed condition string.
 * Callers (e.g. RoutingRuleEvaluator) should catch this and wrap it with routing context.
 */
public class ExpressionEvaluationException extends RuntimeException {
    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
