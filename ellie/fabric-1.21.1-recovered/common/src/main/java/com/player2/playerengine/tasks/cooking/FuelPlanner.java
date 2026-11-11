package com.player2.playerengine.tasks.cooking;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class FuelPlanner {
   public Optional<FuelPlanner.FuelPlan> plan(
      int inputCount, int cookTicksPerItem, PlayerEngineController controller, @Nullable MaterialReservationService ledger
   ) {
      if (inputCount >= 1 && cookTicksPerItem >= 1) {
         int totalCookTicks = inputCount * cookTicksPerItem;
         Map<Item, Integer> burnMap = getFuelBurnMap();
         Optional<FuelPlanner.FuelPlan> result = tryCandidate(Items.CHARCOAL, totalCookTicks, burnMap, controller, ledger);
         if (result.isPresent()) {
            return result;
         }

         result = tryCandidate(Items.COAL, totalCookTicks, burnMap, controller, ledger);
         if (result.isPresent()) {
            return result;
         }

         for (Item plank : ItemHelper.PLANKS) {
            result = tryCandidate(plank, totalCookTicks, burnMap, controller, ledger);
            if (result.isPresent()) {
               return result;
            }
         }

         for (Item log : ItemHelper.LOG) {
            result = tryCandidate(log, totalCookTicks, burnMap, controller, ledger);
            if (result.isPresent()) {
               return result;
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   public Optional<FuelPlanner.DeficitCandidate> deficitFor(
      int inputCount, int cookTicksPerItem, PlayerEngineController controller, @Nullable MaterialReservationService ledger
   ) {
      if (inputCount >= 1 && cookTicksPerItem >= 1 && controller != null) {
         int totalCookTicks = inputCount * cookTicksPerItem;
         Map<Item, Integer> burnMap = getFuelBurnMap();
         FuelPlanner.DeficitCandidate best = null;
         if (StorageHelper.miningRequirementMetInventory(controller, MiningRequirement.WOOD)) {
            best = pickSmaller(best, candidateFor(Items.COAL, totalCookTicks, burnMap, controller, ledger));
         }

         for (Item plank : ItemHelper.PLANKS) {
            best = pickSmaller(best, candidateFor(plank, totalCookTicks, burnMap, controller, ledger));
         }

         for (Item log : ItemHelper.LOG) {
            best = pickSmaller(best, candidateFor(log, totalCookTicks, burnMap, controller, ledger));
         }

         return Optional.ofNullable(best);
      } else {
         return Optional.empty();
      }
   }

   @Nullable
   private static FuelPlanner.DeficitCandidate candidateFor(
      Item item, int totalCookTicks, Map<Item, Integer> burnMap, PlayerEngineController controller, @Nullable MaterialReservationService ledger
   ) {
      if (item != Items.CHARCOAL && item != Items.LAVA_BUCKET) {
         Integer burnTicks = burnMap.get(item);
         if (burnTicks != null && burnTicks > 0) {
            int needed = (totalCookTicks + burnTicks - 1) / burnTicks;
            int held = ledger != null ? ledger.freeInventoryOnly(controller, item) : controller.getItemStorage().getItemCountInventoryOnly(item);
            int deficit = needed - held;
            return deficit <= 0 ? null : new FuelPlanner.DeficitCandidate(item, needed, held, deficit, burnTicks);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static FuelPlanner.DeficitCandidate pickSmaller(@Nullable FuelPlanner.DeficitCandidate a, @Nullable FuelPlanner.DeficitCandidate b) {
      if (a == null) {
         return b;
      } else if (b == null) {
         return a;
      } else {
         return b.deficit() < a.deficit() ? b : a;
      }
   }

   @Nullable
   public static ResourceTask gatherTaskFor(FuelPlanner.DeficitCandidate cand) {
      if (cand == null) {
         return null;
      }

      Item item = cand.item();
      int count = cand.needed();
      if (cand.held() == 0) {
         if (contains(ItemHelper.PLANKS, item) && TaskCatalogue.taskExists("planks")) {
            return TaskCatalogue.getItemTask("planks", count);
         }

         if (contains(ItemHelper.LOG, item) && TaskCatalogue.taskExists("log")) {
            return TaskCatalogue.getItemTask("log", count);
         }
      }

      if (TaskCatalogue.taskExists(item)) {
         return TaskCatalogue.getItemTask(item, count);
      } else if (contains(ItemHelper.LOG, item) && TaskCatalogue.taskExists("log")) {
         return TaskCatalogue.getItemTask("log", count);
      } else {
         return contains(ItemHelper.PLANKS, item) && TaskCatalogue.taskExists("planks")
            ? TaskCatalogue.getItemTask("planks", count)
            : TaskCatalogue.getItemTask(item, count);
      }
   }

   private static boolean contains(Item[] arr, Item item) {
      for (Item candidate : arr) {
         if (candidate == item) {
            return true;
         }
      }

      return false;
   }

   private static Optional<FuelPlanner.FuelPlan> tryCandidate(
      Item candidate,
      int totalCookTicks,
      Map<Item, Integer> burnMap,
      PlayerEngineController controller,
      @Nullable MaterialReservationService ledger
   ) {
      if (candidate == Items.LAVA_BUCKET) {
         return Optional.empty();
      }

      Integer burnTicks = burnMap.get(candidate);
      if (burnTicks != null && burnTicks > 0) {
         int needed = (totalCookTicks + burnTicks - 1) / burnTicks;
         int held = ledger != null ? ledger.freeInventoryOnly(controller, candidate) : controller.getItemStorage().getItemCountInventoryOnly(candidate);
         if (held >= needed) {
            if (ledger != null) {
               ledger.reserve(controller, candidate, needed);
            }

            return Optional.of(new FuelPlanner.FuelPlan(candidate, needed, totalCookTicks));
         } else {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private static Map<Item, Integer> getFuelBurnMap() {
      return AbstractFurnaceBlockEntity.getFuel();
   }

   public record DeficitCandidate(Item item, int needed, int held, int deficit, int burnTicks) {
   }

   public record FuelPlan(Item fuelItem, int fuelCount, int totalCookTicks) {
   }
}
