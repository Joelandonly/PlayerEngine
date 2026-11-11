package com.player2.playerengine.help;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class HelpfulCommand {
   private HelpfulCommand() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> leaf(String rootId, String helpPath, String usage, String shortDescKey, int permLevel) {
      return leaf(rootId, helpPath, usage, shortDescKey, null, List.of(), permLevel, null, null);
   }

   public static LiteralArgumentBuilder<CommandSourceStack> leaf(
      String rootId,
      String helpPath,
      String usage,
      String shortDescKey,
      String longDescKey,
      List<ArgNote> argNotes,
      int permLevel,
      String permNoteKey,
      String category
   ) {
      if (shortDescKey != null && !shortDescKey.isBlank()) {
         String canonical = HelpPath.normalize(helpPath);
         if (canonical.isEmpty()) {
            throw new IllegalArgumentException("HelpfulCommand.leaf: helpPath must be non-blank");
         }

         String[] segments = canonical.split(" ");
         String literalName = segments[segments.length - 1];
         HelpRegistry.register(new HelpEntry(rootId, canonical, usage, shortDescKey, longDescKey, argNotes, permLevel, permNoteKey, category));
         return (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal(literalName).requires(source -> source.hasPermission(permLevel));
      } else {
         throw new IllegalArgumentException("HelpfulCommand.leaf: shortDescKey must be a non-blank translatable key (path=" + helpPath + ")");
      }
   }
}
