/* Recovered from the pinned baseline; setup contract updated to required kit plus placed base. */
package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgBase;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.setup.EllieSetupTask;

public final class EllieSetupCommand
extends Command {
    public EllieSetupCommand() {
        super("setup", "Prepare a persistent chest-anchored survival base: tools, enclosed shelter, accessible workshop, shield/iron equipment, food and fuel reserves, and 32 unplaced torches. Reuse actual inventory and verified base storage, take cover when needed, and report blocked stages honestly. No buckets, armor, diamonds, farms or automatic lighting.", new ArgBase[0]);
    }

    @Override
    protected void call(PlayerEngineController playerEngineController, ArgParser argParser) throws CommandException {
        EllieSetupTask ellieSetupTask = new EllieSetupTask();
        playerEngineController.runUserTaskTracked("ellie_starter_setup", "setup", (Task)ellieSetupTask, RollbackPolicy.NONE, () -> {
            String string = ellieSetupTask.outcomeForCommand();
            if (ellieSetupTask.isSuccessfullyCompleted()) {
                this.finishWithNote("[info-result]" + string);
            } else if (ellieSetupTask.isBlocked()) {
                this.finishWithError(string);
            } else {
                this.finishWithNote(string);
            }
        });
    }
}
