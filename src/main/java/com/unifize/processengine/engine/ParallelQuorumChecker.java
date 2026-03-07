package com.unifize.processengine.engine;

import com.unifize.processengine.model.QuorumPolicy;
import com.unifize.processengine.model.StepState;

import java.util.List;

public interface ParallelQuorumChecker {
    boolean isQuorumSatisfied(QuorumPolicy quorumPolicy, List<StepState> participantStates);

    boolean hasAnyRejection(List<StepState> participantStates);

    List<String> getRemainingParticipants(List<StepState> participantStates);
}
