package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.cooking.SmeltDeferredParams;
import com.player2.playerengine.tasks.cooking.SmeltDeferredTask;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;

public class SmeltCommand extends Command {
   private static final CookingRecipeAccess COOKING_RECIPE_ACCESS = new CookingRecipeAccessImpl();

   public SmeltCommand() throws CommandException {
      super(
         "smelt",
         "smelt <item> [count]. Smelt, blast, or smoke an item using a nearby furnace, blast furnace, or smoker. The companion walks to the furnace, loads the input plus enough fuel (charcoal/coal/planks preferred, sized from the recipe), waits while it cooks, then collects the result — you do not have to babysit it. The output is resolved from the recipe registry, so any smeltable raw material works (e.g. raw iron -> iron ingot, raw food -> cooked food). Count defaults to 1. Examples: `smelt iron 16`, `smelt raw_iron 32`, `smelt potato 8`.",
         new Arg<>(ItemList.class, "item")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      PlayerEngineSettings settings = mod.getModSettings();
      if (!settings.isEnableDeferredSmelt()) {
         mod.reportAgenticProgress(Component.translatable("message.playerengine.smelt.disabled"), true);
         this.finishWithError("Deferred smelting is disabled in the configuration.");
      } else {
         ItemList items;
         try {
            items = parser.get(ItemList.class);
         } catch (CommandException e) {
            mod.reportAgenticProgress(e.getMessage(), true);
            throw e;
         }

         if (items.items != null && items.items.length == 1) {
            ItemTarget target = items.items[0];
            Item input = target.getMatches().length == 1 ? target.getMatches()[0] : null;
            if (input == null) {
               mod.reportAgenticProgress(Component.translatable("message.playerengine.smelt.invalid_args_concrete"), true);
               this.finishWithError("Specify a single concrete item to smelt, e.g. `smelt raw_iron 16`.");
            } else {
               int requested = target.getTargetCount();
               int maxBatch = settings.getDeferredSmeltMaxBatch();
               int count = Math.max(1, Math.min(maxBatch, requested));
               ServerLevel world = mod.getWorld();
               if (world != null) {
                  Optional<CookingRecipeAccess.CookResolution> resolved = COOKING_RECIPE_ACCESS.resolveAny(world.getRecipeManager(), input, world.registryAccess());
                  if (resolved.isEmpty()) {
                     String name = displayName(input);
                     mod.reportAgenticProgress(Component.translatable("message.playerengine.smelt.no_recipe_preflight", new Object[]{name}), true);
                     this.finishWithError("Can't smelt " + name + ": it has no furnace, blast furnace, or smoker recipe.");
                     return;
                  }
               }

               SmeltDeferredParams params = new SmeltDeferredParams.Builder(input, count)
                  .stallPolls(settings.getDeferredSmeltStallPolls())
                  .timeoutSeconds(settings.getDeferredSmeltTimeoutSeconds())
                  .build();
               SmeltDeferredTask task = new SmeltDeferredTask(params);
               mod.runUserTaskTracked(buildStepId(input, count), "smelt_items", task, RollbackPolicy.NONE, () -> this.onSmeltComplete(mod, task));
            }
         } else {
            mod.reportAgenticProgress(Component.translatable("message.playerengine.smelt.invalid_args_count"), true);
            this.finishWithError("Specify exactly one item to smelt, e.g. `smelt iron 16`.");
         }
      }
   }

   private void onSmeltComplete(PlayerEngineController mod, SmeltDeferredTask task) {
      SmeltDeferredTask.Outcome o = task.outcome();
      if (o == null) {
         this.finish();
      } else {
         Component playerLine = playerLine(o);
         mod.reportAgenticProgress(playerLine, true);
         switch (o.kind()) {
            case CLEAN_SUCCESS:
               this.finishWithInfo("smelted " + o.collected() + " " + o.outputName() + posSuffix(o));
               break;
            case PARTIAL_OUT_OF_FUEL:
               this.finishWithNote("partial: smelted " + o.collected() + " of " + o.expected() + " " + o.outputName() + "; out of fuel");
               break;
            case TAMPERED:
               this.finishWithNote("tampered: expected " + o.expected() + ", collected " + o.collected() + " " + o.outputName());
               break;
            case STALLED_RETRYING:
               this.finishWithNote(
                  "stalled: the furnace chunk stopped ticking; collected "
                     + o.collected()
                     + " of "
                     + o.expected()
                     + " "
                     + o.outputName()
                     + " — run `smelt` again near the furnace to finish the rest"
               );
               break;
            case FURNACE_IN_USE:
               this.finishWithError("furnace in use: the furnace already had items in its input or fuel slot" + posSuffix(o) + " — nothing loaded");
               break;
            case NO_FUEL:
               this.finishWithError("no fuel: needed about " + o.fuelNeeded() + " fuel to smelt " + o.expected() + " " + o.outputName());
               break;
            case STALLED_TIMEOUT:
               this.finishWithError(
                  "stalled timeout: the furnace chunk stopped ticking and the job timed out (collected " + o.collected() + " of " + o.expected() + ")"
               );
               break;
            case FURNACE_GONE:
               this.finishWithError("furnace removed" + posSuffix(o) + " — collected " + o.collected() + " of " + o.expected());
               break;
            case NO_RECIPE:
               this.finishWithError("no recipe: that item has no furnace/blast/smoke recipe");
               break;
            case SETUP_FAILED:
               this.finishWithError("could not start the smelt (" + o.reasonLabel() + ")");
         }
      }
   }

   private static Component playerLine(SmeltDeferredTask.Outcome o) {
      String out = displayName(o.outputItem());

      return switch (o.kind()) {
         case CLEAN_SUCCESS -> Component.translatable("message.playerengine.smelt.result.success", new Object[]{o.collected(), out});
         case PARTIAL_OUT_OF_FUEL -> Component.translatable("message.playerengine.smelt.result.partial_fuel", new Object[]{o.collected(), o.expected(), out});
         case TAMPERED -> Component.translatable("message.playerengine.smelt.result.tampered", new Object[]{o.collected()});
         case STALLED_RETRYING -> Component.translatable("message.playerengine.smelt.result.stalled_retrying", new Object[]{o.collected(), o.expected()});
         case FURNACE_IN_USE -> Component.translatable("message.playerengine.smelt.result.furnace_in_use");
         case NO_FUEL -> Component.translatable("message.playerengine.smelt.result.no_fuel", new Object[]{o.fuelNeeded()});
         case STALLED_TIMEOUT -> Component.translatable("message.playerengine.smelt.result.stalled_timeout");
         case FURNACE_GONE -> Component.translatable("message.playerengine.smelt.result.furnace_gone");
         case NO_RECIPE -> Component.translatable("message.playerengine.smelt.result.no_recipe");
         case SETUP_FAILED -> Component.translatable("message.playerengine.smelt.result.setup_failed", new Object[]{o.reasonLabel()});
      };
   }

   private static String posSuffix(SmeltDeferredTask.Outcome o) {
      return o.furnacePos() != null ? " at " + o.furnacePos().getX() + " " + o.furnacePos().getY() + " " + o.furnacePos().getZ() : "";
   }

   private static String displayName(Item item) {
      return item == null ? "that item" : BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
   }

   private static String buildStepId(Item input, int count) {
      return "smelt:" + BuiltInRegistries.ITEM.getKey(input).getPath() + "x" + count;
   }
}
