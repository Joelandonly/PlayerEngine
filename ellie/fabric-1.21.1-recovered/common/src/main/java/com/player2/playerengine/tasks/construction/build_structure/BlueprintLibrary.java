package com.player2.playerengine.tasks.construction.build_structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlueprintLibrary {
   private static final Pattern DIMS = Pattern.compile("(?<![0-9])([0-9]{1,2})\\s*(?:x|×|by)\\s*([0-9]{1,2})(?![0-9])");
   private static final List<String> UNSUPPORTED = List.of(
      "mansion",
      "castle",
      "two story",
      "two-story",
      "two floor",
      "tower",
      "multi story",
      "multi-story",
      "custom",
      "basement",
      "underground bunker",
      "village",
      "boat",
      "ship",
      "redstone",
      "automatic"
   );

   private BlueprintLibrary() {
   }

   public static String choose(String var0, String var1, int var2, int var3, int var4) {
      String var5 = var0 == null ? "" : var0.toLowerCase(Locale.ROOT);
      String var6 = var1 == null ? "" : var1.toLowerCase(Locale.ROOT);
      String var7 = var5 + " " + var6;
      if (!var7.contains("village")
         && !var7.contains("settlement")
         && !var7.contains("megavillage")
         && !var7.contains("mega village")
         && !var7.contains("fortified town")
         && !var7.contains("dirt town")) {
         for (String var15 : UNSUPPORTED) {
            if (var7.contains(var15)) {
               return null;
            }
         }

         String var14;
         if (var7.contains("greenhouse")) {
            var14 = "greenhouse";
         } else if (var7.contains("workshop") || var7.contains("smithy")) {
            var14 = "workshop";
         } else if (!var7.contains("storage shed")
            && !var7.contains("storehouse")
            && !var7.contains("warehouse")
            && !var7.contains("storage house")
            && !var7.contains("shed")) {
            if (!var7.contains("cottage") || !var7.contains("wooden") && !var7.contains("oak")) {
               if (!var7.contains("cottage") && (!var7.contains("stone") || !var7.contains("house") && !var7.contains("shelter"))) {
                  if (var7.contains("lodge") || var7.contains("longhouse")) {
                     var14 = "lodge";
                  } else if (var7.contains("cabin")) {
                     var14 = "cabin";
                  } else {
                     if (!var7.contains("house") && !var7.contains("shelter") && !var7.contains("hut")
                        || !var7.contains("small")
                           && !var7.contains("compact")
                           && !var7.contains("simple")
                           && !var7.contains("starter")
                           && !var7.contains("wood")
                           && !var7.contains("oak")
                           && !DIMS.matcher(var7).find()) {
                        return null;
                     }

                     var14 = "starter";
                  }
               } else {
                  var14 = "stone";
               }
            } else {
               var14 = "cabin";
            }
         } else {
            var14 = "storage";
         }

         for (String var10 : List.of(
            "spruce", "birch", "acacia", "jungle", "mangrove", "cherry", "dark oak", "netherite", "quartz", "deepslate", "brick", "sandstone"
         )) {
            if (var7.contains(var10)) {
               return null;
            }
         }

         if (var7.contains("flat roof") && (var14.equals("cabin") || var14.equals("lodge"))) {
            return null;
         }

         if (var7.contains("stone") && !var14.equals("stone") && !var14.equals("storage") && !var14.equals("workshop")) {
            return null;
         }

         if (var14.equals("starter") && (var7.contains("glass") || var7.contains("window"))) {
            var14 = "cabin";
         }

         int var17;
         int var18;
         switch (var14) {
            case "cabin":
               var17 = 7;
               var18 = 7;
               break;
            case "lodge":
               var17 = 9;
               var18 = 9;
               break;
            case "stone":
               var17 = 7;
               var18 = 7;
               break;
            case "workshop":
               var17 = 9;
               var18 = 7;
               break;
            case "storage":
               var17 = 5;
               var18 = 7;
               break;
            case "greenhouse":
               var17 = 7;
               var18 = 9;
               break;
            default:
               var17 = 5;
               var18 = 5;
         }

         Matcher var19 = DIMS.matcher(var5);
         if (!var19.find()) {
            var19 = DIMS.matcher(var6);
         }

         if (var19.find(0)) {
            var17 = Integer.parseInt(var19.group(1));
            var18 = Integer.parseInt(var19.group(2));
            if (var17 < 5 || var18 < 5 || var17 > 13 || var18 > 13) {
               return null;
            }
         }

         if (var17 % 2 != 0 && var18 % 2 != 0) {
            if (var14.equals("starter") && (var17 >= 9 || var18 >= 9)) {
               var14 = "lodge";
            }

            if (var14.equals("starter") && (var17 >= 7 || var18 >= 7)) {
               var14 = "cabin";
            }

            if (var14.equals("lodge") && (var17 < 7 || var18 < 7)) {
               var14 = "cabin";
            }

            BlueprintLibrary.Plan var20 = make(var14, var17, var18, var2, var3, var4);
            System.out
               .printf(
                  "[EllieBlueprint] LOCAL_SELECT kind=%s footprint=%dx%d anchor=(%d,%d,%d) cells=%d materials=%s%n",
                  var14,
                  var17,
                  var18,
                  var2,
                  var3,
                  var4,
                  var20.blocks.size(),
                  var20.materialCounts()
               );
            return var20.toDsl();
         } else {
            return null;
         }
      } else if (!var7.contains("nether") && !var7.contains("underwater") && !var7.contains("floating")) {
         Matcher var8 = DIMS.matcher(var5);
         if (!var8.find()) {
            var8 = DIMS.matcher(var6);
         }

         if (!var8.find(0) || Integer.parseInt(var8.group(1)) == 47 && Integer.parseInt(var8.group(2)) == 47) {
            BlueprintLibrary.Plan var9 = makeVillage(var2, var3, var4);
            System.out
               .printf(
                  "[EllieBlueprint] LOCAL_SELECT kind=fortified_village footprint=47x47 anchor=(%d,%d,%d) cells=%d materials=%s%n",
                  var2,
                  var3,
                  var4,
                  var9.blocks.size(),
                  var9.materialCounts()
               );
            return var9.toDsl();
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String buildAt(int var0, int var1, int var2) {
      return make("starter", 5, 5, var0, var1, var2).toDsl();
   }

   private static BlueprintLibrary.Plan make(String var0, int var1, int var2, int var3, int var4, int var5) {
      BlueprintLibrary.Plan var6 = new BlueprintLibrary.Plan(var3, var4, var5);
      boolean var7 = var0.equals("stone");
      boolean var8 = var0.equals("greenhouse");
      boolean var9 = var0.equals("cabin");
      boolean var10 = var0.equals("lodge");
      boolean var11 = var0.equals("workshop");
      boolean var12 = var0.equals("storage");
      int var13 = !var10 && !var11 ? 3 : 4;
      String var14 = var7 ? "cobblestone" : "oak_planks";
      String var15 = var7 ? "cobblestone" : "oak_planks";
      String var16 = var8 ? "glass" : "oak_planks";

      for (int var17 = 0; var17 <= var13; var17++) {
         for (int var18 = 0; var18 < var2; var18++) {
            for (int var19 = 0; var19 < var1; var19++) {
               if (var17 == 0) {
                  var6.set(var19, var17, var18, var15);
               } else if (var19 != 0 && var18 != 0 && var19 != var1 - 1 && var18 != var2 - 1) {
                  var6.set(var19, var17, var18, "air");
               } else {
                  String var20 = var14;
                  if ((var9 || var10 || var11 || var12 || var8) && (var19 == 0 || var19 == var1 - 1) && (var18 == 0 || var18 == var2 - 1)) {
                     var20 = "oak_log";
                  }

                  if (var8 && (var19 != 0 && var19 != var1 - 1 || var18 != 0 && var18 != var2 - 1)) {
                     var20 = "glass";
                  }

                  var6.set(var19, var17, var18, var20);
               }
            }
         }
      }

      for (int var22 = 0; var22 < var2; var22++) {
         for (int var25 = 0; var25 < var1; var25++) {
            var6.set(var25, var13 + 1, var22, var16);
         }
      }

      if (var9 || var10) {
         for (int var23 = 0; var23 < var2; var23++) {
            for (int var26 = 1; var26 < var1 - 1; var26++) {
               var6.set(var26, var13 + 2, var23, "oak_planks");
               if (var10 && var26 >= 2 && var26 <= var1 - 3) {
                  var6.set(var26, var13 + 3, var23, "oak_planks");
               }
            }
         }
      }

      int var24 = var1 / 2;
      var6.set(var24, 1, 0, "oak_door");
      var6.set(var24, 2, 0, "oak_door");
      if (var7 || var9 || var10 || var11) {
         for (int var21 : new int[]{1, var1 - 2}) {
            if (var21 != var24) {
               var6.set(var21, 2, var2 - 1, "glass");
            }
         }

         if (var1 >= 7 && var2 >= 7) {
            var6.set(0, 2, var2 / 2, "glass");
            var6.set(var1 - 1, 2, var2 / 2, "glass");
         }
      }

      if (var11) {
         var6.set(2, 1, var2 - 2, "crafting_table");
         var6.set(3, 1, var2 - 2, "furnace");
         var6.set(4, 1, var2 - 2, "chest");
         var6.set(5, 1, var2 - 2, "chest");
      } else if (var12) {
         var6.set(1, 1, var2 - 2, "chest");
         var6.set(var1 - 2, 1, var2 - 2, "chest");
      }

      if (var8) {
         for (int var28 = 0; var28 < var2; var28++) {
            var6.set(var1 / 2, var13 + 1, var28, "oak_planks");
         }
      }

      return var6;
   }

   private static BlueprintLibrary.Plan makeVillage(int var0, int var1, int var2) {
      BlueprintLibrary.Plan var3 = new BlueprintLibrary.Plan(var0, var1, var2);

      for (int var4 = 1; var4 <= 45; var4++) {
         for (int var5 = 22; var5 <= 24; var5++) {
            var3.set(var5, 0, var4, "cobblestone");
            var3.set(var4, 0, var5, "cobblestone");
         }
      }

      for (int var7 = 17; var7 <= 29; var7++) {
         for (int var26 = 17; var26 <= 29; var26++) {
            if (var7 <= 19 || var7 >= 27 || var26 <= 19 || var26 >= 27) {
               var3.set(var7, 0, var26, "cobblestone");
            }
         }
      }

      for (int var8 = 20; var8 <= 26; var8++) {
         var3.set(20, 0, var8, "cobblestone");
         var3.set(26, 0, var8, "cobblestone");
         var3.set(var8, 0, 20, "cobblestone");
         var3.set(var8, 0, 26, "cobblestone");
      }

      for (int var9 = 17; var9 <= 24; var9++) {
         var3.set(var9, 0, 11, "cobblestone");
      }

      for (int var10 = 24; var10 <= 29; var10++) {
         var3.set(var10, 0, 9, "cobblestone");
      }

      for (int var11 = 24; var11 <= 34; var11++) {
         var3.set(var11, 0, 14, "cobblestone");
      }

      for (int var12 = 14; var12 <= 18; var12++) {
         var3.set(34, 0, var12, "cobblestone");
      }

      for (int var13 = 24; var13 <= 27; var13++) {
         var3.set(var13, 0, 18, "cobblestone");
      }

      for (int var14 = 13; var14 <= 24; var14++) {
         var3.set(var14, 0, 33, "cobblestone");
      }

      for (int var15 = 21; var15 <= 24; var15++) {
         var3.set(var15, 0, 38, "cobblestone");
      }

      for (int var16 = 24; var16 <= 30; var16++) {
         var3.set(var16, 0, 35, "cobblestone");
      }

      villageBuilding(var3, 5, 5, 13, 13, 5, "east", "cobblestone", true);
      villageBuilding(var3, 29, 5, 9, 9, 3, "west", "dirt", false);
      villageBuilding(var3, 35, 15, 7, 7, 3, "west", "dirt", false);
      villageBuilding(var3, 5, 29, 9, 9, 3, "east", "dirt", false);
      villageBuilding(var3, 15, 35, 7, 7, 3, "east", "dirt", false);
      villageBuilding(var3, 30, 29, 13, 13, 4, "west", "cobblestone", true);
      villageBuilding(var3, 15, 27, 5, 5, 3, "east", "dirt", false);
      villageBuilding(var3, 27, 16, 5, 5, 3, "west", "dirt", false);

      for (int var17 = 19; var17 <= 24; var17++) {
         var3.set(var17, 0, 29, "cobblestone");
      }

      for (int var18 = 22; var18 <= 24; var18++) {
         var3.set(var18, 0, 0, "cobblestone");
         var3.set(var18, 0, 46, "cobblestone");
         var3.set(0, 0, var18, "cobblestone");
         var3.set(46, 0, var18, "cobblestone");
      }

      for (int var19 = 0; var19 < 47; var19++) {
         for (int var27 = 1; var27 <= 3; var27++) {
            boolean var6 = var19 >= 22 && var19 <= 24;
            if (!var6) {
               var3.set(var19, var27, 0, "cobblestone");
               var3.set(var19, var27, 46, "cobblestone");
               var3.set(0, var27, var19, "cobblestone");
               var3.set(46, var27, var19, "cobblestone");
            }
         }
      }

      for (byte var20 = 0; var20 < 47; var20 += 2) {
         if (var20 < 22 || var20 > 24) {
            var3.set(var20, 4, 0, "cobblestone");
            var3.set(var20, 4, 46, "cobblestone");
            var3.set(0, 4, var20, "cobblestone");
            var3.set(46, 4, var20, "cobblestone");
         }
      }

      for (int var21 = 5; var21 <= 22; var21++) {
         var3.set(var21, 0, 3, "cobblestone");
         var3.set(var21, 0, 43, "cobblestone");
      }

      for (int var22 = 24; var22 <= 41; var22++) {
         var3.set(var22, 0, 3, "cobblestone");
         var3.set(var22, 0, 43, "cobblestone");
      }

      villageTower(var3, 1, 1);
      villageTower(var3, 41, 1);
      villageTower(var3, 1, 41);
      villageTower(var3, 41, 41);

      for (int var23 = 21; var23 <= 25; var23++) {
         for (int var28 = 21; var28 <= 25; var28++) {
            var3.set(var23, 0, var28, "cobblestone");
            if (var23 != 21 && var23 != 25 && var28 != 21 && var28 != 25) {
               var3.set(var23, 1, var28, "air");
            } else {
               var3.set(var23, 1, var28, "cobblestone");
               var3.set(var23, 2, var28, "cobblestone");
            }
         }
      }

      for (int var24 = 3; var24 <= 9; var24++) {
         var3.set(23, var24, 23, "cobblestone");
      }

      for (int var25 = 21; var25 <= 25; var25++) {
         for (int var29 = 21; var29 <= 25; var29++) {
            if ((var25 == 21 || var25 == 25) && (var29 == 21 || var29 == 25)) {
               var3.set(var25, 3, var29, "cobblestone");
            }
         }
      }

      return var3;
   }

   private static void villageBuilding(BlueprintLibrary.Plan var0, int var1, int var2, int var3, int var4, int var5, String var6, String var7, boolean var8) {
      for (int var9 = 0; var9 < var4; var9++) {
         for (int var10 = 0; var10 < var3; var10++) {
            var0.set(var1 + var10, 0, var2 + var9, "cobblestone");
         }
      }

      for (int var14 = 1; var14 <= var5; var14++) {
         for (int var18 = 0; var18 < var4; var18++) {
            for (int var11 = 0; var11 < var3; var11++) {
               boolean var12 = var11 == 0 || var18 == 0 || var11 == var3 - 1 || var18 == var4 - 1;
               if (!var12) {
                  if (var14 <= 3) {
                     var0.set(var1 + var11, var14, var2 + var18, "air");
                  }
               } else {
                  String var13 = var7;
                  if ((var11 == 0 || var11 == var3 - 1) && (var18 == 0 || var18 == var4 - 1)) {
                     var13 = "cobblestone";
                  }

                  if (var14 == var5) {
                     var13 = "cobblestone";
                  }

                  var0.set(var1 + var11, var14, var2 + var18, var13);
               }
            }
         }
      }

      for (int var15 = 0; var15 < var4; var15++) {
         for (int var19 = 0; var19 < var3; var19++) {
            var0.set(
               var1 + var19,
               var5 + 1,
               var2 + var15,
               var8 ? "cobblestone" : (var19 != 0 && var15 != 0 && var19 != var3 - 1 && var15 != var4 - 1 ? "dirt" : "cobblestone")
            );
         }
      }

      if (var8) {
         for (int var16 = 1; var16 < var3 - 1; var16++) {
            var0.set(var1 + var16, var5 + 2, var2 + 1, "cobblestone");
            var0.set(var1 + var16, var5 + 2, var2 + var4 - 2, "cobblestone");
         }
      }

      int var17 = var1 + var3 / 2;
      int var20 = var2 + var4 / 2;
      if (var6.equals("east")) {
         var17 = var1 + var3 - 1;
      }

      if (var6.equals("west")) {
         var17 = var1;
      }

      if (var6.equals("north")) {
         var20 = var2;
      }

      if (var6.equals("south")) {
         var20 = var2 + var4 - 1;
      }

      var0.set(var17, 1, var20, "air");
      var0.set(var17, 2, var20, "air");
      if (var3 >= 7 && var4 >= 7) {
         int var21 = var1 + 1;
         int var22 = var1 + var3 - 2;
         var0.set(var21, 2, var2, "air");
         var0.set(var22, 2, var2, "air");
         var0.set(var21, 2, var2 + var4 - 1, "air");
         var0.set(var22, 2, var2 + var4 - 1, "air");
      }
   }

   private static void villageTower(BlueprintLibrary.Plan var0, int var1, int var2) {
      for (int var3 = 0; var3 < 5; var3++) {
         for (int var4 = 0; var4 < 5; var4++) {
            var0.set(var1 + var3, 0, var2 + var4, "cobblestone");

            for (int var5 = 1; var5 <= 7; var5++) {
               boolean var6 = var3 == 0 || var3 == 4 || var4 == 0 || var4 == 4;
               if (var6) {
                  var0.set(var1 + var3, var5, var2 + var4, "cobblestone");
               } else if (var5 <= 6) {
                  var0.set(var1 + var3, var5, var2 + var4, "air");
               }
            }

            var0.set(var1 + var3, 8, var2 + var4, "cobblestone");
            if ((var3 == 0 || var3 == 4 || var4 == 0 || var4 == 4) && (var3 + var4) % 2 == 0) {
               var0.set(var1 + var3, 9, var2 + var4, "cobblestone");
            }
         }
      }

      int var7 = var1 == 1 ? var1 + 4 : var1;
      int var8 = var2 + 2;
      var0.set(var7, 1, var8, "air");
      var0.set(var7, 2, var8, "air");
   }

   private static final class Plan {
      final int x;
      final int y;
      final int z;
      final LinkedHashMap<String, String> blocks = new LinkedHashMap<>();

      Plan(int var1, int var2, int var3) {
         this.x = var1;
         this.y = var2;
         this.z = var3;
      }

      void set(int var1, int var2, int var3, String var4) {
         this.blocks.put(var1 + "," + var2 + "," + var3, var4);
      }

      Map<String, Integer> materialCounts() {
         LinkedHashMap<String, Integer> var1 = new LinkedHashMap<>();

         for (String var3 : this.blocks.values()) {
            if (!var3.equals("air")) {
               var1.merge(var3, 1, Integer::sum);
            }
         }

         return var1;
      }

      String toDsl() {
         StringBuilder var1 = new StringBuilder(this.blocks.size() * 43);

         for (Entry var3 : this.blocks.entrySet()) {
            String[] var4 = ((String)var3.getKey()).split(",");
            var1.append("setBlock(")
               .append(this.x + Integer.parseInt(var4[0]))
               .append(',')
               .append(this.y + Integer.parseInt(var4[1]))
               .append(',')
               .append(this.z + Integer.parseInt(var4[2]))
               .append(",\"")
               .append((String)var3.getValue())
               .append("\");\n");
         }

         return var1.toString();
      }
   }
}
