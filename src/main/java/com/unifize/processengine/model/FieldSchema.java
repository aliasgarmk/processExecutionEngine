package com.unifize.processengine.model;

public record FieldSchema(
        String name,
        boolean required,
        String regex
) {
}
