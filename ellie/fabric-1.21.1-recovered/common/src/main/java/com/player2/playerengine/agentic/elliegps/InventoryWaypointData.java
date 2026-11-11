package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.containeraccess.ItemCount;
import java.util.List;

public final class InventoryWaypointData {
   public int[] secondaryPos;
   public String containerKind;
   public InventoryWaypointData.Snapshot snapshot;

   public InventoryWaypointData() {
   }

   public InventoryWaypointData(int[] secondaryPos, String containerKind, InventoryWaypointData.Snapshot snapshot) {
      this.secondaryPos = secondaryPos;
      this.containerKind = containerKind;
      this.snapshot = snapshot;
   }

   public static final class Snapshot {
      public int totalSlots;
      public int emptySlots;
      public List<ItemCount> items;
      public long gameTime;

      public Snapshot() {
      }

      public Snapshot(int totalSlots, int emptySlots, List<ItemCount> items, long gameTime) {
         this.totalSlots = totalSlots;
         this.emptySlots = emptySlots;
         this.items = items;
         this.gameTime = gameTime;
      }
   }
}
