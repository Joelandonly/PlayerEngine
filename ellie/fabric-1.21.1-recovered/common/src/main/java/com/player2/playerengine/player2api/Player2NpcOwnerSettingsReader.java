package com.player2.playerengine.player2api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Player2NpcOwnerSettingsReader {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final String KEY_USER_LIST_MODE = "userListMode";
   public static final String KEY_BOT_LIST_MODE = "botListMode";
   public static final String KEY_AUTO_EQUIP_ARMOR = "autoEquipArmor";

   private Player2NpcOwnerSettingsReader() {
   }

   public static Player2NpcOwnerSettingsReader.ListMode userListMode(MinecraftServer server, UUID settingsOwnerUuid) {
      return load(server, settingsOwnerUuid).user;
   }

   public static Player2NpcOwnerSettingsReader.ListMode botListMode(MinecraftServer server, UUID settingsOwnerUuid) {
      return load(server, settingsOwnerUuid).bot;
   }

   public static boolean autoEquipArmor(MinecraftServer server, UUID settingsOwnerUuid) {
      return isAutoEquipEnabled(server, settingsOwnerUuid);
   }

   public static boolean isAutoEquipEnabled(MinecraftServer server, UUID settingsOwnerUuid) {
      return load(server, settingsOwnerUuid).autoEquipArmor;
   }

   private static Player2NpcOwnerSettingsReader.Settings load(MinecraftServer server, UUID ownerUuid) {
      Path path = Player2NpcPersistencePaths.userSettingsFile(server, ownerUuid);
      if (!Files.isRegularFile(path)) {
         return new Player2NpcOwnerSettingsReader.Settings(
            Player2NpcOwnerSettingsReader.ListMode.BLACKLIST, Player2NpcOwnerSettingsReader.ListMode.BLACKLIST, true
         );
      }

      try {
         String raw = Files.readString(path, StandardCharsets.UTF_8);
         JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
         Player2NpcOwnerSettingsReader.ListMode u = root.has("userListMode")
            ? Player2NpcOwnerSettingsReader.ListMode.fromJson(root.get("userListMode").getAsString())
            : Player2NpcOwnerSettingsReader.ListMode.BLACKLIST;
         Player2NpcOwnerSettingsReader.ListMode b = root.has("botListMode")
            ? Player2NpcOwnerSettingsReader.ListMode.fromJson(root.get("botListMode").getAsString())
            : Player2NpcOwnerSettingsReader.ListMode.BLACKLIST;
         boolean autoEquip = !root.has("autoEquipArmor") || root.get("autoEquipArmor").getAsBoolean();
         return new Player2NpcOwnerSettingsReader.Settings(u, b, autoEquip);
      } catch (Exception e) {
         LOGGER.warn("Failed reading user settings at {}", path, e);
         return new Player2NpcOwnerSettingsReader.Settings(
            Player2NpcOwnerSettingsReader.ListMode.BLACKLIST, Player2NpcOwnerSettingsReader.ListMode.BLACKLIST, true
         );
      }
   }

   public enum ListMode {
      BLACKLIST,
      WHITELIST;

      static Player2NpcOwnerSettingsReader.ListMode fromJson(String raw) {
         return raw != null && raw.trim().equalsIgnoreCase("whitelist") ? WHITELIST : BLACKLIST;
      }
   }

   private static final class Settings {
      final Player2NpcOwnerSettingsReader.ListMode user;
      final Player2NpcOwnerSettingsReader.ListMode bot;
      final boolean autoEquipArmor;

      Settings(Player2NpcOwnerSettingsReader.ListMode user, Player2NpcOwnerSettingsReader.ListMode bot, boolean autoEquipArmor) {
         this.user = user;
         this.bot = bot;
         this.autoEquipArmor = autoEquipArmor;
      }
   }
}
