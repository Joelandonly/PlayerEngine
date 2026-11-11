package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.containeraccess.ItemCount;
import com.player2.playerengine.multiversion.item.ItemVer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class WaypointItemCategorizer {
   private static final String CAT_TOOLS = "tools";
   private static final String CAT_WEAPONS = "weapons";
   private static final String CAT_ARMOR = "armor";
   private static final String CAT_FOOD = "food";
   private static final String CAT_BUILDING = "building blocks";
   private static final String CAT_ORES = "ores";
   private static final String CAT_INGOTS_METALS = "ingots";
   private static final String CAT_METALS = "metals";
   private static final String CAT_REDSTONE = "redstone";
   private static final String CAT_FUEL = "fuel";
   private static final String CAT_VALUABLES = "valuables";
   private static final String CAT_CROPS_SEEDS = "crops";

   private WaypointItemCategorizer() {
   }

   public static List<String> categorize(List<ItemCount> aggregate) {
      LinkedHashSet<String> result = new LinkedHashSet<>();
      LinkedHashSet<String> nameTokens = new LinkedHashSet<>();

      for (ItemCount ic : aggregate) {
         if (ic != null && ic.registryId() != null) {
            String registryId = ic.registryId();
            Item item = resolveItem(registryId);
            addCategoryTokens(registryId, item, result);
            addNameTokens(registryId, nameTokens);
         }
      }

      for (String tok : nameTokens) {
         result.add(tok);
      }

      return List.copyOf(result);
   }

   private static void addCategoryTokens(String registryId, Item item, LinkedHashSet<String> out) {
      String path = pathOf(registryId);
      if (item != null) {
         addClassBasedCategories(registryId, path, item, out);
      } else {
         addPatternBasedCategories(path, out);
      }
   }

   private static void addClassBasedCategories(String registryId, String path, Item item, LinkedHashSet<String> out) {
      if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem || item instanceof DiggerItem) {
         out.add("tools");
      }

      if (item instanceof SwordItem || item instanceof TridentItem) {
         out.add("weapons");
      }

      if (item instanceof ArmorItem) {
         out.add("armor");
      }

      if (ItemVer.isFood(item)) {
         out.add("food");
      }

      if (item instanceof BlockItem) {
         out.add("building blocks");
      }

      addPatternBasedCategories(path, out);
      addValuablesCheck(path, out);
      addRedstoneCheck(path, out);
      addFuelCheck(item, path, out);
      addCropsSeedsCheck(path, out);
   }

   private static void addPatternBasedCategories(String path, LinkedHashSet<String> out) {
      if (path.endsWith("_ore") || path.contains("_ore_") || path.equals("ancient_debris")) {
         out.add("ores");
      }

      if (path.startsWith("raw_")) {
         out.add("ores");
         out.add("ingots");
         out.add("metals");
      }

      if (path.endsWith("_ingot")) {
         out.add("ingots");
         out.add("metals");
      }

      if (path.endsWith("_nugget")) {
         out.add("metals");
      }

      if (path.equals("diamond")
         || path.equals("emerald")
         || path.equals("amethyst_shard")
         || path.equals("quartz")
         || path.equals("lapis_lazuli")
         || path.equals("prismarine_crystals")) {
         out.add("valuables");
         out.add("ingots");
      }

      if ((path.startsWith("block_of_") || path.startsWith("block") && path.contains("_") || path.endsWith("_block"))
         && (
            path.contains("iron")
               || path.contains("gold")
               || path.contains("copper")
               || path.contains("netherite")
               || path.contains("diamond")
               || path.contains("emerald")
               || path.contains("lapis")
               || path.contains("coal")
               || path.contains("redstone")
               || path.contains("quartz")
         )) {
         out.add("metals");
      }
   }

   private static void addValuablesCheck(String path, LinkedHashSet<String> out) {
      if (path.contains("diamond") || path.contains("netherite") || path.contains("emerald") || path.contains("gold") || path.contains("ancient_debris")) {
         out.add("valuables");
      }
   }

   private static void addRedstoneCheck(String path, LinkedHashSet<String> out) {
      if (path.contains("redstone")
         || path.contains("piston")
         || path.contains("repeater")
         || path.contains("comparator")
         || path.contains("observer")
         || path.contains("dropper")
         || path.contains("dispenser")
         || path.contains("hopper")
         || path.contains("lever")
         || path.contains("daylight")
         || path.contains("tripwire")
         || path.contains("target_block")
         || path.contains("note_block")) {
         out.add("redstone");
      }
   }

   private static void addFuelCheck(Item item, String path, LinkedHashSet<String> out) {
      if (path.contains("coal")
         || path.contains("charcoal")
         || path.contains("log")
         || path.contains("planks")
         || path.contains("stick")
         || path.contains("wood")
         || path.contains("blaze_rod")) {
         out.add("fuel");
      }
   }

   private static void addCropsSeedsCheck(String path, LinkedHashSet<String> out) {
      if (path.endsWith("_seeds")
         || path.endsWith("_seed")
         || path.contains("wheat")
         || path.contains("potato")
         || path.contains("carrot")
         || path.contains("beetroot")
         || path.contains("melon")
         || path.contains("pumpkin")
         || path.contains("cocoa")
         || path.contains("nether_wart")
         || path.contains("sweet_berries")
         || path.contains("glow_berries")
         || path.contains("bamboo")) {
         out.add("crops");
      }
   }

   private static void addNameTokens(String registryId, LinkedHashSet<String> out) {
      String path = pathOf(registryId);
      String fullName = path.replace('_', ' ');
      out.add(fullName);
      out.add(fullName + "s");
      String[] parts = path.split("_");

      for (String part : parts) {
         if (part.length() >= 2) {
            out.add(part);
            out.add(part + "s");
         }
      }
   }

   private static Item resolveItem(String registryId) {
      try {
         ResourceLocation rl = ResourceLocation.tryParse(registryId);
         if (rl == null) {
            return null;
         }

         Item item = (Item)BuiltInRegistries.ITEM.get(rl);
         return item == Items.AIR && !"minecraft:air".equals(registryId) ? null : item;
      } catch (Exception e) {
         PlayerEngine.LOGGER.debug("WaypointItemCategorizer: could not resolve item '{}': {}", registryId, e.getMessage());
         return null;
      }
   }

   private static String pathOf(String registryId) {
      int colon = registryId.indexOf(58);
      return colon >= 0 ? registryId.substring(colon + 1).toLowerCase(Locale.ROOT) : registryId.toLowerCase(Locale.ROOT);
   }
}
