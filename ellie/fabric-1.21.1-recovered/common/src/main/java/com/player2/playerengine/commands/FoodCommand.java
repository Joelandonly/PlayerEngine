package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.resources.CollectFoodTask;

public class FoodCommand extends Command {
   public FoodCommand() throws CommandException {
      super(
         "food",
         "Gather additional food measured in hunger points, cooking raw meat as needed. Count is NOT item count: one cooked chicken is 6 points. For an exact number of food items use get <food_item> <count>. Missing resources stop with a specific BLOCKED reason.",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      int foodPoints = FoodAcquisition.validateRequested(parser.get(Integer.class));
      foodPoints += FoodAcquisition.startingScore(mod, false);
      ProjectTaskGateway.run(mod, new CollectFoodTask(foodPoints), () -> this.finish());
   }
}
