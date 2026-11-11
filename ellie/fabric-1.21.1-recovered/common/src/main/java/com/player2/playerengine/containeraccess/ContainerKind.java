package com.player2.playerengine.containeraccess;

import java.util.Locale;

public enum ContainerKind {
   CHEST,
   DOUBLE_CHEST,
   TRAPPED_CHEST,
   DOUBLE_TRAPPED_CHEST,
   BARREL,
   SHULKER_BOX,
   GENERIC;

   public String token() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean isChest() {
      return this == CHEST || this == DOUBLE_CHEST || this == TRAPPED_CHEST || this == DOUBLE_TRAPPED_CHEST;
   }

   public boolean isDouble() {
      return this == DOUBLE_CHEST || this == DOUBLE_TRAPPED_CHEST;
   }
}
