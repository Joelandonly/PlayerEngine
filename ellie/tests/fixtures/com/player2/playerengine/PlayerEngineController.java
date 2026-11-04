package com.player2.playerengine;
import com.player2.playerengine.executor.*;
import com.player2.playerengine.player2api.AIPersistantData;
import java.util.Optional;
/** Test boundary only: no Minecraft world or NPC is simulated. */
public class PlayerEngineController {
    private final IStepExecutorAdapter adapter;
    private final com.player2.playerengine.trackers.storage.ItemStorageTracker inventory = new com.player2.playerengine.trackers.storage.ItemStorageTracker();
    public PlayerEngineController(IStepExecutorAdapter adapter) { this.adapter = adapter; }
    public IStepExecutorAdapter getStepExecutorAdapter() { return adapter; }
    public PlayerEngineSettings getModSettings() { return new PlayerEngineSettings(); }
    public com.player2.playerengine.trackers.storage.ItemStorageTracker getItemStorage() { return inventory; }
    private com.player2.playerengine.commands.base.CommandExecutor commands;
    public com.player2.playerengine.commands.base.CommandExecutor getCommandExecutor(){if(commands==null)commands=new com.player2.playerengine.commands.base.CommandExecutor(this);return commands;}
    public void log(String message) {}
    public Optional<StepExecution> getActiveTrackedStep() { return adapter.getActiveExecution(); }
    public AIPersistantData getAIPersistantData() { throw new IllegalStateException("No game in local harness"); }
}
