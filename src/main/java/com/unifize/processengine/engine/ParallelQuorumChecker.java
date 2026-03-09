package com.unifize.processengine.engine;

import com.unifize.processengine.model.QuorumPolicy;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface ParallelQuorumChecker {
    boolean isQuorumSatisfied(QuorumPolicy quorumPolicy, List<StepState> participantStates);

    boolean hasAnyRejection(List<StepState> participantStates);

    List<String> getRemainingParticipants(List<StepState> participantStates);

    /**
     * Returns a new list with the updated StepState replacing the record sharing its stepStateId.
     * If no match is found the updated state is appended. Owned here so ProcessEngine stays thin.
     */
    List<StepState> mergeUpdatedState(List<StepState> existing, StepState updated);
}
