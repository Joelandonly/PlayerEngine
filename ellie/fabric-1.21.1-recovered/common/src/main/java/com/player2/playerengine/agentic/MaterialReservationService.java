package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;

public final class MaterialReservationService {
   private final Map<Item, Integer> reserved = new HashMap<>();

   public int free(PlayerEngineController controller, Item item) {
      return this.freeAgainst(controller.getItemStorage().getItemCount(item), item);
   }

   public int freeInventoryOnly(PlayerEngineController controller, Item item) {
      return this.freeAgainst(controller.getItemStorage().getItemCountInventoryOnly(item), item);
   }

   private int freeAgainst(int held, Item item) {
      int r = Math.min(this.reserved.getOrDefault(item, 0), held);
      return Math.max(0, held - r);
   }

   public int reserve(PlayerEngineController controller, Item item, int amount) {
      int grant = Math.min(Math.max(0, amount), this.free(controller, item));
      if (grant > 0) {
         this.reserved.merge(item, grant, Integer::sum);
      }

      return grant;
   }

   public void release(Item item, int amount) {
      if (amount > 0) {
         this.reserved.computeIfPresent(item, (k, v) -> Math.max(0, v - amount));
      }
   }

   public void clear() {
      this.reserved.clear();
   }
}
