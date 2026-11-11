package com.player2.playerengine.containeraccess;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ContainerScanService {
   private ContainerScanService() {
   }

   public static ContainerScanService.ScanOutcome scan(
      PlayerEngineController controller, BlockPos pos, ScanMode mode, List<StorageItemArgs.ItemQuery> targets
   ) {
      if (mode != ScanMode.TARGETED || targets != null && !targets.isEmpty()) {
         ServerLevel level = controller.getWorld();
         ContainerResolver.Resolution resolution = ContainerResolver.resolve(level, pos);
         if (!resolution.ok()) {
            return ContainerScanService.ScanOutcome.failure(resolution.code(), resolution.detail());
         }

         ContainerSnapshot snapshot = snapshot(level, resolution.resolved(), mode, targets);
         refreshLegacyCache(controller, resolution.resolved().canonicalPos(), resolution.resolved().secondaryPos());
         return ContainerScanService.ScanOutcome.success(snapshot);
      } else {
         return ContainerScanService.ScanOutcome.failure(StorageAccessCode.INVALID_ARGUMENT, "targeted mode requires item names");
      }
   }

   public static ContainerSnapshot snapshot(ServerLevel level, ResolvedContainer resolved, ScanMode mode, List<StorageItemArgs.ItemQuery> targets) {
      Container container = resolved.container();
      int totalSlots = container.getContainerSize();
      TreeMap<String, Integer> counts = new TreeMap<>();
      List<IndexedSlot> slots = mode == ScanMode.DEEP ? new ArrayList<>() : null;
      int emptySlots = 0;

      for (int i = 0; i < totalSlots; i++) {
         ItemStack stack = container.getItem(i);
         if (stack.isEmpty()) {
            emptySlots++;
         } else {
            String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(registryId, stack.getCount(), Integer::sum);
            if (slots != null) {
               slots.add(new IndexedSlot(i, registryId, stack.getCount(), false));
            }
         }
      }

      List<ItemCount> aggregate;
      if (mode == ScanMode.TARGETED) {
         TreeMap<String, Integer> filtered = new TreeMap<>();
         if (targets != null) {
            for (StorageItemArgs.ItemQuery query : targets) {
               String fullId = BuiltInRegistries.ITEM.getKey(query.item()).toString();
               filtered.put(fullId, counts.getOrDefault(fullId, 0));
            }
         }

         aggregate = toItemCounts(filtered);
      } else {
         aggregate = toItemCounts(counts);
      }

      return new ContainerSnapshot(
         level.dimension().location().toString(),
         resolved.canonicalPos(),
         resolved.secondaryPos(),
         resolved.kind(),
         totalSlots,
         emptySlots,
         aggregate,
         slots == null ? null : List.copyOf(slots),
         level.getGameTime()
      );
   }

   public static void refreshLegacyCache(PlayerEngineController controller, BlockPos canonicalPos, BlockPos secondaryPos) {
      controller.getItemStorage().containers.WritableCache(controller, canonicalPos);
      Debug.logInternal("StorageAccess: refreshed container cache @ " + ContainerResolver.formatPos(canonicalPos));
      if (secondaryPos != null) {
         controller.getItemStorage().containers.WritableCache(controller, secondaryPos);
         Debug.logInternal("StorageAccess: refreshed container cache @ " + ContainerResolver.formatPos(secondaryPos));
      }
   }

   private static List<ItemCount> toItemCounts(Map<String, Integer> sortedCounts) {
      List<ItemCount> result = new ArrayList<>(sortedCounts.size());

      for (Entry<String, Integer> entry : sortedCounts.entrySet()) {
         result.add(new ItemCount(entry.getKey(), entry.getValue()));
      }

      return List.copyOf(result);
   }

   public record ScanOutcome(ContainerSnapshot snapshot, StorageAccessCode code, String detail) {
      public boolean ok() {
         return this.code == StorageAccessCode.OK;
      }

      public static ContainerScanService.ScanOutcome success(ContainerSnapshot snapshot) {
         return new ContainerScanService.ScanOutcome(snapshot, StorageAccessCode.OK, "");
      }

      public static ContainerScanService.ScanOutcome failure(StorageAccessCode code, String detail) {
         return new ContainerScanService.ScanOutcome(null, code, detail);
      }
   }
}
