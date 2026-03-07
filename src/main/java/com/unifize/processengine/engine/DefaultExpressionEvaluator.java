package com.unifize.processengine.engine;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DefaultExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        String[] orParts = expression.split("(?i)\\s+OR\\s+");
        for (String orPart : orParts) {
            if (evaluateAndExpression(orPart.trim(), context)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateAndExpression(String expression, Map<String, Object> context) {
        String[] andParts = expression.split("(?i)\\s+AND\\s+");
        for (String andPart : andParts) {
            if (!evaluateClause(andPart.trim(), context)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateClause(String clause, Map<String, Object> context) {
        if (clause.isBlank()) {
            return true;
        }

        String upper = clause.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" IS NOT EMPTY")) {
            String field = clause.substring(0, upper.indexOf(" IS NOT EMPTY")).trim();
            return !isBlank(context.get(field));
        }
        if (upper.endsWith(" IS EMPTY")) {
            String field = clause.substring(0, upper.indexOf(" IS EMPTY")).trim();
            return isBlank(context.get(field));
        }
        if (clause.contains("==")) {
            String[] parts = clause.split("==", 2);
            return Objects.equals(normalize(context.get(parts[0].trim())), normalize(unquote(parts[1].trim())));
        }
        if (clause.contains("!=")) {
            String[] parts = clause.split("!=", 2);
            return !Objects.equals(normalize(context.get(parts[0].trim())), normalize(unquote(parts[1].trim())));
        }

        throw new IllegalArgumentException("Unsupported expression clause: " + clause);
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue.trim();
        }
        return value;
    }

    private static String unquote(String token) {
        if ((token.startsWith("'") && token.endsWith("'")) || (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    private static boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String stringValue) {
            return stringValue.isBlank();
        }
        return false;
    }
}
