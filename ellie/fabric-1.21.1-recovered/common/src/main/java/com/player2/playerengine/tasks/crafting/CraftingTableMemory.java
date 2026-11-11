package com.player2.playerengine.tasks.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public final class CraftingTableMemory {
   private static final double MAX_TRAVEL_RADIUS = 16.0;
   private static final int MAX_VERTICAL = 5;
   private static final Map<Object, Set<String>> recorded = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Object, String> lastChoice = Collections.synchronizedMap(new WeakHashMap<>());

   private CraftingTableMemory() {
   }

   public static boolean reasonable(int[] var0, int[] var1, double var2) {
      if (var0 != null && var1 != null && var0.length >= 3 && var1.length >= 3) {
         int var4 = Math.abs(var0[1] - var1[1]);
         double var5 = Math.max(3.5, Math.min(16.0, var2));
         double var7 = var0[0] - var1[0];
         double var9 = var0[2] - var1[2];
         return var4 <= 5 && var7 * var7 + var9 * var9 <= var5 * var5;
      } else {
         return false;
      }
   }

   private static double d2(int[] var0, int[] var1) {
      if (var0 != null && var1 != null) {
         double var2 = var0[0] - var1[0];
         double var4 = var0[1] - var1[1];
         double var6 = var0[2] - var1[2];
         return var2 * var2 + var4 * var4 + var6 * var6;
      } else {
         return Double.MAX_VALUE;
      }
   }

   private static Object invoke(Object var0, String var1, Object... var2) {
      if (var0 == null) {
         return null;
      }

      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
            try {
               return var6.invoke(var0, var2);
            } catch (IllegalArgumentException var8) {
            } catch (Throwable var9) {
               return null;
            }
         }
      }

      return null;
   }

   private static Class<?> load(String var0) {
      try {
         return Class.forName(var0);
      } catch (Throwable var2) {
         return null;
      }
   }

   private static Object getField(Object var0, String var1) {
      if (var0 == null) {
         return null;
      }

      Class var2 = var0.getClass();

      while (var2 != null) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3.get(var0);
         } catch (NoSuchFieldException var4) {
            var2 = var2.getSuperclass();
         } catch (Throwable var5) {
            return null;
         }
      }

      return null;
   }

   private static void put(Object var0, String var1, Object var2) {
      try {
         Field var3 = var0.getClass().getField(var1);
         var3.set(var0, var2);
      } catch (Throwable var4) {
      }
   }

   private static int[] xyz(Object var0) {
      if (var0 == null) {
         return null;
      }

      Object var1 = invoke(var0, "method_10263");
      Object var2 = invoke(var0, "method_10264");
      Object var3 = invoke(var0, "method_10260");
      return var1 instanceof Number && var2 instanceof Number && var3 instanceof Number
         ? new int[]{((Number)var1).intValue(), ((Number)var2).intValue(), ((Number)var3).intValue()}
         : null;
   }

   private static Object pos(int[] var0) {
      if (var0 == null) {
         return null;
      }

      try {
         return load("net.minecraft.class_2338").getConstructor(int.class, int.class, int.class).newInstance(var0[0], var0[1], var0[2]);
      } catch (Throwable var2) {
         return null;
      }
   }

   private static Object world(Object var0) {
      return invoke(var0, "getWorld");
   }

   private static int[] feet(Object var0) {
      Object var1 = invoke(var0, "getPlayer");
      return xyz(invoke(var1, "method_24515"));
   }

   private static String dimension(Object var0) {
      Object var1 = invoke(var0, "method_27983");
      Object var2 = invoke(var1, "method_29177");
      return var2 == null ? null : var2.toString();
   }

   private static boolean isTable(Object var0, Object var1) {
      Object var2 = world(var0);
      if (var2 != null && var1 != null) {
         Object var3 = invoke(var2, "method_8477", var1);
         if (!Boolean.TRUE.equals(var3)) {
            return false;
         }

         try {
            Object var4 = invoke(var2, "method_8320", var1);
            Class var5 = load("net.minecraft.class_2246");
            Object var6 = var5.getField("field_9980").get(null);
            Object var7 = invoke(var4, "method_27852", var6);
            return Boolean.TRUE.equals(var7);
         } catch (Throwable var8) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void event(String var0, String var1) {
      try {
         Class var2 = load("com.player2.playerengine.observatory.ObservatoryTelemetry");
         var2.getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
      }
   }

   private static Object gps() {
      Class var0 = load("com.player2.playerengine.agentic.elliegps.EllieGPSStore");
      if (var0 == null) {
         return null;
      }

      try {
         return var0.getMethod("get").invoke(null);
      } catch (Throwable var2) {
         return null;
      }
   }

   private static void reportChoice(Object var0, String var1, int[] var2) {
      if (var2 != null) {
         String var3 = var1 + " @" + Arrays.toString(var2);
         synchronized (lastChoice) {
            if (var3.equals(lastChoice.get(var0))) {
               return;
            }

            lastChoice.put(var0, var3);
         }

         event("CRAFT_TABLE_CHOICE", var3);
      }
   }

   public static boolean observeReachable(Object var0, Object var1, boolean var2) {
      if (var2 && isTable(var0, var1)) {
         Object var3 = world(var0);
         String var4 = dimension(var3);
         int[] var5 = xyz(var1);
         if (var4 != null && var5 != null) {
            String var6 = var4 + ":" + Arrays.toString(var5);
            synchronized (recorded) {
               Set var7 = recorded.computeIfAbsent(var3, var0x -> new HashSet<>());
               if (!var7.add(var6)) {
                  return var2;
               }
            }

            try {
               Object var15 = gps();
               if (var15 == null) {
                  return var2;
               }

               Object var9 = invoke(var15, "byPosition", var4, var1);
               if (var9 != null) {
                  return var2;
               }

               Class var10 = load("com.player2.playerengine.agentic.elliegps.WaypointRecord");
               Object var11 = var10.getConstructor().newInstance();
               put(var11, "schemaVersion", 1);
               put(var11, "type", "crafting_table");
               put(var11, "dimension", var4);
               put(var11, "pos", var5);
               put(var11, "id", var10.getMethod("idFor", String.class, load("net.minecraft.class_2338")).invoke(null, var4, var1));
               put(var11, "description", "Verified working crafting table");
               put(var11, "keywords", List.of("crafting", "table", "workbench"));
               put(var11, "origin", "heuristic_pass");
               put(var11, "stale", false);
               Object var12 = invoke(var15, "upsert", var11);
               event(
                  "CRAFT_TABLE_REMEMBER",
                  "table=" + Arrays.toString(var5) + " dim=" + var4 + " status=" + (var12 == null ? "unavailable" : String.valueOf(invoke(var12, "status")))
               );
            } catch (Throwable var13) {
               event("CRAFT_TABLE_REMEMBER", "failed=" + var13.getClass().getSimpleName());
            }

            return var2;
         } else {
            return var2;
         }
      } else {
         return var2;
      }
   }

   private static Object remembered(Object var0, double var1) {
      Object var3 = gps();
      Object var4 = world(var0);
      String var5 = dimension(var4);
      int[] var6 = feet(var0);
      if (var3 != null && var4 != null && var5 != null && var6 != null) {
         Object var7 = invoke(var3, "publishedRecords");
         if (!(var7 instanceof List)) {
            return null;
         }

         Object var8 = null;
         double var9 = Double.MAX_VALUE;

         for (Object var12 : (List)var7) {
            if ("crafting_table".equals(getField(var12, "type")) && !Boolean.TRUE.equals(getField(var12, "stale")) && var5.equals(getField(var12, "dimension"))
               )
             {
               int[] var13 = (int[])getField(var12, "pos");
               if (reasonable(var6, var13, var1)) {
                  double var14 = d2(var6, var13);
                  if (!(var14 >= var9)) {
                     Object var16 = pos(var13);
                     if (isTable(var0, var16)) {
                        var8 = var16;
                        var9 = var14;
                     }
                  }
               }
            }
         }

         return var8;
      } else {
         return null;
      }
   }

   public static Optional filterReusable(Object var0, double var1, Optional var3) {
      int[] var4 = feet(var0);
      Object var5 = var3 == null ? null : var3.orElse(null);
      if (!reasonable(var4, xyz(var5), var1) || !isTable(var0, var5)) {
         var5 = null;
      }

      Object var6 = remembered(var0, var1);
      Object var7 = var6 == null || var5 != null && !(d2(var4, xyz(var6)) < d2(var4, xyz(var5))) ? var5 : var6;
      if (var7 != null) {
         reportChoice(var0, var7 == var6 ? "gps" : "local_scan", xyz(var7));
      } else {
         reportChoice(var0, "place_or_craft_local", var4);
      }

      return Optional.ofNullable(var7);
   }

   public static Optional filterReachable(Object var0, Optional var1) {
      if (var1 != null && !var1.isEmpty()) {
         Object var2 = var1.get();
         Object var3 = invoke(var2, "pos");
         return reasonable(feet(var0), xyz(var3), 16.0) && isTable(var0, var3) ? var1 : Optional.empty();
      } else {
         return Optional.empty();
      }
   }
}
