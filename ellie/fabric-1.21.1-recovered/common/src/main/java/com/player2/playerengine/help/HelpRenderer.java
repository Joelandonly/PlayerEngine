package com.player2.playerengine.help;

import com.mojang.brigadier.tree.CommandNode;
import com.player2.playerengine.automaton.api.command.helpers.Paginator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public final class HelpRenderer {
   public static final int PAGE_SIZE = 7;

   private HelpRenderer() {
   }

   public static void index(String rootId, int page, CommandSourceStack source) {
      List<HelpEntry> visible = new ArrayList<>();

      for (HelpEntry entry : HelpRegistry.entriesFor(rootId)) {
         if (canRun(source, entry)) {
            visible.add(entry);
         }
      }

      if (visible.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("help." + rootId + ".index_empty").withStyle(ChatFormatting.GRAY), false);
      } else {
         source.sendSuccess(() -> Component.translatable("help." + rootId + ".index_header").withStyle(ChatFormatting.GOLD), false);
         Paginator<HelpEntry> paginator = new Paginator<>(source, visible).setPageSize(7);
         int clamped = Math.max(1, Math.min(page, paginator.getMaxPage()));
         paginator.skipPages(clamped - 1);
         paginator.display(HelpRenderer::rowComponent, "/" + rootId + " help");
      }
   }

   public static void detail(String rootId, String path, int page, CommandSourceStack source) {
      String canonical = HelpPath.normalize(path);
      HelpEntry exact = HelpRegistry.get(rootId, canonical);
      if (exact != null) {
         renderLeaf(exact, source);
      } else {
         String prefix = canonical + " ";
         List<HelpEntry> family = new ArrayList<>();

         for (HelpEntry entry : HelpRegistry.entriesFor(rootId)) {
            if (entry.path().startsWith(prefix) && canRun(source, entry)) {
               family.add(entry);
            }
         }

         if (family.isEmpty()) {
            source.sendFailure(Component.translatable("help." + rootId + ".unknown"));
         } else if (family.size() == 1) {
            renderLeaf(family.get(0), source);
         } else if (family.size() > 7) {
            Paginator<HelpEntry> paginator = new Paginator<>(source, family).setPageSize(7);
            int clamped = Math.max(1, Math.min(page, paginator.getMaxPage()));
            paginator.skipPages(clamped - 1);
            paginator.display(HelpRenderer::rowComponent, "/" + rootId + " help " + canonical);
         } else {
            for (HelpEntry entry : family) {
               source.sendSuccess(() -> rowComponent(entry), false);
            }
         }
      }
   }

   private static Component rowComponent(HelpEntry entry) {
      return Component.literal(entry.usage())
         .withStyle(ChatFormatting.GOLD)
         .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
         .append(Component.translatable(entry.shortDescKey()).withStyle(ChatFormatting.GRAY));
   }

   private static void renderLeaf(HelpEntry entry, CommandSourceStack source) {
      source.sendSuccess(() -> Component.literal(entry.usage()).withStyle(ChatFormatting.GOLD), false);
      String descKey = entry.longDescKey() != null && !entry.longDescKey().isBlank() ? entry.longDescKey() : entry.shortDescKey();
      source.sendSuccess(() -> Component.translatable(descKey).withStyle(ChatFormatting.GRAY), false);

      for (ArgNote note : entry.argNotes()) {
         source.sendSuccess(() -> {
            MutableComponent line = Component.literal(note.argName() + ": ").withStyle(ChatFormatting.YELLOW);
            line.append(Component.translatable(note.noteKey()).withStyle(ChatFormatting.GRAY));
            return line;
         }, false);
      }

      if (entry.permNoteKey() != null && !entry.permNoteKey().isBlank()) {
         source.sendSuccess(() -> Component.translatable(entry.permNoteKey()).withStyle(ChatFormatting.RED), false);
      }
   }

   private static boolean canRun(CommandSourceStack source, HelpEntry entry) {
      MinecraftServer server = source.getServer();
      if (server == null) {
         return source.hasPermission(entry.permLevel());
      }

      CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild(entry.rootId());
      if (node == null) {
         return source.hasPermission(entry.permLevel());
      }

      if (!testRequirement(node, source)) {
         return false;
      }

      String canonical = HelpPath.normalize(entry.path());
      if (!canonical.isEmpty()) {
         for (String segment : canonical.split(" ")) {
            CommandNode<CommandSourceStack> child = node.getChild(segment);
            if (child == null) {
               break;
            }

            node = child;
            if (!testRequirement(node, source)) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean testRequirement(CommandNode<CommandSourceStack> node, CommandSourceStack source) {
      try {
         return node.getRequirement().test(source);
      } catch (RuntimeException ignored) {
         return false;
      }
   }
}
