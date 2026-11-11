package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.agentic.MineBlockParams;
import com.player2.playerengine.tasks.agentic.MineBlockTask;
import net.minecraft.network.chat.Component;

public class MineCommand extends Command {
   public MineCommand() throws CommandException {
      super(
         "mine",
         "mine <block> [count]. Mine and collect up to [count] of the nearest matching blocks of the named type (ores, stone, deepslate, obsidian, gravel, etc.). The companion deterministically picks and acquires the right pickaxe tier if it lacks one, breaks the block(s), and collects the drops. Count is a number of BLOCKS to break (a block may drop multiple items or, with too-weak a tool, none) and defaults to 1. Examples: `mine iron_ore 8`, `mine cobblestone 32`, `mine minecraft:deepslate`.",
         new Arg<>(String.class, "block"),
         new Arg<>(Integer.class, "count", 1, 1)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String blockId;
      int requested;
      try {
         blockId = parser.get(String.class);
         requested = parser.get(Integer.class);
      } catch (CommandException e) {
         mod.reportAgenticProgress(e.getMessage(), true);
         this.finishWithError(e.getMessage());
         return;
      }

      if (blockId != null && !blockId.isBlank()) {
         String block = blockId.trim();
         int count = Math.max(1, Math.min(256, requested));
         MineBlockParams params = new MineBlockParams(block, count, 32.0, 120.0, 8, 6);
         MineBlockTask task = new MineBlockTask(params, null, null);
         mod.runUserTaskTracked("mine:" + block + "x" + count, "mine_block", task, RollbackPolicy.NONE, () -> this.onMineComplete(mod, task, count));
      } else {
         mod.reportAgenticProgress(Component.translatable("message.playerengine.mine.specify_block"), true);
         this.finishWithError("Specify a block to mine, e.g. `mine iron_ore 8`.");
      }
   }

   private void onMineComplete(PlayerEngineController mod, MineBlockTask task, int count) {
      MineBlockTask.Outcome o = task.outcome();
      if (o == null) {
         this.finish();
      } else {
         Component playerLine = playerLine(o, count);
         mod.reportAgenticProgress(playerLine, true);
         switch (o.kind()) {
            case CLEAN_SUCCESS:
               this.finishWithInfo("mined " + o.mined() + " " + o.blockId());
               break;
            case PARTIAL_NO_DROPS:
               this.finishWithNote("partial: mined " + o.mined() + " " + o.blockId() + "; " + o.noDrops() + " gave no drops (tool too weak)");
               break;
            case PARTIAL_TIMEOUT:
               this.finishWithNote(
                  "partial: mined " + o.mined() + " of " + count + " " + o.blockId() + "; timed out before finishing (more may remain — retrying can continue)"
               );
               break;
            case PARTIAL_RANGE_EXHAUSTED:
               this.finishWithNote("partial: mined " + o.mined() + " " + o.blockId() + "; no more " + o.blockId() + " within range (relocate to mine more)");
               break;
            case PARTIAL_DROPS_LOST:
               this.finishWithNote(
                  "partial: mined " + o.mined() + " " + o.blockId() + "; " + o.lostDrops() + " drop(s) fell into water/void and could not be collected"
               );
               break;
            case FAILED:
               this.finishWithError("could not mine " + o.blockId() + ": " + readable(o.reasonToken()));
         }
      }
   }

   private static Component playerLine(MineBlockTask.Outcome o, int count) {
      Component block = display(o.blockId());

      return switch (o.kind()) {
         case CLEAN_SUCCESS -> Component.translatable("message.playerengine.mine.success", new Object[]{o.mined(), block});
         case PARTIAL_NO_DROPS -> Component.translatable("message.playerengine.mine.partial_no_drops", new Object[]{o.mined(), block, o.noDrops()});
         case PARTIAL_TIMEOUT -> Component.translatable("message.playerengine.mine.partial_timeout", new Object[]{o.mined(), count, block});
         case PARTIAL_RANGE_EXHAUSTED -> Component.translatable("message.playerengine.mine.partial_range_exhausted", new Object[]{o.mined(), block});
         case PARTIAL_DROPS_LOST -> Component.translatable("message.playerengine.mine.partial_drops_lost", new Object[]{o.mined(), block, o.lostDrops()});
         case FAILED -> Component.translatable("message.playerengine.mine.failed", new Object[]{block, readable(o.reasonToken())});
      };
   }

   private static Component display(String id) {
      if (id != null && !id.isBlank()) {
         String s = id.trim();
         int colon = s.indexOf(58);
         if (colon >= 0 && colon + 1 < s.length()) {
            s = s.substring(colon + 1);
         }

         if (s.startsWith("#")) {
            s = s.substring(1);
         }

         return Component.literal(s.replace('_', ' '));
      } else {
         return Component.translatable("message.playerengine.mine.block_fallback");
      }
   }

   private static String readable(String token) {
      if (token != null && !token.isBlank()) {
         String t = token.trim();
         if (t.startsWith("could_not_acquire_tool:")) {
            String tier = t.substring("could_not_acquire_tool:".length());
            return "couldn't get a " + tier + " pickaxe";
         }

         return switch (t) {
            case "unknown_block_id" -> "that block name isn't recognized";
            case "no_target_block_in_range" -> "no matching block within range";
            case "mine_timeout", "timeout" -> "timed out";
            case "no_more_blocks_in_range" -> "no more matching blocks within range";
            case "incorrect_tool_no_drops" -> "tool too weak";
            case "drops_unreachable" -> "drops fell into water or void";
            case "interrupted" -> "interrupted";
            default -> t.replace('_', ' ').replace(':', ' ');
         };
      } else {
         return "unknown reason";
      }
   }
}
