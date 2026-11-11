package com.player2.playerengine.patch;

import java.lang.reflect.Method;
import java.util.Locale;

public final class ToolRequestRouting {
   private ToolRequestRouting() {
   }

   public static Object select(Object var0) {
      if (var0 == null) {
         return null;
      }

      try {
         Object[] var1 = (Object[])var0.getClass().getMethod("getMatches").invoke(var0);
         if (var1 != null && var1.length == 1 && var1[0] != null) {
            Class var2 = Class.forName("net.minecraft.class_1792");
            Method var3 = Class.forName("com.player2.playerengine.util.helpers.ItemHelper").getMethod("stripItemName", var2);
            String var4 = ((String)var3.invoke(null, var1[0])).toLowerCase(Locale.ROOT);
            if (!isStandardToolName(var4)) {
               return null;
            }

            Class var5 = Class.forName("com.player2.playerengine.util.ItemTarget");
            return Class.forName("com.player2.playerengine.TaskCatalogue").getMethod("getItemTask", var5).invoke(null, var0);
         } else {
            return null;
         }
      } catch (ReflectiveOperationException | ClassCastException | LinkageError var6) {
         return null;
      }
   }

   public static boolean isStandardToolName(String var0) {
      if (var0 == null) {
         return false;
      }

      String var1 = var0.toLowerCase(Locale.ROOT);
      return (var1.startsWith("stone_") || var1.startsWith("iron_") || var1.startsWith("diamond_"))
         && (var1.endsWith("_pickaxe") || var1.endsWith("_axe") || var1.endsWith("_shovel") || var1.endsWith("_hoe") || var1.endsWith("_sword"));
   }
}
