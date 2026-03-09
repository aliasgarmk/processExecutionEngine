package com.unifize.processengine.engine;

import com.unifize.processengine.model.QuorumMode;
import com.unifize.processengine.model.QuorumPolicy;
import com.unifize.processengine.model.StepState;
import com.unifize.processengine.model.StepStatus;

import java.util.ArrayList;
import java.util.List;

public final class DefaultParallelQuorumChecker implements ParallelQuorumChecker {

    @Override
    public boolean isQuorumSatisfied(QuorumPolicy quorumPolicy, List<StepState> participantStates) {
        long completed = participantStates.stream()
                .filter(state -> state.status() == StepStatus.COMPLETED)
                .count();
        return switch (quorumPolicy.mode()) {
            case ALL -> completed == participantStates.size();
            case MAJORITY -> completed > participantStates.size() / 2;
        };
    }

    @Override
    public boolean hasAnyRejection(List<StepState> participantStates) {
        return participantStates.stream().anyMatch(state -> state.status() == StepStatus.REJECTED);
    }

    @Override
    public List<String> getRemainingParticipants(List<StepState> participantStates) {
        return participantStates.stream()
                .filter(state -> state.status() == StepStatus.PENDING
                        || state.status() == StepStatus.IN_PROGRESS)
                .map(StepState::assignedTo)
                .toList();
    }

    @Override
    public List<StepState> mergeUpdatedState(List<StepState> existing, StepState updated) {
        List<StepState> merged = new ArrayList<>(existing.size());
        boolean replaced = false;
        for (StepState state : existing) {
            if (state.stepStateId().equals(updated.stepStateId())) {
                merged.add(updated);
                replaced = true;
            } else {
                merged.add(state);
            }
        }
        if (!replaced) {
            merged.add(updated);
        }
        return merged;
    }
}
