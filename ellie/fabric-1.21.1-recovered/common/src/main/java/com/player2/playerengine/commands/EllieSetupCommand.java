/* Recovered from the pinned baseline; setup contract updated to required kit plus placed base. */
package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgBase;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.setup.BasicSetupTask;

public final class EllieSetupCommand
extends Command {
    public EllieSetupCommand() {
        super("setup", "Gather wood and stone in batches, make wooden and stone tools, craft 64 torches, and place a furnace and chest beside a verified crafting bench registered as HOME. No shelter, iron kit, lighting placement, or unrelated survival goals.", new ArgBase[0]);
    }

    @Override
    protected void call(PlayerEngineController playerEngineController, ArgParser argParser) throws CommandException {
        BasicSetupTask ellieSetupTask = new BasicSetupTask();
        playerEngineController.runUserTaskTracked("ellie_starter_setup", "setup", (Task)ellieSetupTask, RollbackPolicy.NONE, () -> {
            String string = ellieSetupTask.controlledReason();
            if (ellieSetupTask.isSuccessful()) {
                this.finishWithNote("[info-result]" + string);
            } else if (ellieSetupTask.isTerminal()) {
                this.finishWithError(string);
            } else {
                this.finishWithNote(string);
            }
        });
    }
}
