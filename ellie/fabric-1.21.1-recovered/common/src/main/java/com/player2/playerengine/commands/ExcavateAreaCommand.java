package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.construction.ExcavateAreaTask;

public final class ExcavateAreaCommand extends Command {
   public ExcavateAreaCommand() throws CommandException {
      super(
         "excavate",
         "Excavate a bounded 3D area with Ellie's native builder. Usage: excavate x1 y1 z1 x2 y2 z2 (inclusive corners). Rejects chests, block entities, fluids, protected depots and unloaded chunks.",
         new Arg<>(Integer.class, "x1"),
         new Arg<>(Integer.class, "y1"),
         new Arg<>(Integer.class, "z1"),
         new Arg<>(Integer.class, "x2"),
         new Arg<>(Integer.class, "y2"),
         new Arg<>(Integer.class, "z2")
      );
   }

   @Override
   protected void call(PlayerEngineController var1, ArgParser var2) throws CommandException {
      int var3 = var2.get(Integer.class);
      int var4 = var2.get(Integer.class);
      int var5 = var2.get(Integer.class);
      int var6 = var2.get(Integer.class);
      int var7 = var2.get(Integer.class);
      int var8 = var2.get(Integer.class);

      try {
         ProjectTaskGateway.run(var1, new ExcavateAreaTask(var3, var4, var5, var6, var7, var8), () -> this.finish());
      } catch (Exception | LinkageError var10) {
         this.finishWithError("Excavate: " + var10.getMessage());
      }
   }
}
