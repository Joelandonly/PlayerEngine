package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.executor.TaskStepExecutorAdapter;
import com.player2.playerengine.tasks.movement.FollowPlayerTask;

public class FollowCommand extends Command {
   public FollowCommand() throws CommandException {
      super(
         "follow", "Follows you or someone else. Example: `follow Player` to follow player with username=Player", new Arg<>(String.class, "username", null, 0)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String username = parser.get(String.class);
      if (username == null) {
         if (mod.getOwner() == null) {
            mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
            this.finish();
            return;
         }

         username = mod.getOwner().getName().getString();
      }

      mod.runUserTaskTracked(
         "follow_player-" + username,
         "follow_player",
         new FollowPlayerTask(username),
         RollbackPolicy.NONE,
         () -> {
            if (mod.getStepExecutorAdapter() instanceof TaskStepExecutorAdapter adapter
               && adapter.getLastCompletedExecution()
                  .map(e -> e.getState() == StepState.FAILED && e.getLastLogEntry().contains(StopReason.FOLLOWED_TARGET_GONE.name()))
                  .orElse(false)) {
               this.finishWithNote("the followed player died or disconnected, so following stopped — do not retry follow until they confirm they are back");
            } else if (mod.getStepExecutorAdapter() instanceof TaskStepExecutorAdapter adapter
               && adapter.getLastCompletedExecution()
                  .map(e -> e.getState() == StepState.FAILED && e.getLastLogEntry().contains(StopReason.CANCELLED_SUPERSEDED_BY_SURVIVAL.name()))
                  .orElse(false)) {
               this.finishWithNote("following paused because I had to stop and eat to survive — say follow again if you want me to keep following");
            } else {
               this.finish();
            }
         }
      );
   }
}
