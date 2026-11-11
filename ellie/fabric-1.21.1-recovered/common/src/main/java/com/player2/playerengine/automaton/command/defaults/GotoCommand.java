package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.BlockById;
import com.player2.playerengine.automaton.api.command.datatypes.ForBlockOptionalMeta;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeCoordinate;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeGoal;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class GotoCommand extends Command {
   protected GotoCommand() {
      super("goto");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
         args.requireMax(3);
         BetterBlockPos origin = baritone.getEntityContext().feetPos();
         Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
         this.logDirect(source, String.format("Going to: %s", goal.toString()));
         baritone.getCustomGoalProcess().setGoalAndPath(goal);
      } else {
         args.requireMax(1);
         BlockOptionalMeta destination = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
         baritone.getGetToBlockProcess().getToBlock(destination);
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      return args.tabCompleteDatatype(BlockById.INSTANCE);
   }

   @Override
   public String getShortDesc() {
      return "Go to a coordinate or block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The goto command tells Automatone to head towards a given goal or block.",
         "",
         "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft commands. Or, you can just use regular numbers.",
         "",
         "Usage:",
         "> goto <block> - Go to a block, wherever it is in the world",
         "> goto <y> - Go to a Y level",
         "> goto <x> <z> - Go to an X,Z position",
         "> goto <x> <y> <z> - Go to an X,Y,Z position"
      );
   }
}
