package com.unifize.processengine.engine;

import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepDefinition;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface StepActivator {
    List<StepState> activateStep(ProcessInstance instance, StepDefinition stepDefinition);
}
