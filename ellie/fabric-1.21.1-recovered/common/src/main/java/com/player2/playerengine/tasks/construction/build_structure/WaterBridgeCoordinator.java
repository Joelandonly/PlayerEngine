package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class WaterBridgeCoordinator {
   private static final int MIN_WATER = 4;
   private static final int MAX_WATER = 20;
   private static final WeakHashMap<Object, WaterBridgeCoordinator.State> states = new WeakHashMap<>();
   private static final Map<Class<?>, Method[]> worldMethods = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile Constructor<?> blockPositionCtor;

   private WaterBridgeCoordinator() {
   }

   public static Object beforeGatherReturn(Object var0, Object var1, Object var2) {
      if (var0 != null && var2 != null && var1 != null) {
         try {
            WaterBridgeCoordinator.State var51;
            synchronized (states) {
               var51 = states.computeIfAbsent(var1, var0x -> new WaterBridgeCoordinator.State());
            }

            if (var51.cooldown > 0) {
               var51.cooldown--;
               return var0;
            }

            Object var53 = invoke(var2, "getWorld");
            if (var51.positions != null) {
               if (var51.materialTask != null) {
                  int var55 = count(invoke(var2, "getItemStorage"), var51.materialItem);
                  if (var55 < var51.materialTarget) {
                     if (!Boolean.TRUE.equals(invoke(var51.materialTask, "isFinished")) && ++var51.checked <= 1200) {
                        return var51.materialTask;
                     }

                     abort(var51, "Unable to craft bridge planks locally; returning to original task");
                     return var0;
                  }

                  log("BRIDGE_SUPPLY", "Prepared " + var55 + " surface planks");
                  var51.materialTask = null;
                  var51.materialItem = null;
                  var51.checked = 0;
               }

               if (var51.next >= var51.positions.size()) {
                  complete(var51);
                  return var0;
               }

               Object var56 = var51.positions.get(var51.next);
               String var57 = blockId(var53, var56);
               String var58 = String.valueOf(var51.block);
               if (!var57.equals("minecraft:water")) {
                  if (!var57.equals(itemBlockName(var58))) {
                     abort(var51, "Crossing changed; will not overwrite " + var57);
                     return var0;
                  }

                  var51.next++;
                  var51.task = null;
                  var51.checked = 0;
                  if (var51.next >= var51.positions.size()) {
                     complete(var51);
                     return var0;
                  }

                  var56 = var51.positions.get(var51.next);
               }

               if (var51.task == null) {
                  var51.task = makePlaceTask(var56, var51.block, var1.getClass().getClassLoader());
                  log("WATER_BRIDGE", "Placing surface-level step " + (var51.next + 1) + "/" + var51.positions.size() + " at " + var56);
               }

               clearCounter(var1, "prepStallTicks");
               clearCounter(var1, "gatheringStallTicks");
               if (++var51.checked <= 400 && (!Boolean.TRUE.equals(invoke(var51.task, "isFinished")) || !blockId(var53, var56).equals("minecraft:water"))) {
                  return var51.task;
               }

               abort(var51, "Bridge placement failed/stalled at " + var56);
               return var0;
            } else {
               if (var51.started++ % 40 != 0) {
                  return var0;
               }

               Object var54 = invoke(var2, "getBaritone");
               Object var6 = invoke(var54, "getPathingBehavior");
               if (invoke(var6, "getPath") instanceof Optional var8 && !var8.isEmpty()) {
                  List var9 = (List)invoke(var8.get(), "positions");
                  if (var9.size() < 5) {
                     skip(var51, "Path too short");
                     return var0;
                  }

                  Object var10 = invoke(invoke(var54, "getEntityContext"), "feetPos");
                  int var11 = coord(var10, "method_10263");
                  int var12 = coord(var10, "method_10264");
                  int var13 = coord(var10, "method_10260");
                  if (var51.probeX != Integer.MIN_VALUE && Math.abs(var11 - var51.probeX) < 3 && Math.abs(var13 - var51.probeZ) < 3 && var51.probeWait < 160) {
                     var51.probeWait += 40;
                     return var0;
                  }

                  var51.probeX = var11;
                  var51.probeZ = var13;
                  var51.probeWait = 0;
                  boolean var14 = false;

                  label330:
                  for (int var15 = -5; var15 <= 5; var15++) {
                     for (int var16 = -5; var16 <= 5; var16++) {
                        if (Math.abs(var15) + Math.abs(var16) <= 6) {
                           for (int var17 = -2; var17 <= 0; var17++) {
                              if (blockId(var53, blockPos(var10, var11 + var15, var12 + var17, var13 + var16)).equals("minecraft:water")) {
                                 var14 = true;
                                 break label330;
                              }
                           }
                        }
                     }
                  }

                  if (!var14) {
                     skip(var51, "No near-shore water to bridge");
                     return var0;
                  }

                  Object var59 = var9.get(var9.size() - 1);
                  int var60 = coord(var59, "method_10263");
                  int var61 = coord(var59, "method_10260");
                  int var18 = Math.abs(var60 - var11) + Math.abs(var61 - var13);
                  int var19 = var9.size() - var18;
                  boolean var20 = false;

                  for (Object var22 : var9) {
                     if (Math.abs(coord(var22, "method_10263") - var11) <= 7 && Math.abs(coord(var22, "method_10260") - var13) <= 7) {
                        int var23 = coord(var22, "method_10264") - 1;
                        if (blockId(var53, blockPos(var10, coord(var22, "method_10263"), var23, coord(var22, "method_10260"))).equals("minecraft:water")) {
                           var20 = true;
                           break;
                        }
                     }
                  }

                  if (var19 < 7 && !var20) {
                     skip(var51, "Route not meaningfully detoured; detour=" + var19);
                     return var0;
                  }

                  int[][] var62 = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                  ArrayList var63 = null;
                  int var64 = Integer.MAX_VALUE;
                  String var24 = "No suitable short water crossing within 5 blocks of Ellie";

                  for (int[] var28 : var62) {
                     int var29 = var28[0];
                     int var30 = var28[1];
                     if (var29 * (var60 - var11) + var30 * (var61 - var13) >= 7) {
                        for (int var31 = -3; var31 <= 3; var31++) {
                           for (int var32 = 0; var32 <= 5; var32++) {
                              int var33 = var11 + var29 * var32 + (var30 != 0 ? var31 : 0);
                              int var34 = var13 + var30 * var32 + (var29 != 0 ? var31 : 0);

                              for (int var35 = var12 - 2; var35 <= var12; var35++) {
                                 Object var36 = blockPos(var10, var33, var35, var34);
                                 String var37 = blockId(var53, var36);
                                 if (var37.equals("minecraft:unloaded")) {
                                    var24 = "Unloaded shore area";
                                 } else if (!isAir(var37) && !var37.equals("minecraft:water") && !var37.equals("minecraft:lava") && !var37.contains("leaves")) {
                                    Object var38 = blockPos(var10, var33, var35 + 1, var34);
                                    if (isAir(blockId(var53, var38))) {
                                       ArrayList var39 = new ArrayList();

                                       for (int var40 = 1; var40 <= 21; var40++) {
                                          int var41 = var33 + var29 * var40;
                                          int var42 = var34 + var30 * var40;
                                          Object var43 = blockPos(var10, var41, var35, var42);
                                          String var44 = blockId(var53, var43);
                                          if (!var44.equals("minecraft:water")) {
                                             if (var44.equals("minecraft:unloaded")) {
                                                var24 = "Crossing enters unloaded chunks";
                                             }

                                             if (var39.size() >= 4
                                                && var39.size() <= 20
                                                && !isAir(var44)
                                                && !var44.equals("minecraft:unloaded")
                                                && !var44.equals("minecraft:lava")
                                                && !var44.contains("leaves")) {
                                                int var45 = var32 + Math.abs(var31) + var39.size();
                                                int var46 = Math.abs(var60 - var41) + Math.abs(var61 - var42);
                                                if (var46 + 4 < var18 && var45 < var64) {
                                                   var63 = new ArrayList(var39);
                                                   var64 = var45;
                                                }
                                                break;
                                             }

                                             if (var39.size() > 20) {
                                                var24 = "Water crossing longer than 20";
                                             }
                                             break;
                                          }

                                          if (!isAir(blockId(var53, blockPos(var10, var41, var35 + 1, var42)))) {
                                             var24 = "Water has obstructed headroom";
                                             break;
                                          }

                                          var39.add(var43);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (var63 == null) {
                     skip(var51, var24 + " (pathLen=" + var9.size() + ", direct=" + var18 + ")");
                     return var0;
                  }

                  Object var65 = availablePlanks(var2, var1, var63.size());
                  Object var66 = null;
                  if (var65 == null) {
                     Object[] var67 = planLocalCraft(var2, var1, var63.size());
                     if (var67 != null) {
                        var65 = var67[0];
                        var66 = var67[1];
                     }
                  }

                  if (var65 == null) {
                     skip(var51, "Found " + var63.size() + "-tile crossing, no usable planks or local crafting supply");
                     return var0;
                  } else {
                     Object var68 = itemToBlock(var65, var1.getClass().getClassLoader());
                     if (var68 == null) {
                        skip(var51, "Cannot map plank item to block");
                        return var0;
                     } else {
                        var51.positions = var63;
                        var51.block = var68;
                        var51.next = 0;
                        var51.task = null;
                        var51.checked = 0;
                        var51.materialTask = var66;
                        var51.materialItem = var65;
                        var51.materialTarget = var63.size();
                        log(
                           "WATER_BRIDGE_PLAN",
                           "Route detour="
                              + var19
                              + ", crossing="
                              + var63.size()
                              + " tiles, surface Y="
                              + coord(var63.get(0), "method_10264")
                              + ", supply="
                              + (var66 == null ? "inventory" : "local crafting")
                        );
                        return beforeGatherReturn(var0, var1, var2);
                     }
                  }
               } else {
                  skip(var51, "No active Baritone path");
                  return var0;
               }
            }
         } catch (Throwable var50) {
            Throwable var3 = var50;
            synchronized (states) {
               WaterBridgeCoordinator.State var5 = states.computeIfAbsent(var1, var0x -> new WaterBridgeCoordinator.State());
               skip(var5, "Bridge planner unavailable: " + var3.getClass().getSimpleName());
               abort(var5, "Planner unavailable: " + var3.getClass().getSimpleName());
               return var0;
            }
         }
      } else {
         return var0;
      }
   }

   private static Object blockPos(Object var0, int var1, int var2, int var3) throws Exception {
      Constructor var4 = blockPositionCtor;
      if (var4 == null) {
         Class var5 = Class.forName("net.minecraft.class_2338", false, var0.getClass().getClassLoader());
         var4 = var5.getConstructor(int.class, int.class, int.class);
         blockPositionCtor = var4;
      }

      return var4.newInstance(var1, var2, var3);
   }

   private static Object[] planLocalCraft(Object var0, Object var1, int var2) {
      try {
         Object var3 = invoke(var0, "getItemStorage");
         ClassLoader var4 = var1.getClass().getClassLoader();
         Collection var5 = (Collection)Class.forName("com.player2.playerengine.util.helpers.ItemHelper", false, var4).getMethod("getWoodItems").invoke(null);
         String var6 = GenericWoodResolver.describe(field(var1, "this$0"));

         for (Object var8 : var5) {
            Object var9 = field(var8, "planks");
            Object var10 = field(var8, "log");
            if (var9 != null && var10 != null && var6.equals(String.valueOf(field(var8, "prefix")))) {
               int var11 = count(var3, var9);
               int var12 = count(var3, var10);
               if (var11 + 4 * var12 >= var2) {
                  Class var13 = Class.forName("net.minecraft.class_1792", false, var4);
                  Class var14 = Class.forName("com.player2.playerengine.util.ItemTarget", false, var4);
                  Class var15 = Class.forName("com.player2.playerengine.PlayerEngineController", false, var4);
                  Class var16 = Class.forName("com.player2.playerengine.tasks.crafting.CraftMacroTasks", false, var4);
                  Object var17 = var14.getConstructor(var13, int.class).newInstance(var9, var2);
                  Object var18 = var16.getMethod("tryCreateMacroTask", var15, var14).invoke(null, var0, var17);
                  if (var18 != null) {
                     return new Object[]{var9, var18};
                  }
               }
            }
         }
      } catch (Throwable var19) {
         log("BRIDGE_SKIP", "Local plank crafting unavailable: " + var19.getClass().getSimpleName());
      }

      return null;
   }

   private static void skip(WaterBridgeCoordinator.State var0, String var1) {
      if (!var1.equals(var0.lastSkip) || ++var0.reported >= 15) {
         var0.lastSkip = var1;
         var0.reported = 0;
         log("BRIDGE_SKIP", var1);
      }
   }

   private static boolean isAir(String var0) {
      return var0.equals("minecraft:air") || var0.equals("minecraft:cave_air");
   }

   private static int coord(Object var0, String var1) throws Exception {
      return ((Number)invoke(var0, var1)).intValue();
   }

   private static String itemBlockName(String var0) {
      int var1 = var0.indexOf("minecraft:");
      if (var1 < 0) {
         return var0;
      }

      int var2 = var0.indexOf(125, var1);
      return var2 < 0 ? var0.substring(var1) : var0.substring(var1, var2);
   }

   private static Object makePlaceTask(Object var0, Object var1, ClassLoader var2) throws Exception {
      Class var3 = Class.forName("net.minecraft.class_2248", false, var2);
      Class var4 = Class.forName("net.minecraft.class_2338", false, var2);
      Class var5 = Class.forName("com.player2.playerengine.tasks.construction.PlaceBlockTask", false, var2);
      Object var6 = Array.newInstance(var3, 1);
      Array.set(var6, 0, var1);
      return var5.getConstructor(var4, var6.getClass()).newInstance(var0, var6);
   }

   private static Object availablePlanks(Object var0, Object var1, int var2) throws Exception {
      Object var3 = invoke(var0, "getItemStorage");
      Object var4 = Class.forName("com.player2.playerengine.util.helpers.ItemHelper", false, var1.getClass().getClassLoader())
         .getMethod("getWoodItems")
         .invoke(null);
      String var5 = GenericWoodResolver.describe(field(var1, "this$0"));
      Object var6 = null;
      int var7 = 0;

      for (Object var9 : (Collection)var4) {
         Object var10 = field(var9, "planks");
         if (var10 != null) {
            int var11 = count(var3, var10);
            if (var11 >= var2 && String.valueOf(field(var9, "prefix")).equals(var5)) {
               return var10;
            }

            if (var11 >= var2 && var11 > var7) {
               var6 = var10;
               var7 = var11;
            }
         }
      }

      return var6;
   }

   private static int count(Object var0, Object var1) throws Exception {
      for (Method var5 : var0.getClass().getMethods()) {
         if (var5.getName().equals("getItemCountInventoryOnly") && var5.getParameterCount() == 1) {
            Class var6 = var5.getParameterTypes()[0];
            if (var6.isArray()) {
               Object var7 = Array.newInstance(var6.getComponentType(), 1);
               Array.set(var7, 0, var1);
               return ((Number)var5.invoke(var0, var7)).intValue();
            }
         }
      }

      return 0;
   }

   private static Object itemToBlock(Object var0, ClassLoader var1) throws Exception {
      Class var2 = Class.forName("net.minecraft.class_1792", false, var1);
      Class var3 = Class.forName("com.player2.playerengine.util.helpers.ItemHelper", false, var1);
      Object var4 = Array.newInstance(var2, 1);
      Array.set(var4, 0, var0);
      Object var5 = var3.getMethod("itemsToBlocks", var4.getClass()).invoke(null, var4);
      return Array.getLength(var5) > 0 ? Array.get(var5, 0) : null;
   }

   private static String blockId(Object var0, Object var1) throws Exception {
      Method[] var2 = worldMethods.get(var0.getClass());
      if (var2 == null) {
         Class var3 = Class.forName("net.minecraft.class_2338", false, var0.getClass().getClassLoader());
         var2 = new Method[]{var0.getClass().getMethod("method_8477", var3), var0.getClass().getMethod("method_8320", var3)};

         for (Method var7 : var2) {
            var7.setAccessible(true);
         }

         worldMethods.put(var0.getClass(), var2);
      }

      if (!Boolean.TRUE.equals(var2[0].invoke(var0, var1))) {
         return "minecraft:unloaded";
      }

      Object var8 = var2[1].invoke(var0, var1);
      Object var9 = invoke(var8, "method_26204");
      return itemBlockName(String.valueOf(var9));
   }

   private static void clearCounter(Object var0, String var1) {
      try {
         for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
            try {
               Field var3 = var2.getDeclaredField(var1);
               var3.setAccessible(true);
               var3.setInt(var0, 0);
               return;
            } catch (NoSuchFieldException var4) {
            }
         }
      } catch (Throwable var5) {
      }
   }

   private static Object field(Object var0, String var1) throws Exception {
      for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3.get(var0);
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(var1);
   }

   private static Object invoke(Object var0, String var1) throws Exception {
      Method var2 = var0.getClass().getMethod(var1);
      var2.setAccessible(true);
      return var2.invoke(var0);
   }

   private static void complete(WaterBridgeCoordinator.State var0) {
      log("WATER_BRIDGE_DONE", "Surface crossing finished (" + var0.positions.size() + " steps)");
      var0.positions = null;
      var0.task = null;
      var0.materialTask = null;
      var0.materialItem = null;
      var0.cooldown = 1200;
   }

   private static void abort(WaterBridgeCoordinator.State var0, String var1) {
      if (var0.positions != null) {
         log("WATER_BRIDGE_ABORT", var1);
      }

      var0.positions = null;
      var0.task = null;
      var0.materialTask = null;
      var0.materialItem = null;
      var0.cooldown = 1200;
   }

   private static void log(String var0, String var1) {
      try {
         Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
         System.out.println("[EllieBridge] " + var0 + ": " + var1);
      }
   }

   private static final class State {
      List<Object> positions;
      Object block;
      Object task;
      Object materialTask;
      Object materialItem;
      int next;
      int checked;
      int cooldown;
      int started;
      int materialTarget;
      int reported;
      int probeX = Integer.MIN_VALUE;
      int probeZ = Integer.MIN_VALUE;
      int probeWait;
      String lastSkip = "";
   }
}
