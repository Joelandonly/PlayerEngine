package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.executor.TaskStepExecutorAdapter;
import com.player2.playerengine.patch.ToolRequestRouting;
import com.player2.playerengine.player2api.AgentCommandUtils;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.tasks.crafting.CraftMacroTasks;
import com.player2.playerengine.tasks.smithing.resolver.SmithingRecipeAccess;
import com.player2.playerengine.tasks.smithing.resolver.SmithingRecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerLevel;

public class GetCommand extends Command {
   private static final SmithingRecipeAccess SMITHING_RECIPE_ACCESS = new SmithingRecipeAccessImpl();

   public GetCommand() throws CommandException {
      super(
         "get",
         "Crafts or gathers any item that has a vanilla or modded crafting recipe. Accepts any registered item name. For MODDED items you MUST use the full registry id in the form namespace:path — e.g. `get iceandfire:podium_oak 1`, NOT a bare name and NOT the translation/lang key you see in inventory (which looks like `block.iceandfire.podium_oak`). Bare names work only for vanilla items (e.g. `get diamond 1`). If you only know an item's display/lang key, get its real registry id from `inspect ITEM <id>` or an advanced tooltip (F3+H). You can craft an item even if you don't have ingredients in inventory already. Examples: `get log 20` gets 20 logs, `get diamond_chestplate 1` gets 1 diamond chestplate, `get iceandfire:podium_oak 1` crafts 1 modded podium. For equipments you have to specify the type of equipments like wooden, stone, iron, golden and diamond. For wooden items, prefer the GENERIC name — `get sign 1`, `get planks 4`, `get log 2`, NOT `get oak_sign 1` — so the best wood species actually available nearby is chosen automatically; only name a species if that exact wood is required.",
         new Arg<>(ItemList.class, "items")
      );
   }

   private void getItems(PlayerEngineController mod, ItemTarget... items) {
      items = AgentCommandUtils.addPresentItemsToTargets(mod, items);
      if (items != null && items.length != 0) {
         Task targetTask = this.resolveTask(mod, items);
         if (targetTask != null) {
            Task trackedTask = targetTask;
            mod.runUserTaskTracked(buildStepId(items), "get_items", targetTask, RollbackPolicy.NONE, () -> this.onGetComplete(mod, trackedTask));
         } else {
            this.finish();
         }
      } else {
         mod.log("You must specify at least one item!");
         this.finish();
      }
   }

   private void onGetComplete(PlayerEngineController mod, Task trackedTask) {
      boolean genuineFailure = false;
      if (mod.getStepExecutorAdapter() instanceof TaskStepExecutorAdapter adapter) {
         Optional<StepExecution> execOpt = adapter.getLastCompletedExecution();
         genuineFailure = execOpt.<Boolean>map(e -> e.getState() == StepState.FAILED && e.getLastLogEntry().contains(StopReason.FATAL.name())).orElse(false);
      }

      String creationNote = null;
      String terminationReason = null;
      if (trackedTask instanceof CraftMacroResourceTask macro) {
         creationNote = blankToNull(macro.getCreationNote());
         terminationReason = blankToNull(macro.getFailureReason());
      } else if (trackedTask instanceof ResourceTask rt) {
         terminationReason = rt.getAggregatedFailureReason().map(GetCommand::blankToNull).orElse(null);
      }

      if (genuineFailure) {
         String failureMsg = joinNotes(creationNote, getFailureReason(trackedTask));
         mod.reportAgenticProgress(failureMsg, true);
         this.finishWithError(failureMsg);
      } else if (terminationReason == null && creationNote == null) {
         this.finish();
      } else {
         String degradeMsg = joinNotes(creationNote, terminationReason);
         mod.reportAgenticProgress(degradeMsg, true);
         this.finishWithNote(degradeMsg);
      }
   }

   private static String blankToNull(String s) {
      return s != null && !s.isBlank() ? s : null;
   }

   private static String joinNotes(String first, String second) {
      if (first == null) {
         return second;
      } else {
         return second == null ? first : first + "; " + second;
      }
   }

   private static String getFailureReason(Task trackedTask) {
      if (trackedTask instanceof CraftMacroResourceTask macro) {
         String reason = macro.getFailureReason();
         if (reason != null && !reason.isBlank()) {
            return reason;
         }
      } else if (trackedTask instanceof ResourceTask rt) {
         Optional<String> reason = rt.getAggregatedFailureReason();
         if (reason.isPresent() && !reason.get().isBlank()) {
            return reason.get();
         }
      }

      return "could not obtain the requested item(s)";
   }

   private Task resolveTask(PlayerEngineController mod, ItemTarget... items) {
      if (items.length == 1) {
         Object var10000 = ToolRequestRouting.select(items[0]);
         if (var10000 != null) {
            return (Task)var10000;
         }
      }

      if (items.length == 1 && items[0].getMatches().length == 1) {
         String exactId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items[0].getMatches()[0]).toString();
         var acquisition = com.player2.playerengine.acquisition.NativeAcquisitionPlanner.choose(
             mod, exactId, items[0].getTargetCount(), null);
         if (acquisition.task() != null) return acquisition.task();
         if (acquisition.route() == com.player2.playerengine.acquisition.NativeAcquisitionPlanner.Route.ALREADY_HELD)
             return null;
         if (com.player2.playerengine.acquisition.ProjectMaterialPlanner.recipeBacked(exactId)) {
             this.finishWithError(acquisition.reason());
             return null; // no fallback to in-world scavenging of a crafted block
         }
      }
      if (items.length == 1) {
         CraftMacroTasks.MacroProbe probe = CraftMacroTasks.macroOutcome(mod, items[0]);
         if (probe.outcome() == CraftMacroTasks.MacroOutcome.MACRO_CREATED) {
            return probe.task();
         }

         Task catalogueTask = TaskCatalogue.getItemTask(items[0]);
         if (catalogueTask != null) {
            return catalogueTask;
         }

         if (probe.outcome() == CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE && items[0].getMatches().length == 1) {
            String requestedItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items[0].getMatches()[0]).toString();
            var knowledgeTask = com.player2.playerengine.blockknowledge.KnowledgeAcquisition.task(mod, requestedItemId, items[0].getTargetCount());
            if (knowledgeTask.isPresent()) return knowledgeTask.get();
         }

         if (probe.outcome() == CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE) {
            String itemName = items[0].getMatches().length == 1 ? ItemHelper.stripItemName(items[0].getMatches()[0]) : "that item";
            if (items[0].getMatches().length == 1) {
               Item outputItem = items[0].getMatches()[0];
               ServerLevel world = mod.getWorld();
               if (world != null) {
                  Optional<SmithingRecipeAccess.SmithingResolution> smithResolution = SMITHING_RECIPE_ACCESS.resolve(
                     world.getRecipeManager(), outputItem, world.registryAccess()
                  );
                  if (smithResolution.isPresent()) {
                     String smithRedirect = itemName + " is a smithing-table upgrade — use `smith " + itemName.replace(' ', '_') + "` instead of `get`.";
                     mod.reportAgenticProgress(smithRedirect, true);
                     this.finishWithError(smithRedirect);
                     return null;
                  }
               }
            }

            String rejection = "Cannot craft "
               + itemName
               + ": there is no crafting-table recipe for it and no smithing-table transform recipe for it. If it can be smelted or mined, use `smelt` or `mine` directly.";
            mod.reportAgenticProgress(rejection, true);
            this.finishWithError(rejection);
         } else {
            this.finish();
         }

         return null;
      } else {
         return TaskCatalogue.getSquashedItemTask(items);
      }
   }

   private static String buildStepId(ItemTarget... items) {
      List<String> parts = new ArrayList<>();

      for (ItemTarget target : items) {
         if (target.isCatalogueItem()) {
            parts.add(target.getCatalogueName() + "x" + target.getTargetCount());
         } else if (target.getMatches().length == 1) {
            parts.add(ItemHelper.stripItemName(target.getMatches()[0]) + "x" + target.getTargetCount());
         } else {
            parts.add("multi");
         }
      }

      return "get:" + String.join(",", parts);
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemList items;
      try {
         items = parser.get(ItemList.class);
      } catch (CommandException e) {
         mod.reportAgenticProgress(e.getMessage(), true);
         throw e;
      }

      this.getItems(mod, items.items);
   }
}
