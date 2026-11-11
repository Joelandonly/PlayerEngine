package com.player2.playerengine.schematic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.Map.Entry;

public final class SchematicStorageReconciler {
   private static final int MAX_NO_PROGRESS_RETRIES = 3;
   private static final Map<Object, SchematicStorageReconciler.State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

   private SchematicStorageReconciler() {
   }

   public static void recordStart(Object var0, Object var1, List<?> var2) {
      try {
         String var3 = String.valueOf(field(var1, "kind"));
         if (!"DEPOSIT".equals(var3) && !"WITHDRAW".equals(var3)) {
            return;
         }

         int var4 = ((Number)field(var1, "index")).intValue();
         if (var4 < 0 || var4 >= var2.size()) {
            return;
         }

         SchematicStorageReconciler.Snapshot var5 = snapshot(var2.get(var4));
         synchronized (STATES) {
            SchematicStorageReconciler.State var7 = STATES.computeIfAbsent(var0, var0x -> new SchematicStorageReconciler.State());
            var7.actionIdentity = System.identityHashCode(var1);
            var7.before = var5;
         }

         emit(
            var0,
            "SCHEMATIC_STORAGE_BASELINE",
            "action="
               + var3
               + " depot="
               + var4
               + " item="
               + field(var1, "item")
               + " expectedStockTarget="
               + field(var1, "stockTarget")
               + " expectedInventoryTarget="
               + field(var1, "target")
               + " before="
               + describe(var5)
         );
      } catch (ReflectiveOperationException | RuntimeException var10) {
         emit(var0, "SCHEMATIC_STORAGE_DIAGNOSTIC_ERROR", "baseline=" + exception(var10));
      }
   }

   public static boolean interceptBlock(Object var0, String var1, String var2) {
      if (!"DEPOSIT".equals(var1) && !"WITHDRAW".equals(var1)) {
         return false;
      }

      try {
         Object var3 = field(var0, "action");
         if (var3 != null && var1.equals(String.valueOf(field(var3, "kind")))) {
            Object var4 = field(var0, "environment");
            if (var4 == null) {
               return false;
            }

            String var5 = String.valueOf(field(var3, "item"));
            int var6 = ((Number)field(var3, "index")).intValue();
            int var7 = ((Number)field(var3, "target")).intValue();
            int var8 = ((Number)field(var3, "stockTarget")).intValue();
            Map<String, Integer> var9 = numericMap(invoke(var4, "inventory"));
            List var10 = (List)invoke(var4, "depots");
            if (var6 >= 0 && var6 < var10.size()) {
               SchematicStorageReconciler.Snapshot var11 = snapshot(var10.get(var6));
               int var12 = var9.getOrDefault(var5, 0);
               int var13 = var11.count(var5);
               int var14 = var11.room(var5);
               String var15 = "";
               Object var16 = field(var3, "task");
               if (var16 != null) {
                  for (String var18 : List.of("outcome", "code", "failures", "describeProgress")) {
                     try {
                        Object var19 = invoke(var16, var18);
                        if (var19 != null) {
                           var15 = var15 + " " + var18 + "=" + truncate(String.valueOf(var19), 150);
                        }
                     } catch (ReflectiveOperationException | RuntimeException var25) {
                     }
                  }
               }

               String var28 = var1 + "/" + var5 + "/" + var6;
               int var29;
               SchematicStorageReconciler.Snapshot var30;
               synchronized (STATES) {
                  SchematicStorageReconciler.State var21 = STATES.computeIfAbsent(var0, var0x -> new SchematicStorageReconciler.State());
                  var30 = var21.actionIdentity == System.identityHashCode(var3) ? var21.before : null;
                  if (!var28.equals(var21.failureKey)) {
                     var21.failureKey = var28;
                     var21.noProgressRetries = 0;
                     var21.lastCarried = -1;
                     var21.lastStock = -1;
                     var21.lastRoom = -1;
                  }

                  boolean var22 = var21.lastCarried < 0
                     || var21.lastStock < 0
                     || ("DEPOSIT".equals(var1) ? var12 < var21.lastCarried || var13 > var21.lastStock : var12 > var21.lastCarried || var13 < var21.lastStock);
                  boolean var23 = var21.lastRoom >= 0 && var14 > var21.lastRoom;
                  if (var22 || var23) {
                     var21.noProgressRetries = 0;
                  }

                  var29 = ++var21.noProgressRetries;
                  var21.lastCarried = var12;
                  var21.lastStock = var13;
                  var21.lastRoom = var14;
               }

               String var31 = "action="
                  + var1
                  + " item="
                  + var5
                  + " depot="
                  + var6
                  + " expectedCarried="
                  + var7
                  + " actualCarried="
                  + var12
                  + " expectedStock="
                  + var8
                  + " actualStock="
                  + var13
                  + " actualFreeSlots="
                  + var11.freeSlots
                  + " actualItemRoom="
                  + var14
                  + " start="
                  + (var30 == null ? "unavailable" : describe(var30))
                  + " now="
                  + describe(var11)
                  + " changesSinceActionStart="
                  + (var30 == null ? "unavailable" : delta(var30.stock, var11.stock))
                  + " native="
                  + truncate(var15, 350)
                  + " detail="
                  + truncate(var2, 400);
               if (var29 > 3) {
                  emit(var0, "SCHEMATIC_STORAGE_RECOVERY_EXHAUSTED", "no-progress retries=3 " + var31);
                  return false;
               } else {
                  emit(var0, "SCHEMATIC_STORAGE_RECONCILE", "retry=" + var29 + "/3 " + var31);
                  setField(var0, "action", null);
                  return true;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (ReflectiveOperationException | RuntimeException var27) {
         emit(var0, "SCHEMATIC_STORAGE_DIAGNOSTIC_ERROR", "rescan failed: " + exception(var27));
         return false;
      }
   }

   private static SchematicStorageReconciler.Snapshot snapshot(Object var0) throws ReflectiveOperationException {
      return new SchematicStorageReconciler.Snapshot(
         numericMap(invoke(var0, "stock")), numericMap(invoke(var0, "mergeRoom")), ((Number)invoke(var0, "freeSlots")).intValue()
      );
   }

   private static Map<String, Integer> numericMap(Object var0) {
      TreeMap<String, Integer> var1 = new TreeMap<>();
      if (var0 instanceof Map<?, ?> var2) {
         for (Entry<?, ?> var4 : var2.entrySet()) {
            if (var4.getKey() != null && var4.getValue() instanceof Number var5) {
               var1.put(String.valueOf(var4.getKey()), var5.intValue());
            }
         }
      }

      return var1;
   }

   private static String describe(SchematicStorageReconciler.Snapshot var0) {
      return "{free=" + var0.freeSlots + ",stock=" + truncate(var0.stock.toString(), 950) + "}";
   }

   private static String delta(Map<String, Integer> var0, Map<String, Integer> var1) {
      TreeMap var2 = new TreeMap();

      for (String var4 : var0.keySet()) {
         int var5 = var1.getOrDefault(var4, 0) - var0.getOrDefault(var4, 0);
         if (var5 != 0) {
            var2.put(var4, var5);
         }
      }

      for (String var7 : var1.keySet()) {
         if (!var0.containsKey(var7) && (Integer)var1.get(var7) != 0) {
            var2.put(var7, (Integer)var1.get(var7));
         }
      }

      return truncate(var2.toString(), 800);
   }

   private static String truncate(String var0, int var1) {
      return var0.length() <= var1 ? var0 : var0.substring(0, var1) + "...";
   }

   private static String exception(Throwable var0) {
      return var0.getClass().getSimpleName() + ": " + var0.getMessage();
   }

   private static void emit(Object var0, String var1, String var2) {
      try {
         invoke(var0, "event", var1, var2);
      } catch (ReflectiveOperationException | RuntimeException var4) {
      }
   }

   private static Object field(Object var0, String var1) throws ReflectiveOperationException {
      Field var2 = locateField(var0.getClass(), var1);
      var2.setAccessible(true);
      return var2.get(var0);
   }

   private static void setField(Object var0, String var1, Object var2) throws ReflectiveOperationException {
      Field var3 = locateField(var0.getClass(), var1);
      var3.setAccessible(true);
      var3.set(var0, var2);
   }

   private static Field locateField(Class<?> var0, String var1) throws NoSuchFieldException {
      for (Class var2 = var0; var2 != null; var2 = var2.getSuperclass()) {
         try {
            return var2.getDeclaredField(var1);
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(var0.getName() + "." + var1);
   }

   private static Object invoke(Object var0, String var1, Object... var2) throws ReflectiveOperationException {
      Class[] var3 = new Class[var2.length];

      for (int var4 = 0; var4 < var2.length; var4++) {
         var3[var4] = var2[var4].getClass();
      }

      for (Class var7 = var0.getClass(); var7 != null; var7 = var7.getSuperclass()) {
         try {
            Method var5 = var7.getDeclaredMethod(var1, var3);
            var5.setAccessible(true);
            return var5.invoke(var0, var2);
         } catch (NoSuchMethodException var6) {
         }
      }

      throw new NoSuchMethodException(var0.getClass().getName() + "." + var1);
   }

   private static final class Snapshot {
      final Map<String, Integer> stock;
      final Map<String, Integer> mergeRoom;
      final int freeSlots;

      Snapshot(Map<String, Integer> var1, Map<String, Integer> var2, int var3) {
         this.stock = Map.copyOf(var1);
         this.mergeRoom = Map.copyOf(var2);
         this.freeSlots = var3;
      }

      int count(String var1) {
         return this.stock.getOrDefault(var1, 0);
      }

      int room(String var1) {
         return this.mergeRoom.getOrDefault(var1, 0) + this.freeSlots * 64;
      }
   }

   private static final class State {
      int actionIdentity;
      SchematicStorageReconciler.Snapshot before;
      String failureKey = "";
      int lastCarried = -1;
      int lastStock = -1;
      int lastRoom = -1;
      int noProgressRetries;
   }
}
