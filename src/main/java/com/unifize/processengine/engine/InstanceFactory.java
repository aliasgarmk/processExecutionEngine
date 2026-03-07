package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessDefinition;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.User;

import java.util.Map;

public interface InstanceFactory {
    ProcessInstance createInstance(ProcessDefinition definition, User initiator, Map<String, Object> initialFields);
}
