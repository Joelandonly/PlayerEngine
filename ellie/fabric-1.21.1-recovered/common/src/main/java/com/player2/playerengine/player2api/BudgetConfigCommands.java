package com.player2.playerengine.player2api;

import com.player2.playerengine.executor.BudgetFallbackBehavior;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.config.PlayerBudgetConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public final class BudgetConfigCommands {
   private static final String SERVER_FILE = "server_player2.json";
   private static final String PLAYER_FILE = "player-budget.json";

   private BudgetConfigCommands() {
   }

   public static void registerHelpEntries() {
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget soft",
            "player2 budget soft <value>",
            "help.playerengine.player2-budget-soft.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-budget-soft.arg.value")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget hard",
            "player2 budget hard <value>",
            "help.playerengine.player2-budget-hard.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-budget-hard.arg.value")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget window",
            "player2 budget window <minutes>",
            "help.playerengine.player2-budget-window.short",
            null,
            List.of(new ArgNote("minutes", "help.playerengine.player2-budget-window.arg.minutes")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget joules_soft",
            "player2 budget joules_soft <value>",
            "help.playerengine.player2-budget-joules_soft.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-budget-joules_soft.arg.value")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget joules_hard",
            "player2 budget joules_hard <value>",
            "help.playerengine.player2-budget-joules_hard.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-budget-joules_hard.arg.value")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget joules_refresh",
            "player2 budget joules_refresh <seconds>",
            "help.playerengine.player2-budget-joules_refresh.short",
            null,
            List.of(new ArgNote("seconds", "help.playerengine.player2-budget-joules_refresh.arg.seconds")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget fallback_profile",
            "player2 budget fallback_profile <name>",
            "help.playerengine.player2-budget-fallback-profile.short",
            null,
            List.of(new ArgNote("name", "help.playerengine.player2-budget-fallback-profile.arg.name")),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget fallback_behavior switch",
            "player2 budget fallback_behavior switch",
            "help.playerengine.player2-budget-fallback-behavior-switch.short",
            null,
            List.of(),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget fallback_behavior stop",
            "player2 budget fallback_behavior stop",
            "help.playerengine.player2-budget-fallback-behavior-stop.short",
            null,
            List.of(),
            2,
            null,
            "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine", "player2 budget reset", "player2 budget reset", "help.playerengine.player2-budget-reset.short", null, List.of(), 2, null, "budget"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 budget status",
            "player2 budget status",
            "help.playerengine.player2-budget-status.short",
            null,
            List.of(),
            2,
            null,
            "budget"
         )
      );
   }

   public static int setSoft(CommandSourceStack source, int value) {
      return setIntField(source, value, BudgetConfigCommands.Field.SOFT_CALLS);
   }

   public static int setHard(CommandSourceStack source, int value) {
      return setIntField(source, value, BudgetConfigCommands.Field.HARD_CALLS);
   }

   public static int setWindow(CommandSourceStack source, int minutes) {
      BudgetConfigCommands.Target target = resolveTarget(source);
      if (target.failed()) {
         source.sendFailure(target.failure());
         return 0;
      }

      Component fieldLabel = Component.translatable("message.playerengine.budget.field.window_minutes", new Object[]{minutes});
      if (target.useServerFile()) {
         Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
         c.setBudgetWindowMinutes(minutes);
         Player2ServerConfigHolder.validateAndFix(c);
         Player2ServerConfigHolder.save();
         BudgetTracker.resetAll();
         sendSaved(source, fieldLabel, "server_player2.json");
      } else {
         PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(target.server(), target.playerUuid());
         cfg.setBudgetWindowMinutes(minutes);
         PlayerBudgetConfigHolder.save(target.server(), target.playerUuid(), cfg);
         BudgetTracker.resetAll();
         sendSaved(source, fieldLabel, "player-budget.json");
      }

      return 1;
   }

   public static int setJoulesSoft(CommandSourceStack source, int value) {
      return setIntField(source, value, BudgetConfigCommands.Field.SOFT_JOULES);
   }

   public static int setJoulesHard(CommandSourceStack source, int value) {
      return setIntField(source, value, BudgetConfigCommands.Field.HARD_JOULES);
   }

   public static int setJoulesRefresh(CommandSourceStack source, int seconds) {
      BudgetConfigCommands.Target target = resolveTarget(source);
      if (target.failed()) {
         source.sendFailure(target.failure());
         return 0;
      }

      Component fieldLabel = Component.translatable("message.playerengine.budget.field.joules_refresh", new Object[]{seconds});
      if (target.useServerFile()) {
         Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
         c.setJoulesRefreshIntervalSeconds(seconds);
         Player2ServerConfigHolder.validateAndFix(c);
         Player2ServerConfigHolder.save();
         sendSaved(source, fieldLabel, "server_player2.json");
      } else {
         PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(target.server(), target.playerUuid());
         cfg.setJoulesRefreshIntervalSeconds(seconds);
         PlayerBudgetConfigHolder.save(target.server(), target.playerUuid(), cfg);
         sendSaved(source, fieldLabel, "player-budget.json");
      }

      return 1;
   }

   public static int setFallbackProfile(CommandSourceStack source, String profileName) {
      Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
      c.setFallbackProfile(profileName);
      Player2ServerConfigHolder.validateAndFix(c);
      Player2ServerConfigHolder.save();
      ProfileUrlResolver.invalidateCache();
      String display = profileName == null ? "none" : profileName;
      Component fieldLabel = Component.translatable("message.playerengine.budget.field.fallback_profile", new Object[]{display});
      sendSaved(source, fieldLabel, "server_player2.json");
      return 1;
   }

   public static int setFallbackBehaviorSwitch(CommandSourceStack source) {
      Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
      c.setBudgetFallbackBehavior(BudgetFallbackBehavior.SWITCH_PROFILE);
      Player2ServerConfigHolder.validateAndFix(c);
      Player2ServerConfigHolder.save();
      sendSaved(source, Component.translatable("message.playerengine.budget.field.fallback_behavior_switch"), "server_player2.json");
      return 1;
   }

   public static int setFallbackBehaviorStop(CommandSourceStack source) {
      Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
      c.setBudgetFallbackBehavior(BudgetFallbackBehavior.HARD_STOP);
      Player2ServerConfigHolder.validateAndFix(c);
      Player2ServerConfigHolder.save();
      sendSaved(source, Component.translatable("message.playerengine.budget.field.fallback_behavior_stop"), "server_player2.json");
      return 1;
   }

   public static int status(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      if (server == null) {
         source.sendFailure(Component.translatable("message.playerengine.budget.error.no_server"));
         return 0;
      }

      BudgetConfigCommands.Target target = resolveTarget(source);
      if (target.failed()) {
         source.sendFailure(target.failure());
         return 0;
      }

      BudgetThresholds thresholds = target.useServerFile() ? Player2ServerConfigHolder.get() : PlayerBudgetConfigHolder.load(server, target.playerUuid());
      Player2ServerRuntimeConfig serverCfg = Player2ServerConfigHolder.get();
      source.sendSuccess(() -> Component.translatable("message.playerengine.budget.status.header"), false);
      String fileNote = target.useServerFile() ? "server_player2.json" : "player-budget.json";
      source.sendSuccess(() -> Component.translatable("message.playerengine.budget.status.thresholds_from", new Object[]{fileNote}), false);
      source.sendSuccess(
         () -> Component.translatable(
            "message.playerengine.budget.status.call_limits",
            new Object[]{thresholds.getSoftBudgetCallsPerWindow(), thresholds.getHardBudgetCallsPerWindow(), thresholds.getBudgetWindowMinutes()}
         ),
         false
      );
      source.sendSuccess(
         () -> Component.translatable(
            "message.playerengine.budget.status.joules_thresholds",
            new Object[]{thresholds.getSoftJoulesThreshold(), thresholds.getHardJoulesThreshold(), thresholds.getJoulesRefreshIntervalSeconds()}
         ),
         false
      );
      source.sendSuccess(
         () -> Component.translatable(
            "message.playerengine.budget.status.fallback", new Object[]{serverCfg.getFallbackProfile(), serverCfg.getBudgetFallbackBehavior()}
         ),
         false
      );
      Map<String, BudgetTracker.WindowSnapshot> windows = BudgetTracker.statusSnapshot(serverCfg);
      if (windows.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.playerengine.budget.status.no_call_windows"), false);
      } else {
         long now = System.currentTimeMillis();

         for (Entry<String, BudgetTracker.WindowSnapshot> entry : windows.entrySet()) {
            BudgetTracker.WindowSnapshot snap = entry.getValue();
            long resets = Math.max(0L, snap.windowEndMs() - now) / 1000L;
            source.sendSuccess(
               () -> Component.translatable("message.playerengine.budget.status.call_window_entry", new Object[]{entry.getKey(), snap.callCount(), resets}),
               false
            );
         }
      }

      ConcurrentHashMap<String, JoulesCache.JoulesSnapshot> joulesSnaps = JoulesCache.statusSnapshot();
      if (joulesSnaps.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.playerengine.budget.status.no_joules_data"), false);
      } else {
         for (Entry<String, JoulesCache.JoulesSnapshot> entry : joulesSnaps.entrySet()) {
            JoulesCache.JoulesSnapshot snap = entry.getValue();
            long ageS = (System.currentTimeMillis() - snap.refreshedAtMs) / 1000L;
            Component patronDisplay = snap.patronTier.isEmpty()
               ? Component.translatable("message.playerengine.budget.status.patron_none")
               : Component.literal(snap.patronTier);
            source.sendSuccess(
               () -> Component.translatable(
                  "message.playerengine.budget.status.joules_entry", new Object[]{entry.getKey(), snap.joulesDisplay(), patronDisplay, ageS}
               ),
               false
            );
         }
      }

      return 1;
   }

   private static int setIntField(CommandSourceStack source, int value, BudgetConfigCommands.Field field) {
      BudgetConfigCommands.Target target = resolveTarget(source);
      if (target.failed()) {
         source.sendFailure(target.failure());
         return 0;
      }

      Component label = switch (field) {
         case SOFT_CALLS -> Component.translatable("message.playerengine.budget.field.soft_calls", new Object[]{value});
         case HARD_CALLS -> Component.translatable("message.playerengine.budget.field.hard_calls", new Object[]{value});
         case SOFT_JOULES -> Component.translatable("message.playerengine.budget.field.soft_joules", new Object[]{value});
         case HARD_JOULES -> Component.translatable("message.playerengine.budget.field.hard_joules", new Object[]{value});
      };
      if (target.useServerFile()) {
         Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
         applyField(c, field, value);
         Player2ServerConfigHolder.validateAndFix(c);
         Player2ServerConfigHolder.save();
         sendSaved(source, label, "server_player2.json");
      } else {
         PlayerBudgetConfig cfg = PlayerBudgetConfigHolder.load(target.server(), target.playerUuid());
         applyField(cfg, field, value);
         PlayerBudgetConfigHolder.save(target.server(), target.playerUuid(), cfg);
         sendSaved(source, label, "player-budget.json");
      }

      return 1;
   }

   private static void applyField(Player2ServerRuntimeConfig c, BudgetConfigCommands.Field field, int value) {
      switch (field) {
         case SOFT_CALLS:
            c.setSoftBudgetCallsPerWindow(value);
            break;
         case HARD_CALLS:
            c.setHardBudgetCallsPerWindow(value);
            break;
         case SOFT_JOULES:
            c.setSoftJoulesThreshold(value);
            break;
         case HARD_JOULES:
            c.setHardJoulesThreshold(value);
      }
   }

   private static void applyField(PlayerBudgetConfig cfg, BudgetConfigCommands.Field field, int value) {
      switch (field) {
         case SOFT_CALLS:
            cfg.setSoftBudgetCallsPerWindow(value);
            break;
         case HARD_CALLS:
            cfg.setHardBudgetCallsPerWindow(value);
            break;
         case SOFT_JOULES:
            cfg.setSoftJoulesThreshold(value);
            break;
         case HARD_JOULES:
            cfg.setHardJoulesThreshold(value);
      }
   }

   private static void sendSaved(CommandSourceStack source, Component fieldLabel, String fileLabel) {
      source.sendSuccess(() -> Component.translatable("message.playerengine.budget.saved", new Object[]{fieldLabel, fileLabel}), false);
   }

   private static BudgetConfigCommands.Target resolveTarget(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      boolean serverFile = BudgetThresholdsResolver.usesServerBudgetStore(server);
      UUID uuid = null;

      try {
         ServerPlayer player = source.getPlayerOrException();
         uuid = player.getUUID();
      } catch (Exception var5) {
      }

      return !serverFile && uuid == null
         ? BudgetConfigCommands.Target.failure(Component.translatable("message.playerengine.budget.error.player_only"))
         : new BudgetConfigCommands.Target(server, uuid, serverFile, null);
   }

   private enum Field {
      SOFT_CALLS,
      HARD_CALLS,
      SOFT_JOULES,
      HARD_JOULES;
   }

   private record Target(MinecraftServer server, UUID playerUuid, boolean useServerFile, Component failure) {
      boolean failed() {
         return this.failure != null;
      }

      static BudgetConfigCommands.Target failure(Component message) {
         return new BudgetConfigCommands.Target(null, null, false, message);
      }
   }
}
