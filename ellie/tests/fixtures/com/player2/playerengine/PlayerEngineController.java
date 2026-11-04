package com.player2.playerengine;
import com.player2.playerengine.executor.*;
import com.player2.playerengine.player2api.AIPersistantData;
import java.util.Optional;
/** Test boundary only: no Minecraft world or NPC is simulated. */
public class PlayerEngineController {
    private final IStepExecutorAdapter adapter;
    public PlayerEngineController(IStepExecutorAdapter adapter) { this.adapter = adapter; }
    public IStepExecutorAdapter getStepExecutorAdapter() { return adapter; }
    public PlayerEngineSettings getModSettings() { return new PlayerEngineSettings(); }
    public Optional<StepExecution> getActiveTrackedStep() { return adapter.getActiveExecution(); }
    public AIPersistantData getAIPersistantData() { throw new IllegalStateException("No game in local harness"); }
}
