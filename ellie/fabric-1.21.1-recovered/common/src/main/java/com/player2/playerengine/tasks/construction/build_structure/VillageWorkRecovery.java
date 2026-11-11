package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class VillageWorkRecovery {
   private static final int TOOL_CHECK_INTERVAL = 40;
   private static final int MIN_PICKAXE_USES = 24;
   private static final int STALL_TICKS = 440;
   private static final int STALL_MIN_ELEVATION = 2;
   private static final int RECOVERY_COOLDOWN = 900;
   private static final int MAX_REPLANS_PER_LAYER = 2;
   private static final Map<Object, VillageWorkRecovery.State> STATES = new WeakHashMap<>();
   private static long lastWarning;

   private VillageWorkRecovery() {
   }

   public static void onTick(Object var0, Object var1) {
      if (var0 != null && var1 != null) {
         try {
            if (boolField(var0, "finished") || read(var0, "schematic") == null) {
               synchronized (STATES) {
                  STATES.remove(var0);
                  return;
               }
            }

            VillageWorkRecovery.State var2;
            synchronized (STATES) {
               var2 = STATES.computeIfAbsent(var0, var0x -> new VillageWorkRecovery.State());
            }

            int var18 = intField(var0, "ticks");
            int var4 = intField(var0, "activeLayer");
            if (var4 != var2.lastLayer) {
               var2.lastLayer = var4;
               var2.replans = 0;
            }

            if (var18 - var2.lastToolCheck >= 40 && miningStone(var0)) {
               var2.lastToolCheck = var18;
               Object var5 = call(var1, "getItemStorage");
               Object var6 = call(var5, "getItemStacksPlayerInventory", Boolean.TRUE);
               int var7 = bestPickaxeUses(var6);
               if (var7 >= 24) {
                  var2.toolAttempts = 0;
               } else if (var2.toolAttempts < 2) {
                  Object var8 = stonePickaxe();
                  int var9 = countItem(var6, var8);
                  int var10 = var9 + 1;
                  Object var11 = getItemTask(var8, var10);
                  if (var11 != null) {
                     write(var11, "controller", var1);
                     Object var12 = read(var0, "gathering");
                     if (var12 != null) {
                        call(var12, "stop");
                     }

                     write(var0, "gathering", var11);
                     write(var0, "currentResource", var8);
                     writeInt(var0, "resourceStartCount", var9);
                     writeInt(var0, "lastGatheringCount", var9);
                     writeInt(var0, "gatheringStallTicks", 0);
                     writeInt(var0, "gatheringNoProgressRetries", 0);
                     var2.toolAttempts++;
                     log("TOOL_MAINTENANCE", "replacing weak pickaxe (bestRemainingUses=" + var7 + ", stonePickaxes=" + var9 + ", requestTotal=" + var10 + ")");
                  }
               }
            }

            if (!boolField(var0, "startedBuilder") || read(var0, "gathering") != null || read(var0, "prepTask") != null || read(var0, "scaffoldTask") != null) {
               return;
            }

            int var19 = intField(var0, "ticksSinceProgress");
            if (var19 < 440 || var2.replans >= 2 || var18 - var2.lastReplanTick < 900) {
               return;
            }

            Object var20 = call(call(var1, "getPlayer"), "method_24515");
            int var21 = coordinate(var20, "method_10264", "getY");
            Object var22 = read(read(var0, "schematic"), "origin");
            int var23 = coordinate(var22, "method_10264", "getY") + var4;
            if (var21 < var23 + 2) {
               return;
            }

            Object var24 = read(var0, "builder");
            if (var24 == null) {
               return;
            }

            call(var24, "onLostControl");
            writeBool(var0, "startedBuilder", false);
            writeInt(var0, "ticksSinceProgress", 0);
            writeInt(var0, "builderInactiveTicks", 0);
            var2.replans++;
            var2.lastReplanTick = var18;
            log("HEIGHT_REPLAN", "stalled elevated builder replan: noProgressTicks=" + var19 + " feetY=" + var21 + " layerY=" + var23 + " try=" + var2.replans);
         } catch (ReflectiveOperationException | RuntimeException var15) {
            long var3 = System.currentTimeMillis();
            if (var3 - lastWarning > 20000L) {
               lastWarning = var3;
               System.err.println("[EllieRecovery] optional construction recovery skipped: " + var15);
            }
         }
      }
   }

   private static boolean miningStone(Object var0) throws ReflectiveOperationException {
      Object var1 = read(var0, "gathering");
      if (var1 == null) {
         return false;
      }

      Object var2 = read(var0, "currentResource");
      String var3 = String.valueOf(var2).toLowerCase(Locale.ROOT);
      return !var3.contains("pickaxe")
         && !var3.contains("shovel")
         && !var3.contains("axe")
         && (var3.contains("cobblestone") || var3.contains("deepslate") || var3.equals("stone") || var3.endsWith(":stone"));
   }

   private static int bestPickaxeUses(Object var0) throws ReflectiveOperationException {
      if (!(var0 instanceof List)) {
         return Integer.MAX_VALUE;
      }

      int var1 = 0;

      for (Object var3 : (List)var0) {
         if (var3 != null) {
            Object var4 = call(var3, "method_7909");
            if (var4 != null && String.valueOf(var4).toLowerCase(Locale.ROOT).contains("pickaxe")) {
               Object var5 = callStatic("com.player2.playerengine.tasks.farming.FarmBreakToolRequirement", "remainingUses", var3);
               if (var5 instanceof Number) {
                  var1 = Math.max(var1, ((Number)var5).intValue());
               }
            }
         }
      }

      return var1;
   }

   private static Object stonePickaxe() throws ReflectiveOperationException {
      return Class.forName("net.minecraft.class_1802").getField("field_8387").get(null);
   }

   private static int countItem(Object var0, Object var1) throws ReflectiveOperationException {
      int var2 = 0;
      if (var0 instanceof List) {
         for (Object var4 : (List)var0) {
            if (var4 != null && call(var4, "method_7909") == var1) {
               var2++;
            }
         }
      }

      return var2;
   }

   private static Object getItemTask(Object var0, int var1) throws ReflectiveOperationException {
      for (Method var5 : Class.forName("com.player2.playerengine.TaskCatalogue").getMethods()) {
         if (var5.getName().equals("getItemTask")
            && var5.getParameterCount() == 2
            && var5.getParameterTypes()[0].isInstance(var0)
            && var5.getParameterTypes()[1] == int.class) {
            return var5.invoke(null, var0, var1);
         }
      }

      throw new NoSuchMethodException("TaskCatalogue.getItemTask");
   }

   private static Object callStatic(String var0, String var1, Object var2) throws ReflectiveOperationException {
      for (Method var6 : Class.forName(var0).getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == 1 && var6.getParameterTypes()[0].isInstance(var2)) {
            return var6.invoke(null, var2);
         }
      }

      throw new NoSuchMethodException(var0 + "." + var1);
   }

   private static Object call(Object var0, String var1, Object... var2) throws ReflectiveOperationException {
      if (var0 == null) {
         return null;
      }

      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
            boolean var7 = true;

            for (int var8 = 0; var8 < var2.length; var8++) {
               Class var9 = var6.getParameterTypes()[var8];
               Object var10 = var2[var8];
               if (var10 != null && !var9.isInstance(var10) && (var9 != boolean.class || !(var10 instanceof Boolean))) {
                  var7 = false;
               }
            }

            if (var7) {
               var6.setAccessible(true);
               return var6.invoke(var0, var2);
            }
         }
      }

      throw new NoSuchMethodException(var0.getClass().getName() + "." + var1);
   }

   private static Field field(Object var0, String var1) throws ReflectiveOperationException {
      for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3;
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(var0.getClass().getName() + "." + var1);
   }

   private static Object read(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0, var1).get(var0);
   }

   private static boolean boolField(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0, var1).getBoolean(var0);
   }

   private static int intField(Object var0, String var1) throws ReflectiveOperationException {
      return field(var0, var1).getInt(var0);
   }

   private static void write(Object var0, String var1, Object var2) throws ReflectiveOperationException {
      field(var0, var1).set(var0, var2);
   }

   private static void writeInt(Object var0, String var1, int var2) throws ReflectiveOperationException {
      field(var0, var1).setInt(var0, var2);
   }

   private static void writeBool(Object var0, String var1, boolean var2) throws ReflectiveOperationException {
      field(var0, var1).setBoolean(var0, var2);
   }

   private static int coordinate(Object var0, String var1, String var2) throws ReflectiveOperationException {
      if (var0 == null) {
         return Integer.MIN_VALUE;
      }

      for (String var6 : new String[]{var1, var2}) {
         try {
            Object var7 = call(var0, var6);
            if (var7 instanceof Number) {
               return ((Number)var7).intValue();
            }
         } catch (NoSuchMethodException var8) {
         }
      }

      throw new NoSuchMethodException("Missing coordinate on " + var0.getClass());
   }

   private static void log(String var0, String var1) {
      System.out.println("[EllieRecovery] " + var0 + ": " + var1);

      try {
         Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (ReflectiveOperationException | RuntimeException var3) {
      }
   }

   private static final class State {
      int lastToolCheck;
      int toolAttempts;
      int lastLayer = Integer.MIN_VALUE;
      int replans;
      int lastReplanTick = -100000;
   }
}
