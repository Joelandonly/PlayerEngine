package com.player2.playerengine.fabric;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.player2.playerengine.automaton.utils.DirUtil;
import com.player2.playerengine.safezone.SafeZoneRegion;
import com.player2.playerengine.safezone.SafeZoneService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Completion hints for native arguments. The native commands still validate the submitted text. */
final class EllieSlashSuggestions {
   private static final List<String> ITEM_LIST_COMMANDS = List.of(
      "deposit", "deposit_to_storage", "withdraw_from_storage", "scan_storage", "plant_farm"
   );
   private static final List<String> SIGN_LINES = List.of("f0", "f1", "f2", "f3", "b0", "b1", "b2", "b3");

   private EllieSlashSuggestions() {}

   static CompletableFuture<Suggestions> suggest(String id, CommandSourceStack source, SuggestionsBuilder builder) {
      String input = builder.getRemaining();
      List<String> previous = new ArrayList<>();
      int start = 0;
      boolean quoted = false;
      for (int i = 0; i < input.length(); i++) {
         char ch = input.charAt(i);
         if (ch == '\\' && i + 1 < input.length()) {
            i++;
         } else if (ch == '"') {
            quoted = !quoted;
         } else if (!quoted && Character.isWhitespace(ch)) {
            if (i > start) previous.add(input.substring(start, i));
            start = i + 1;
         }
      }
      String current = input.substring(start);
      // Item-list commands reuse item completion after a comma, including without a space.
      if (ITEM_LIST_COMMANDS.contains(id)) {
         start += current.lastIndexOf(',') + 1;
      }
      SuggestionsBuilder token = builder.createOffset(builder.getStart() + start);
      String prefix = token.getRemainingLowerCase();
      choices(id, source, previous, prefix).distinct().filter(value -> matches(value, prefix)).forEach(token::suggest);
      return token.buildFuture();
   }

   private static Stream<String> choices(String id, CommandSourceStack source, List<String> previous, String prefix) {
      int index = previous.size();
      String first = index == 0 ? "" : previous.getFirst().toLowerCase(Locale.ROOT);
      return switch (id) {
         case "get" -> index == 0 ? Stream.concat(items(prefix), Stream.of("log", "planks"))
            : first.matches("[0-9]+") && (index == 1 || index == 2 && previous.get(1).equalsIgnoreCase("of"))
               ? Stream.concat(items(prefix), at(index, 1, "of"))
            : at(index, 1, "1", "8", "16", "64");
         case "smelt", "smith" -> index == 0 ? items(prefix) : at(index, 1, "1", "8", "16", "64");
         case "eat_food" -> index == 0 ? registry(BuiltInRegistries.ITEM.keySet().stream()
            .filter(key -> BuiltInRegistries.ITEM.get(key).components().has(net.minecraft.core.component.DataComponents.FOOD)), prefix) : Stream.empty();
         case "acquire_block", "mine" -> index == 0 ? blocks(prefix) : at(index, 1, "1", "8", "16", "64");
         case "block_info", "scan" -> index == 0 ? blocks(prefix) : Stream.empty();
         case "locate_block" -> index == 0 ? blocks(prefix) : at(index, 1, "16", "32", "64");
         case "follow" -> index == 0 ? source.getOnlinePlayerNames().stream() : Stream.empty();
         case "attack" -> index == 0
            ? Stream.concat(source.getOnlinePlayerNames().stream(), registry(BuiltInRegistries.ENTITY_TYPE.keySet().stream(), prefix))
            : at(index, 1, "1", "5", "10");
         case "give" -> switch (index) {
            case 0 -> Stream.concat(source.getOnlinePlayerNames().stream(), items(prefix));
            case 1 -> Stream.concat(items(prefix), Stream.of("1", "8", "64"));
            case 2 -> Stream.of("1", "8", "64");
            default -> Stream.empty();
         };
         case "equip" -> items(prefix);
         case "food", "meat" -> at(index, 0, "6", "10", "20");
         case "read_signs" -> at(index, 0, "8", "16", "32");
         case "bodylang" -> at(index, 0, "greeting", "nod_head", "shake_head", "victory");
         case "patrol" -> index == 0 ? Stream.concat(coords(source, index), Stream.of("off", "status")) : coords(source, index);
         case "netherbridge" -> switch (index) {
            case 0 -> Stream.of("north", "south", "east", "west");
            case 1 -> Stream.of("16", "32", "64", "128", "256");
            default -> coords(source, index - 2);
         };
         case "hero" -> at(index, 0, "on", "off", "status");
         case "chatclef", "set_attack_hostiles" -> at(index, 0, "on", "off");
         case "set_follow_mode" -> at(index, 0, "NORMAL", "COWARD", "DEFENDER");
         case "locate_structure" -> at(index, 0, "stronghold", "desert_temple");
         case "goto" -> {
            if (index > 3 || previous.stream().anyMatch(value -> !value.matches("-?[0-9]+"))) yield Stream.empty();
            yield Stream.concat(coords(source, index), Stream.of("overworld", "nether", "end"));
         }
         case "create_waypoint", "audit_waypoint", "compare_waypoint", "delete_waypoint", "harvest_farm" -> coords(source, index);
         case "excavate" -> coords(source, index % 3, index < 6);
         case "locate_waypoints" -> at(index, 0, "chest", "sign", "farm");
         case "setup_farm" -> Stream.concat(coords(source, index), at(index, 0, "bot", "here", "owner", "player"));
         case "cleanup_storage" -> Stream.concat(coords(source, index), at(index, 0, "status", "off"));
         case "build_structure" -> switch (index) {
            case 3 -> Stream.of("\"compact cobblestone village\"", "\"small wooden cabin\"", "\"storage shed\"");
            case 4 -> Stream.of("\"with a road and a door\"");
            default -> coords(source, index);
         };
         case "schematic_prepare", "schematic_build" -> index == 0 ? schematics(source) : coords(source, index - 1);
         case "plant_farm" -> index == 0 || index == 4 && prefix.startsWith("single:")
            ? crops(prefix, index == 4 ? "single:" : "")
            : index == 4 ? Stream.of("preserve", "mixed", "single:") : coords(source, index - 1);
         case "deposit" -> itemList(items(prefix), index, prefix);
         case "deposit_to_storage", "withdraw_from_storage" -> index < 3
            ? coords(source, index) : itemList(items(prefix), index - 3, prefix);
         case "scan_storage" -> index < 3 ? coords(source, index)
            : index == 3 ? Stream.of("light", "deep", "targeted")
            : previous.get(3).equalsIgnoreCase("targeted") ? itemList(items(prefix), index - 4, prefix) : Stream.empty();
         case "deposit_storage_slot", "withdraw_storage_slot" -> index < 3 ? coords(source, index)
            : index == 4 ? Stream.of("1", "8", "64") : index < 6 ? Stream.of("0", "1", "2") : Stream.empty();
         case "place_sign" -> {
            if (index < 3) yield coords(source, index);
            if (index == 3) yield Stream.of("up", "down", "north", "south", "east", "west");
            var lines = SIGN_LINES.stream().filter(line -> !previous.contains(line) && previous.stream().noneMatch(arg -> arg.startsWith(line + "=")));
            yield index == 4 ? Stream.concat(lines, registry(BuiltInRegistries.ITEM.keySet().stream()
               .filter(key -> key.getPath().endsWith("_sign")), prefix)) : lines;
         }
         case "agentic" -> switch (index) {
            case 0 -> Stream.of("get", "collect loose drops");
            case 1 -> first.equals("get") ? items(prefix) : at(index, 1, "loose drops");
            case 2 -> first.equals("get") ? Stream.of("1", "8", "64") : at(index, 2, "drops");
            default -> Stream.empty();
         };
         case "ellie_storage" -> index == 0
            ? Stream.of("init", "status", "list", "resume", "expand", "reserve", "register", "organize", "off", "on", "approve_floor")
            : switch (first) {
               case "init" -> at(index, 1, "here");
               case "expand" -> at(index, 1, "4", "8", "12");
               case "reserve" -> at(index, 1, "4", "8", "12");
               case "register" -> coords(source, index - 1);
               default -> Stream.empty();
            };
         case "safezone" -> index == 0 ? Stream.of("here", "pos1", "pos2", "save", "add", "list", "check", "remove")
            : switch (first) {
               case "here" -> index == 1 ? Stream.of("home") : index == 2 ? Stream.of("8", "12", "16") : at(index, 3, "4", "8", "12");
               case "save" -> at(index, 1, "home");
               case "add" -> index == 1 ? Stream.of("home") : coords(source, (index - 2) % 3, index < 8);
               case "remove" -> index == 1 ? safeZoneNames(source) : Stream.empty();
               default -> Stream.empty();
            };
         case "waterfill" -> switch (index) {
            case 0 -> Stream.of("here");
            case 1, 2 -> first.equals("here") ? Stream.of("1", "2", "4") : Stream.empty();
            case 3 -> first.equals("here") ? Stream.of("auto", "dirt", "cobblestone", "cobbled_deepslate", "stone", "oak_planks") : Stream.empty();
            default -> Stream.empty();
         };
         default -> Stream.empty();
      };
   }

   private static Stream<String> at(int index, int expected, String... options) {
      return index == expected ? Stream.of(options) : Stream.empty();
   }

   private static Stream<String> coords(CommandSourceStack source, int axis) {
      return coords(source, axis, true);
   }

   private static Stream<String> coords(CommandSourceStack source, int axis, boolean enabled) {
      if (!enabled || axis < 0 || axis > 2) return Stream.empty();
      var position = net.minecraft.core.BlockPos.containing(source.getPosition());
      return switch (axis) {
         case 0 -> Stream.of(Integer.toString(position.getX()), position.getX() + " " + position.getY() + " " + position.getZ());
         case 1 -> Stream.of(Integer.toString(position.getY()));
         default -> Stream.of(Integer.toString(position.getZ()));
      };
   }

   private static Stream<String> items(String prefix) {
      return registry(BuiltInRegistries.ITEM.keySet().stream(), prefix);
   }

   private static Stream<String> blocks(String prefix) {
      return registry(BuiltInRegistries.BLOCK.keySet().stream(), prefix);
   }

   private static Stream<String> registry(Stream<ResourceLocation> keys, String prefix) {
      return keys.map(key -> key.getNamespace().equals("minecraft") && !prefix.contains(":") ? key.getPath() : key.toString());
   }

   private static Stream<String> itemList(Stream<String> items, int index, String prefix) {
      return index == 0 || !prefix.isEmpty() && !Character.isDigit(prefix.charAt(0))
         ? items : Stream.concat(Stream.of("1", "8", "16", "64"), items);
   }

   private static Stream<String> crops(String prefix, String policy) {
      return Stream.of("wheat_seeds", "beetroot_seeds", "carrot", "potato", "melon_seeds", "pumpkin_seeds")
         .map(id -> policy + (prefix.contains("minecraft:") ? "minecraft:" : "") + id)
         .map(id -> policy.isEmpty() ? id + "=" : id);
   }

   private static Stream<String> schematics(CommandSourceStack source) {
      if (!(source.getEntity() instanceof ServerPlayer player) || EllieSlashCommands.ownedCompanion(player) == null) {
         return Stream.empty();
      }
      Predicate<String> supported = name -> name.endsWith(".litematic") || name.endsWith(".schem");
      try (var paths = Files.list(DirUtil.getGameDir().resolve("schematics"))) {
         return paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
            .filter(supported).limit(256).map(name -> name.chars().anyMatch(Character::isWhitespace) || name.contains(";") || name.contains("\"")
               ? "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : name)
            .toList().stream();
      } catch (IOException | UncheckedIOException ignored) {
         return Stream.empty();
      }
   }

   private static Stream<String> safeZoneNames(CommandSourceStack source) {
      if (!(source.getEntity() instanceof ServerPlayer player)) return Stream.empty();
      String dimension = player.serverLevel().dimension().location().toString();
      return SafeZoneService.data(source.getServer()).owned(player.getUUID()).stream()
         .filter(zone -> zone.dimension().equals(dimension)).map(SafeZoneRegion::name);
   }

   private static boolean matches(String value, String prefix) {
      return value.toLowerCase(Locale.ROOT).startsWith(prefix)
         || value.startsWith("\"") && value.substring(1).toLowerCase(Locale.ROOT).startsWith(prefix);
   }
}
