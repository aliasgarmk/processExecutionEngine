package com.unifize.processengine.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Describes one field in a step's schema.
 * The compiledPattern is derived from regex in the compact constructor so that
 * Pattern.compile is paid once per definition load, not on every validation call.
 */
public record FieldSchema(
        String name,
        boolean required,
        String regex,
        Pattern compiledPattern
) {
    /** Canonical compact constructor — compiles regex eagerly. */
    public FieldSchema {
        compiledPattern = (regex != null && !regex.isBlank()) ? Pattern.compile(regex) : null;
    }

    /** Convenience constructor for callers that specify name, required, and regex. */
    public FieldSchema(String name, boolean required, String regex) {
        this(name, required, regex, null); // compiledPattern computed in compact constructor
    }

    /** Convenience constructor for fields without a regex constraint. */
    public FieldSchema(String name, boolean required) {
        this(name, required, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldSchema other)) return false;
        return required == other.required
                && Objects.equals(name, other.name)
                && Objects.equals(regex, other.regex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, required, regex);
    }
}
