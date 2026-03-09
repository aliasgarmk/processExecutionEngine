package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.model.ProcessDefinition;

public interface DefinitionValidator {
    void validate(ProcessDefinition definition) throws DefinitionValidationException;

    /**
     * Validates that all regex strings in the definition are syntactically correct.
     * Catches PatternSyntaxException from FieldSchema construction and re-throws as
     * DefinitionValidationException so callers receive a structured error at load time.
     */
    void compilePatterns(ProcessDefinition definition) throws DefinitionValidationException;
}
