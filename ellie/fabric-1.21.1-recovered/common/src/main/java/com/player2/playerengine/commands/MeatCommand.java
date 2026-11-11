package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.resources.CollectMeatTask;

public class MeatCommand extends Command {
   public MeatCommand() throws CommandException {
      super(
         "meat",
         "Gather additional cooked-meat hunger points. Count is NOT item count: cooked chicken is 6 points and cooked beef is 8. For exact item counts use get cooked_chicken <count> or get cooked_beef <count>. Failed hunting/cooking reports BLOCKED.",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      int count = FoodAcquisition.validateRequested(parser.get(Integer.class));
      count += FoodAcquisition.startingScore(mod, true);
      ProjectTaskGateway.run(mod, new CollectMeatTask(count), () -> this.finish());
   }
}
