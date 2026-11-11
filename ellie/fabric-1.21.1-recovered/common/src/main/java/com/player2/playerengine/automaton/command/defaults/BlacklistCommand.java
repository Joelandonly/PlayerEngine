package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.process.IGetToBlockProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class BlacklistCommand extends Command {
   public BlacklistCommand() {
      super("blacklist");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IGetToBlockProcess proc = baritone.getGetToBlockProcess();
      if (!proc.isActive()) {
         throw new CommandInvalidStateException("GetToBlockProcess is not currently active");
      }

      if (proc.blacklistClosest()) {
         this.logDirect(source, "Blacklisted closest instances");
      } else {
         throw new CommandInvalidStateException("No known locations, unable to blacklist");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Blacklist closest block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "While going to a block this command blacklists the closest block so that block finding processes won't attempt to get to it.",
         "",
         "Usage:",
         "> blacklist"
      );
   }
}
