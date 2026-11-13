package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.entity.PatrolTask;
import net.minecraft.core.BlockPos;

public final class PatrolCommand extends Command {
   public PatrolCommand() {
      super("patrol", "Patrol from Ellie’s current position to x y z. Use patrol status or patrol off.");
   }

   @Override protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String[] args = parser.getArgUnits();
      var current = mod.getUserTaskChain().getCurrentTask();
      if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
         finishWithInfo(current instanceof PatrolTask patrol ? patrol.status() : "Patrol is off.");
         return;
      }
      if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
         if (current instanceof PatrolTask) mod.cancelUserTask();
         finishWithInfo("Patrol is off.");
         return;
      }
      if (args.length != 3) throw new CommandException("Expected patrol x y z | status | off");
      BlockPos end;
      try {
         end = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
      } catch (NumberFormatException invalid) {
         throw new CommandException("Patrol coordinates must be whole numbers.");
      }
      var world = mod.getWorld();
      BlockPos start = mod.getEntity().blockPosition();
      if (!world.isInWorldBounds(end) || !world.getWorldBorder().isWithinBounds(end) || end.equals(start)) {
         throw new CommandException("Choose a different patrol endpoint inside the world border.");
      }
      mod.runUserTaskTracked("patrol", "patrol", new PatrolTask(start, end, world.dimension()), RollbackPolicy.NONE, this::finish);
   }
}
