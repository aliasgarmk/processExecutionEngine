package com.unifize.processengine.engine;

import com.unifize.processengine.exception.DuplicateInstanceException;
import com.unifize.processengine.exception.InstanceNotFoundException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.model.ProcessInstance;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface StateStore {

    /**
     * Persists a new ProcessInstance and its initial StepState records atomically.
     * Throws DuplicateInstanceException if the instanceId already exists.
     */
    void saveInstance(ProcessInstance instance, List<StepState> initialStepStates)
            throws DuplicateInstanceException;

    /**
     * Atomically updates the ProcessInstance and upserts the provided StepState records.
     * Increments the lock version on success.
     * Throws OptimisticLockException if the stored lock version differs from instance.version().
     */
    void updateInstance(ProcessInstance instance, List<StepState> stepStates)
            throws OptimisticLockException;

    ProcessInstance loadInstance(String instanceId) throws InstanceNotFoundException;

    List<StepState> loadStepStates(String instanceId);

    List<StepState> loadStepStatesForStep(String instanceId, String stepId);
}
