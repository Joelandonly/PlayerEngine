package com.player2.playerengine.containeraccess;

import java.util.Locale;

public enum StorageAccessCode {
   OK,
   INVALID_ARGUMENT,
   INVALID_ITEM_NAME,
   INVALID_COUNT,
   INVALID_SLOT,
   SLOT_EMPTY,
   SLOT_OCCUPIED,
   INSUFFICIENT_ITEMS,
   INSUFFICIENT_SPACE,
   ITEM_NOT_ALLOWED,
   CONTAINER_MISSING,
   CONTAINER_TOO_FAR,
   CONTAINER_UNREACHABLE,
   CONTAINER_UNSUPPORTED,
   CONTAINER_CHANGED;

   public String token() {
      return this.name().toLowerCase(Locale.ROOT);
   }
}
