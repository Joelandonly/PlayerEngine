package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map.Entry;

public final class GenericWoodResolver {
   private static final String[] SPECIES = new String[]{
      "dark_oak", "mangrove", "jungle", "spruce", "birch", "acacia", "cherry", "oak", "bamboo", "crimson", "warped"
   };
   private static final Set<String> OVERWORLD = Set.of("dark_oak", "mangrove", "jungle", "spruce", "birch", "acacia", "cherry", "oak");
   private static final WeakHashMap<Object, String> selections = new WeakHashMap<>();

   private GenericWoodResolver() {
   }

   public static List resolve(List var0, Object var1) {
      if (var0 != null && !var0.isEmpty() && var1 != null) {
         try {
            String var2 = String.valueOf(field(var1, "description")).toLowerCase(Locale.ROOT).replace('-', '_');
            String var3 = requestedSpecies(var2);
            boolean var4 = false;
            boolean var5 = false;
            boolean var6 = false;

            for (Object var8 : var0) {
               String var9 = strip(String.valueOf(field(var8, "blockName")));
               if (isGeneric(var9)) {
                  var4 = true;
               }

               if (var9.startsWith("oak_") && isWoodComponent(var9)) {
                  var5 = true;
               }

               for (String var13 : SPECIES) {
                  if (!var13.equals("oak") && var9.startsWith(var13 + "_") && isWoodComponent(var9)) {
                     var6 = true;
                  }
               }
            }

            if (var4 || var5 && !var6 && !"oak".equals(var3)) {
               String var20 = var3 != null ? var3 : chooseLocalSpecies(var1);
               if (var20 == null) {
                  var20 = "oak";
               }

               if (!var4 && "oak".equals(var20)) {
                  synchronized (selections) {
                     selections.put(var1, var20);
                  }

                  record("WOOD_RESOLVE", "Requested=" + (var3 == null ? "any" : var3) + ", chosen=oak, blueprint unchanged");
                  set("woodSpecies", "oak");
                  return var0;
               } else {
                  ArrayList var21 = new ArrayList(var0.size());
                  int var23 = 0;

                  for (Object var26 : var0) {
                     String var27 = String.valueOf(field(var26, "blockName"));
                     String var28 = strip(var27);
                     String var14 = genericToConcrete(var28, var20);
                     if (var14 == null && !var6 && !"oak".equals(var3) && var28.startsWith("oak_") && isWoodComponent(var28)) {
                        var14 = var20 + var28.substring(3);
                     }

                     if (var14 != null && !var14.equals(var28)) {
                        if (var27.startsWith("minecraft:")) {
                           var14 = "minecraft:" + var14;
                        }

                        Constructor var15 = var26.getClass().getConstructor(int.class, int.class, int.class, String.class);
                        var21.add(var15.newInstance(field(var26, "x"), field(var26, "y"), field(var26, "z"), var14));
                        var23++;
                     } else {
                        var21.add(var26);
                     }
                  }

                  synchronized (selections) {
                     selections.put(var1, var20);
                  }

                  record(
                     "WOOD_RESOLVE",
                     "Requested=" + (var3 == null ? "any" : var3) + ", chosen=" + var20 + ", substitutions=" + var23 + ", static construction palette locked"
                  );
                  set("woodSpecies", var20);
                  return var21;
               }
            } else {
               return var0;
            }
         } catch (Throwable var19) {
            record("WOOD_RESOLVE_ERROR", "Wood palette fallback to blueprint item IDs: " + var19);
            return var0;
         }
      } else {
         return var0;
      }
   }

   public static String describe(Object var0) {
      synchronized (selections) {
         return selections.getOrDefault(var0, "unspecified");
      }
   }

   public static String dependencyStatus(Object var0, Object var1) {
      String var2 = "oak";

      try {
         Object var3 = field(var0, "this$0");
         String var4 = describe(var3);
         if (!"unspecified".equals(var4)) {
            var2 = var4;
         }
      } catch (Throwable var10) {
      }

      int var12 = -1;
      int var13 = -1;
      int var5 = -1;

      try {
         Object var6 = invoke(var1, "getItemStorage");
         Class var7 = Class.forName("com.player2.playerengine.util.helpers.ItemHelper", false, var0.getClass().getClassLoader());

         for (Object var9 : (Collection)var7.getMethod("getWoodItems").invoke(null)) {
            if (var2.equals(String.valueOf(field(var9, "prefix")))) {
               var12 = itemCount(var6, field(var9, "planks"));
               var13 = itemCount(var6, field(var9, "log"));
               var5 = itemCount(var6, field(var9, "door"));
               break;
            }
         }
      } catch (Throwable var11) {
      }

      return var2
         + " door (owned="
         + label(var5)
         + ") -> 6 matching "
         + var2
         + " planks (owned="
         + label(var12)
         + ") -> "
         + var2
         + " logs (owned="
         + label(var13)
         + ") -> 3x3 crafting table";
   }

   private static String label(int var0) {
      return var0 < 0 ? "unknown" : String.valueOf(var0);
   }

   private static boolean isGeneric(String var0) {
      return Set.of("wood", "log", "logs", "plank", "planks", "wooden_planks", "wooden_log", "wooden_door", "wood_door").contains(var0);
   }

   private static boolean isWoodComponent(String var0) {
      return var0.endsWith("_planks")
         || var0.endsWith("_log")
         || var0.endsWith("_wood")
         || var0.endsWith("_door")
         || var0.endsWith("_stairs")
         || var0.endsWith("_slab")
         || var0.endsWith("_fence")
         || var0.endsWith("_fence_gate")
         || var0.endsWith("_trapdoor");
   }

   private static String genericToConcrete(String var0, String var1) {
      return switch (var0) {
         case "wood" -> var1 + "_wood";
         case "log", "logs", "wooden_log" -> var1 + "_log";
         case "plank", "planks", "wooden_planks" -> var1 + "_planks";
         case "wooden_door", "wood_door" -> var1 + "_door";
         default -> null;
      };
   }

   private static String strip(String var0) {
      return var0.startsWith("minecraft:") ? var0.substring(10) : var0;
   }

   private static String requestedSpecies(String var0) {
      String var1 = " " + var0.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ') + " ";

      for (String var5 : SPECIES) {
         String var6 = " " + var5.replace('_', ' ') + " ";
         if (var1.contains(var6)) {
            return var5;
         }
      }

      return null;
   }

   private static String chooseLocalSpecies(Object var0) {
      HashMap<String, Integer> var1 = new HashMap<>();
      HashMap<String, Integer> var2 = new HashMap<>();
      HashMap<String, Integer> var3 = new HashMap<>();

      for (String var5 : OVERWORLD) {
         var1.put(var5, 0);
         var2.put(var5, 0);
         var3.put(var5, 0);
      }

      Object var36;
      try {
         var36 = field(var0, "mod");
      } catch (Throwable var33) {
         record("WOOD_SCAN", "Controller unavailable: " + var33.getClass().getSimpleName());
         return "oak";
      }

      if (var36 == null) {
         record("WOOD_SCAN", "No controller; defaulting oak");
         return "oak";
      }

      try {
         Object var37 = invoke(var36, "getItemStorage");
         Class var6 = Class.forName("com.player2.playerengine.util.helpers.ItemHelper", false, var0.getClass().getClassLoader());
         Object var7 = var6.getMethod("getWoodItems").invoke(null);

         for (Object var9 : (Collection)var7) {
            String var10 = String.valueOf(field(var9, "prefix"));
            if (OVERWORLD.contains(var10)) {
               int var11 = itemCount(var37, field(var9, "planks"));
               int var12 = itemCount(var37, field(var9, "log"));
               int var13 = itemCount(var37, field(var9, "door"));
               var1.merge(var10, var11 * 10 + var12 * 40 + var13 * 24, Integer::sum);
            }
         }
      } catch (Throwable var35) {
         record("WOOD_INVENTORY", "Inventory scan unavailable: " + var35);
      }

      try {
         Object var38 = invoke(var36, "getWorld");
         Object var40 = field(var0, "buildPosition");
         int var41 = ((Number)invoke(var40, "method_10263")).intValue();
         int var42 = ((Number)invoke(var40, "method_10264")).intValue();
         int var43 = ((Number)invoke(var40, "method_10260")).intValue();
         Class var44 = var40.getClass();
         Constructor var45 = var44.getConstructor(int.class, int.class, int.class);
         Method var46 = var38.getClass().getMethod("method_8477", var44);
         Method var47 = var38.getClass().getMethod("method_8320", var44);
         int var14 = 0;
         int var15 = 0;
         int var16 = 0;
         int[] var17 = new int[]{8, 16, 24};

         for (int var21 : var17) {
            int var22 = var21 == 24 ? 2 : 1;

            for (int var23 = -var21; var23 <= var21; var23 += var22) {
               for (int var24 = -var21; var24 <= var21; var24 += var22) {
                  if (var21 == 8 || Math.max(Math.abs(var23), Math.abs(var24)) > (var21 == 16 ? 8 : 16)) {
                     Object var25 = var45.newInstance(var41 + var23, var42, var43 + var24);
                     var14++;
                     if (Boolean.TRUE.equals(var46.invoke(var38, var25))) {
                        var15++;

                        for (int var26 = -5; var26 <= 14; var26++) {
                           Object var27 = var45.newInstance(var41 + var23, var42 + var26, var43 + var24);
                           Object var28 = var47.invoke(var38, var27);
                           String var29 = String.valueOf(invoke(var28, "method_26204"));

                           for (String var31 : OVERWORLD) {
                              if (var29.contains("minecraft:" + var31 + "_log") || var29.contains("minecraft:" + var31 + "_wood")) {
                                 int var32 = Math.max(Math.abs(var23), Math.abs(var24));
                                 var1.merge(var31, Math.max(6, 36 - var32), Integer::sum);
                                 var2.merge(var31, 1, Integer::sum);
                                 var16++;
                                 break;
                              }

                              if (var29.contains("minecraft:" + var31 + "_leaves")) {
                                 var3.merge(var31, 1, Integer::sum);
                                 if ((Integer)var3.get(var31) <= 24) {
                                    var1.merge(var31, 1, Integer::sum);
                                 }
                                 break;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (var16 >= 12) {
               break;
            }
         }

         record(
            "WOOD_SCAN",
            "center=("
               + var41
               + ","
               + var42
               + ","
               + var43
               + ") columns="
               + var14
               + " loaded="
               + var15
               + " trunks="
               + var2
               + " canopy="
               + var3
               + " score="
               + var1
         );
      } catch (Throwable var34) {
         record("WOOD_SCAN", "Local scan error: " + var34);
      }

      String var39 = var1.entrySet().stream().sorted((var0x, var1x) -> {
         int var2x = Integer.compare((Integer)var1x.getValue(), (Integer)var0x.getValue());
         return var2x != 0 ? var2x : ((String)var0x.getKey()).compareTo((String)var1x.getKey());
      }).filter(var0x -> (Integer)var0x.getValue() > 0).map(Entry::getKey).findFirst().orElse(null);
      if (var39 == null) {
         record("WOOD_SCAN", "No local species established from inventory or loaded terrain; oak fallback (unverified)");
         return "oak";
      } else {
         record("WOOD_CHOICE", "Preferred=" + var39 + "; scores=" + var1 + "; no remote-biome preference; explicit user species still wins");
         return var39;
      }
   }

   private static int itemCount(Object var0, Object var1) throws Exception {
      if (var1 == null) {
         return 0;
      }

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
      return var0.getClass().getMethod(var1).invoke(var0);
   }

   private static void record(String var0, String var1) {
      try {
         Class var2 = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry");
         var2.getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
         System.out.println("[EllieWood] " + var0 + ": " + var1);
      }
   }

   private static void set(String var0, String var1) {
      try {
         Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("set", String.class, Object.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
      }
   }
}
