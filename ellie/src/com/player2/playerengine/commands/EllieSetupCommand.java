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
        super("setup", "Prepare a full starter kit (iron-or-better tools and armor, shield, two buckets, 32 torches, 16 bread, bed) and a safe nearby workstation/storage base (one crafting table, one furnace, eight separated chest blocks). Verify inventory and placed blocks; no house or farm is implied. Blocks are placed using native tasks, not LLM blueprint generation.", new ArgBase[0]);
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
