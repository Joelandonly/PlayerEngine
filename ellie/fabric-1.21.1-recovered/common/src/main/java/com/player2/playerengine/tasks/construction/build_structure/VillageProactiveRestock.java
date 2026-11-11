package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

public final class VillageProactiveRestock {
   private static final int LARGE_PROJECT_RESERVE = 64;
   private static final int CHECK_EVERY_TICKS = 5;

   private VillageProactiveRestock() {
   }

   public static int refillThreshold(int var0, int var1, int var2) {
      return var2 > 1024 && var1 > 256 ? 64 : var0;
   }

   public static boolean needsRefill(int var0, int var1, int var2) {
      return var0 >= 0 && var1 > var0 && var0 < var2;
   }

   public static boolean beforeBuilderTick(Object var0, Object var1) {
      try {
         if (!getBoolean(var0, "startedBuilder")) {
            return false;
         }

         int var2 = getInt(var0, "ticks");
         if (var2 % 5 != 0) {
            return false;
         }

         Object var3 = get(var0, "schematic");
         Object var4 = get(var0, "builder");
         if (var3 != null && var4 != null && var1 != null) {
            Object var5 = invoke(var4, "isActive");
            if (var5 instanceof Boolean && (Boolean)var5) {
               int var6 = getInt(var0, "activeLayer");
               if (invoke(var3, "requiredInventoryScoped", var1, var6) instanceof Map<?, ?> var8 && !var8.isEmpty()) {
                  Object var9 = invoke(var1, "getItemStorage");
                  if (var9 == null) {
                     return false;
                  }

                  int var10 = ((Map)get(var3, "blocks")).size();

                  for (Entry<?, ?> var12 : var8.entrySet()) {
                     Object var13 = var12.getKey();
                     if (var13 != null && var12.getValue() instanceof Number) {
                        int var14 = ((Number)var12.getValue()).intValue();
                        if (var14 > 0) {
                           int var15 = inventoryCount(var9, var13);
                           int var16 = Math.min(64, Math.max(1, var14));
                           if (needsRefill(var15, var14, var16)) {
                              invoke(var4, "onLostControl");
                              put(var0, "startedBuilder", false);
                              put(var0, "builderInactiveTicks", 0);
                              put(var0, "ticksSinceProgress", 0);
                              String var17 = "proactive restock item="
                                 + var13
                                 + " held="
                                 + var15
                                 + " neededInLayer="
                                 + var14
                                 + " reserve="
                                 + var16
                                 + " layer="
                                 + var6
                                 + " planCells="
                                 + var10;
                              System.out.println("[EllieMaterial] " + var17);
                              observatoryEvent(var0.getClass().getClassLoader(), "MATERIAL_REFILL", var17);
                              return true;
                           }
                        }
                     }
                  }

                  return false;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (ReflectiveOperationException | RuntimeException var18) {
         if (getSafeTick(var0) % 100 == 0) {
            System.err.println("[EllieMaterial] Restock probe skipped: " + var18);
         }

         return false;
      }
   }

   private static int getSafeTick(Object var0) {
      try {
         return getInt(var0, "ticks");
      } catch (ReflectiveOperationException var2) {
         return -1;
      }
   }

   private static int inventoryCount(Object var0, Object var1) throws ReflectiveOperationException {
      Object var2 = Array.newInstance(var1.getClass(), 1);
      Array.set(var2, 0, var1);
      Object var3 = invoke(var0, "getItemCountInventoryOnly", var2);
      if (!(var3 instanceof Number)) {
         throw new ReflectiveOperationException("Invalid inventory count");
      } else {
         return ((Number)var3).intValue();
      }
   }

   private static Field field(Class<?> var0, String var1) throws ReflectiveOperationException {
      for (Class var2 = var0; var2 != null; var2 = var2.getSuperclass()) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3;
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(var0 + "." + var1);
   }

   private static Object get(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0.getClass(), var1).get(var0);
   }

   private static int getInt(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0.getClass(), var1).getInt(var0);
   }

   private static boolean getBoolean(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0.getClass(), var1).getBoolean(var0);
   }

   private static void put(Object var0, String var1, Object var2) throws ReflectiveOperationException {
      field(var0.getClass(), var1).set(var0, var2);
   }

   private static Object invoke(Object var0, String var1, Object... var2) throws ReflectiveOperationException {
      Method var3 = null;

      for (Class var4 = var0.getClass(); var4 != null && var3 == null; var4 = var4.getSuperclass()) {
         for (Method var8 : var4.getDeclaredMethods()) {
            if (var8.getName().equals(var1) && var8.getParameterCount() == var2.length) {
               Class[] var9 = var8.getParameterTypes();
               boolean var10 = true;

               for (int var11 = 0; var11 < var2.length; var11++) {
                  if (var2[var11] != null
                     && (var9[var11].isPrimitive() || !var9[var11].isInstance(var2[var11]))
                     && (var9[var11] != int.class || !(var2[var11] instanceof Integer))) {
                     var10 = false;
                     break;
                  }
               }

               if (var10) {
                  var3 = var8;
                  break;
               }
            }
         }
      }

      if (var3 == null) {
         throw new NoSuchMethodException(var0.getClass() + "." + var1);
      }

      var3.setAccessible(true);

      try {
         return var3.invoke(var0, var2);
      } catch (InvocationTargetException var12) {
         Throwable var13 = var12.getCause();
         throw new ReflectiveOperationException("Invoked " + var1 + " failed: " + var13, var13);
      }
   }

   private static void observatoryEvent(ClassLoader var0, String var1, String var2) {
      try {
         Class var3 = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry", false, var0);
         Method var4 = var3.getMethod("event", String.class, String.class);
         var4.invoke(null, var1, var2);
      } catch (ReflectiveOperationException | RuntimeException var5) {
      }
   }
}
