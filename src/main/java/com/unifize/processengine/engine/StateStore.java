package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DuplicateInstanceException;
import com.unifize.processengine.exception.InstanceNotFoundException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.model.AuditEntry;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface StateStore {
    void saveInstance(ProcessInstance instance, List<StepState> initialStepStates, List<AuditEntry> auditEntries)
            throws DuplicateInstanceException;

    void updateInstance(ProcessInstance instance, List<StepState> stepStates, List<AuditEntry> auditEntries)
            throws OptimisticLockException;

    ProcessInstance loadInstance(String instanceId) throws InstanceNotFoundException;

    List<StepState> loadStepStates(String instanceId);

    List<StepState> loadStepStatesForStep(String instanceId, String stepId);
}
