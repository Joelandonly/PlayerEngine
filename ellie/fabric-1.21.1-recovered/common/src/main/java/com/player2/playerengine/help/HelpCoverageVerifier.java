package com.player2.playerengine.help;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;

public final class HelpCoverageVerifier {
   private static final String[] ROOTS = new String[]{"playerengine", "player2npc"};
   private static final Map<String, List<String>> EXPLICIT_REQUIRED = Map.of("playerengine", List.of("capability inspect", "rag retrieve", "routing probe"));

   private HelpCoverageVerifier() {
   }

   public static void verify(CommandDispatcher<CommandSourceStack> dispatcher) {
      boolean strict = Boolean.getBoolean("playerengine.help.strict");
      List<String> misses = new ArrayList<>();

      for (String rootId : ROOTS) {
         CommandNode<CommandSourceStack> rootNode = dispatcher.getRoot().getChild(rootId);
         if (rootNode != null) {
            if (strict) {
               Debug.logWarning("HelpCoverageVerifier: root '%s' has %d registered help entries", rootId, HelpRegistry.entriesFor(rootId).size());
            }

            for (CommandNode<CommandSourceStack> child : rootNode.getChildren()) {
               walk(rootId, child, new ArrayList<>(), misses);
            }
         }
      }

      for (Entry<String, List<String>> required : EXPLICIT_REQUIRED.entrySet()) {
         String rootId = required.getKey();
         if (dispatcher.getRoot().getChild(rootId) != null) {
            for (String path : required.getValue()) {
               if (!HelpRegistry.has(rootId, path)) {
                  misses.add(rootId + " " + path);
               }
            }
         }
      }

      if (!misses.isEmpty()) {
         if (strict) {
            throw new IllegalStateException("Help coverage gaps (" + misses.size() + " undocumented leaf/leaves): " + String.join("; ", misses));
         }

         for (String miss : misses) {
            Debug.logWarning("HelpCoverageVerifier: undocumented command leaf: %s", miss);
         }
      }
   }

   private static void walk(String rootId, CommandNode<CommandSourceStack> node, List<String> parentTrail, List<String> misses) {
      List<String> trail = parentTrail;
      if (node instanceof LiteralCommandNode) {
         trail = new ArrayList<>(parentTrail);
         trail.add(node.getName());
         if (isCountedLeaf(node)) {
            String path = HelpPath.of(trail);
            if (!HelpRegistry.has(rootId, path)) {
               misses.add(rootId + " " + path);
            }
         }
      }

      for (CommandNode<CommandSourceStack> child : node.getChildren()) {
         walk(rootId, child, trail, misses);
      }
   }

   private static boolean isCountedLeaf(CommandNode<CommandSourceStack> node) {
      if (!(node instanceof LiteralCommandNode)) {
         return false;
      }

      if (node.getCommand() != null) {
         return true;
      }

      List<CommandNode<CommandSourceStack>> children = new ArrayList<>(node.getChildren());
      if (children.isEmpty()) {
         return false;
      }

      for (CommandNode<CommandSourceStack> child : children) {
         if (!(child instanceof ArgumentCommandNode) || child.getCommand() == null) {
            return false;
         }
      }

      return true;
   }
}
