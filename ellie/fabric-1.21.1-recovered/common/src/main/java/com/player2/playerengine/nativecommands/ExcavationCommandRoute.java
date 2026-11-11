package com.player2.playerengine.nativecommands;

import java.util.Locale;

public final class ExcavationCommandRoute {
   private ExcavationCommandRoute() {
   }

   public static String exact(String var0) {
      if (var0 == null) {
         return null;
      }

      String var1 = var0.strip().replaceFirst("(?i)^ellie\\s*[,!:]?\\s*", "").replaceFirst("^@", "").strip();
      String[] var2 = var1.split("\\s+");
      if (var2.length == 7 && var2[0].toLowerCase(Locale.ROOT).equals("excavate")) {
         StringBuilder var3 = new StringBuilder("excavate");

         for (int var4 = 1; var4 < 7; var4++) {
            if (!var2[var4].matches("-?[0-9]{1,9}")) {
               return null;
            }

            try {
               var3.append(' ').append(Integer.parseInt(var2[var4]));
            } catch (NumberFormatException var6) {
               return null;
            }
         }

         return var3.toString();
      } else {
         return null;
      }
   }
}
