package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.behavior.IPathingBehavior;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ForceCancelCommand extends Command {
   public ForceCancelCommand() {
      super("forcecancel");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
      pathingBehavior.cancelEverything();
      pathingBehavior.forceCancel();
      this.logDirect(source, "ok force canceled");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Force cancel";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("Like cancel, but more forceful.", "", "Usage:", "> forcecancel");
   }
}
