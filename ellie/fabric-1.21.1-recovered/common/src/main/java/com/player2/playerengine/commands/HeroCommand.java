package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.entity.HeroTask;
import java.util.Locale;

public class HeroCommand extends Command {
   public HeroCommand() throws CommandException {
      super("hero", "Guard the owner with twelve animated combat techniques. Use off to exit or status to check.",
         new Arg<>(String.class, "on|off|status", "on", 0, false));
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String action = parser.get(String.class).toLowerCase(Locale.ROOT);
      boolean active = mod.getUserTaskChain().getCurrentTask() instanceof HeroTask;
      switch (action) {
         case "on" -> {
            if (active) this.finishWithInfo("Hero mode is already active.");
            else {
               mod.runUserTaskTracked("hero", "hero", new HeroTask(), RollbackPolicy.NONE, this::finish);
            }
         }
         case "off" -> {
            if (active) mod.cancelUserTask();
            this.finishWithInfo("Hero mode is off.");
         }
         case "status" -> this.finishWithInfo(active ? "Hero mode is active." : "Hero mode is off.");
         default -> throw new CommandException("Usage: hero [on|off|status]");
      }
   }
}
