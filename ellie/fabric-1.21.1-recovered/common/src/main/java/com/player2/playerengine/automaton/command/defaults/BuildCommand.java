package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeBlockPos;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeFile;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.utils.DirUtil;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import org.apache.commons.io.FilenameUtils;

public class BuildCommand extends Command {
   private static final File schematicsDir = DirUtil.getGameDir().resolve("schematics").toFile();

   public BuildCommand() {
      super("build");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      File file = args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
      if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
         file = new File(file.getAbsolutePath() + "." + baritone.settings().schematicFallbackExtension.get());
      }

      BetterBlockPos origin = new BetterBlockPos(BlockPos.containing(source.getPosition()));
      BetterBlockPos buildOrigin;
      if (args.hasAny()) {
         args.requireMax(3);
         buildOrigin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
      } else {
         args.requireMax(0);
         buildOrigin = origin;
      }

      boolean success = baritone.getBuilderProcess().build(file.getName(), file, buildOrigin);
      if (!success) {
         throw new CommandInvalidStateException(
            "Couldn't load the schematic. Make sure to use the FULL file name, including the extension (e.g. blah.schematic)."
         );
      }

      this.logDirect(source, String.format("Successfully loaded schematic for building\nOrigin: %s", buildOrigin));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasExactlyOne()) {
         return RelativeFile.tabComplete(args, schematicsDir);
      } else if (args.has(2)) {
         args.get();
         return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
      } else {
         return Stream.empty();
      }
   }

   @Override
   public String getShortDesc() {
      return "Build a schematic";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Build a schematic from a file.",
         "",
         "Usage:",
         "> build <filename> - Loads and builds '<filename>.schematic'",
         "> build <filename> <x> <y> <z> - Custom position"
      );
   }
}
