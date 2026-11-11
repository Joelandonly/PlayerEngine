package com.player2.playerengine.containeraccess;

import java.util.Locale;

public enum ScanMode {
   LIGHT,
   DEEP,
   TARGETED;

   public String token() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static ScanMode fromToken(String token) {
      if (token == null) {
         return null;
      }

      switch (token.trim().toLowerCase(Locale.ROOT)) {
         case "light":
            return LIGHT;
         case "deep":
            return DEEP;
         case "targeted":
            return TARGETED;
         default:
            return null;
      }
   }
}
