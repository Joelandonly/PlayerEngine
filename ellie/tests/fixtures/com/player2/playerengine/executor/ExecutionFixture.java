package com.player2.playerengine.executor;
/** Uses the real baseline StepExecution state machine, not an imitation. */
public class ExecutionFixture {
    public static StepExecution receipt(String id, String kind, StepState state, String reason) {
        StepExecution execution = new StepExecution(id, kind);
        if (state != StepState.PENDING && state != StepState.BLOCKED) execution.transitionTo(StepState.RUNNING, "start");
        if (state != StepState.PENDING && state != StepState.RUNNING) execution.transitionTo(state, reason);
        return execution;
    }
}
