package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DefinitionValidationException;
import com.unifize.processengine.model.ProcessDefinition;

public interface DefinitionValidator {
    void validate(ProcessDefinition definition) throws DefinitionValidationException;
}
