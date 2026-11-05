package com.player2.playerengine.tasks;
import com.player2.playerengine.tasks.base.*;
/** Resource classification boundary only. Task.tick and shared recovery are production bytecode. */
public abstract class ResourceTask extends Task implements ITaskCanForce {
    public boolean shouldForce(Task task) { return false; }
}
