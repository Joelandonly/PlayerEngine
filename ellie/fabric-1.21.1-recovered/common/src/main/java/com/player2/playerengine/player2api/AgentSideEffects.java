package com.player2.playerengine.player2api;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.commands.base.UnknownCommandException;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.manager.TTSManager;
import com.player2.playerengine.retrieval.RagDeepSearchCommands;
import com.player2.playerengine.retrieval.learning.AliasLearningService;
import com.player2.playerengine.tasks.LookAtOwnerTask;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AgentSideEffects {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String MIXED_IDLE_MODEL_ERROR = "idle must be sent by itself and cannot be combined with another command";
   private static final String IGNORED_IDLE_MODEL_NOTE = "idle was ignored because a real user task is still active; use stop to cancel that task";

   public static void onEntityMessage(MinecraftServer server, Event.CharacterMessage characterMessage) {
      AgentConversationData sendingCharacterData = characterMessage.sendingCharacterData();
      boolean hasText = characterMessage.message() != null && !characterMessage.message().isBlank();
      List<MarkerParser.SegmentBoundary> pendingBoundaries = sendingCharacterData.getPendingSegmentActions();
      boolean hasPendingGesture = pendingBoundaries != null && !pendingBoundaries.isEmpty();
      if (hasText) {
         Component chatLine = Component.translatable(
            "message.playerengine.chat.character_message", new Object[]{sendingCharacterData.getName(), characterMessage.message()}
         );

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            broadcastChatToPlayer(server, chatLine, player);
         }
      }

      if (hasText || hasPendingGesture) {
         TTSManager.TTS(
            characterMessage.message(),
            sendingCharacterData.getPendingChunks(),
            sendingCharacterData.getPendingSegmentActions(),
            sendingCharacterData.getCharacter(),
            sendingCharacterData.getPlayer2apiService(),
            sendingCharacterData.getUUID()
         );
         sendingCharacterData.markSpeakingFor(characterMessage.message());
         List<String> invalidMarkers = sendingCharacterData.getPendingInvalidMarkers();
         if (invalidMarkers != null && !invalidMarkers.isEmpty()) {
            broadcastChatToAllPlayers(
               server,
               Component.translatable(
                  "message.playerengine.agent.invalid_gesture", new Object[]{sendingCharacterData.getName(), String.join("', '", invalidMarkers)}
               )
            );
         }

         ConversationManager.onAICharacterMessage(characterMessage, characterMessage.sendingCharacterData().getUUID());
      }

      if (characterMessage.command() != null && !characterMessage.command().isBlank()) {
         onCommandListGenerated(
            characterMessage.sendingCharacterData().getMod(), characterMessage.command(), characterMessage.sendingCharacterData()::onCommandFinish
         );
      }
   }

   public static void onError(MinecraftServer server, String errMsg, ServerPlayer player) {
      LOGGER.error(errMsg);
      broadcastErrorMsgToPlayer(server, errMsg, player);
   }

   public static void onCommandListGenerated(PlayerEngineController mod, String command, Consumer<AgentSideEffects.CommandExecutionStopReason> onStop) {
      CommandExecutor cmdExecutor = mod.getCommandExecutor();
      String commandWithPrefix = cmdExecutor.isClientCommand(command) ? command : cmdExecutor.getCommandPrefix() + command;
      if (commandWithPrefix.equals("@stop")) {
         mod.isStopping = true;
      } else {
         mod.isStopping = false;
      }

      AgentSideEffects.IdleCommandShape idleShape = classifyIdleCommandLine(commandWithPrefix, cmdExecutor.getCommandPrefix());
      if (idleShape == AgentSideEffects.IdleCommandShape.MIXED_IDLE) {
         rejectMixedIdleCommand(mod, onStop);
      } else {
         String commandId = idleShape == AgentSideEffects.IdleCommandShape.SOLE_IDLE ? "idle" : firstCommandId(commandWithPrefix, cmdExecutor);
         AgentSideEffects.IdleHandling idleHandling = classifyIdleHandling(
            commandId, mod.hasActiveNonIdleUserTask(), mod.getModSettings().isEnableLookAtOwnerIdle()
         );
         if (idleHandling != AgentSideEffects.IdleHandling.NOT_IDLE) {
            if (idleHandling == AgentSideEffects.IdleHandling.IGNORE_ACTIVE_TASK) {
               LOGGER.info(
                  "Ignoring idle while a non-idle user task is active (step={})", mod.getActiveTrackedStep().map(e -> e.getStepKind()).orElse("unknown")
               );
               reportIgnoredIdleToPlayer(mod);
            } else if (idleHandling == AgentSideEffects.IdleHandling.INSTALL_LOOK_AT_OWNER) {
               mod.runIdleUserTask(new LookAtOwnerTask());
            } else if (idleHandling == AgentSideEffects.IdleHandling.LEAVE_WITHOUT_USER_TASK) {
               mod.clearPolicyIdleUserTask();
            }

            completeHandledIdle(idleHandling, commandWithPrefix, onStop);
         } else {
            String processedCommandWithPrefix = commandWithPrefix;
            MinecraftServer server = mod.getWorld().getServer();
            UUID ownerUuid = mod.getOwner() != null ? mod.getOwner().getUUID() : null;
            UUID botUuid = mod.getPlayer().getUUID();
            String acceptedCommandId = firstCommandId(processedCommandWithPrefix, cmdExecutor);
            if (RagDeepSearchCommands.isMetaCommandId(acceptedCommandId)) {
               LOGGER.debug("[B5] ignoring virtual command rag_deepsearch in AgentSideEffects");
            } else {
               Consumer<String> onFinishWithNote = note -> {
                  if (mod.isStopping) {
                     LOGGER.info("[AgentSideEffects/AgentSideEffects]: (%s) was cancelled. Not adding finish event to queue.", processedCommandWithPrefix);
                     onStop.accept(new AgentSideEffects.CommandExecutionStopReason.Cancelled(commandWithPrefix));
                     LOGGER.info("after cancel, not running look at owner");
                  } else {
                     if (!commandWithPrefix.equals("@bodylang greeting")) {
                        LOGGER.info("Running on stop after finish cmd={}", commandWithPrefix);
                        onStop.accept(new AgentSideEffects.CommandExecutionStopReason.Finished(commandWithPrefix, note));
                     } else {
                        LOGGER.info("Ignore onStop for bodylang greeting");
                     }

                     if (mod.getModSettings().isEnableLookAtOwnerIdle()) {
                        LOGGER.info("Running look at owner task after finish cmd={}", commandWithPrefix);
                        mod.runIdleUserTask(new LookAtOwnerTask());
                     }
                  }
               };
               Runnable runExecute = () -> cmdExecutor.execute(processedCommandWithPrefix, () -> {
                  if (server != null && ownerUuid != null && acceptedCommandId != null) {
                     AliasLearningService.onCommandAccepted(server, ownerUuid, botUuid, acceptedCommandId, cmdExecutor);
                  }
               }, () -> onFinishWithNote.accept(null), onFinishWithNote, err -> {
                  boolean unknownCommand = err instanceof UnknownCommandException;
                  if (!unknownCommand && server != null && ownerUuid != null && acceptedCommandId != null) {
                     AliasLearningService.onCommandRejected(server, ownerUuid, botUuid, acceptedCommandId, err.getMessage());
                  }

                  if (unknownCommand && server != null && ownerUuid != null) {
                     ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
                     if (ownerPlayer != null) {
                        broadcastChatToPlayer(server, playerCorrectionFor(processedCommandWithPrefix, cmdExecutor, err.getMessage()), ownerPlayer);
                     }
                  }

                  onStop.accept(new AgentSideEffects.CommandExecutionStopReason.Error(commandWithPrefix, err.getMessage()));
                  if (mod.getModSettings().isEnableLookAtOwnerIdle()) {
                     LOGGER.info("Running look at owner aftr error in cmd={}", commandWithPrefix);
                     mod.runIdleUserTask(new LookAtOwnerTask());
                  }
               });
               if (server != null && !server.isSameThread()) {
                  server.execute(runExecute);
               } else {
                  runExecute.run();
               }
            }
         }
      }
   }

   public static void broadcastChatToPlayer(MinecraftServer server, String message, ServerPlayer player) {
      player.displayClientMessage(Component.literal(message), false);
   }

   public static void broadcastChatToPlayer(MinecraftServer server, Component message, ServerPlayer player) {
      player.displayClientMessage(message, false);
   }

   private static void broadcastErrorMsgToPlayer(MinecraftServer server, String message, ServerPlayer player) {
      MutableComponent output = Component.literal(message);
      output.setStyle(output.getStyle().applyFormat(ChatFormatting.RED));
      player.displayClientMessage(output, false);
   }

   public static void broadcastChatToAllPlayers(MinecraftServer server, String message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         broadcastChatToPlayer(server, message, player);
      }
   }

   public static void broadcastChatToAllPlayers(MinecraftServer server, Component message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         broadcastChatToPlayer(server, message, player);
      }
   }

   public static String firstCommandId(String commandWithPrefix, CommandExecutor cmdExecutor) {
      if (commandWithPrefix != null && !commandWithPrefix.isBlank()) {
         try {
            String line = commandWithPrefix;
            if (cmdExecutor.isClientCommand(line)) {
               line = line.substring(cmdExecutor.getCommandPrefix().length());
            }

            String first = line.split(";")[0].trim();
            if (first.isEmpty()) {
               return null;
            }

            int sp = first.indexOf(32);
            String name = sp == -1 ? first : first.substring(0, sp);
            return CommandExecutor.resolveName(name.toLowerCase(Locale.ROOT));
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   static AgentSideEffects.IdleCommandShape classifyIdleCommandLine(String commandWithPrefix, String commandPrefix) {
      if (commandWithPrefix != null && !commandWithPrefix.isBlank()) {
         String prefix = commandPrefix == null ? "" : commandPrefix;
         String line = commandWithPrefix;
         if (!prefix.isEmpty() && line.startsWith(prefix)) {
            line = line.substring(prefix.length());
         }

         int commandCount = 0;
         boolean containsIdle = false;

         for (String rawPart : line.split(";", -1)) {
            String part = rawPart.trim();
            if (!part.isEmpty()) {
               if (!prefix.isEmpty() && part.startsWith(prefix)) {
                  part = part.substring(prefix.length()).trim();
               }

               if (!part.isEmpty()) {
                  int space = part.indexOf(32);
                  String rawName = space < 0 ? part : part.substring(0, space);
                  String commandId = CommandExecutor.resolveName(rawName.toLowerCase(Locale.ROOT));
                  commandCount++;
                  containsIdle |= "idle".equals(commandId);
               }
            }
         }

         if (!containsIdle) {
            return AgentSideEffects.IdleCommandShape.NO_IDLE;
         } else {
            return commandCount == 1 ? AgentSideEffects.IdleCommandShape.SOLE_IDLE : AgentSideEffects.IdleCommandShape.MIXED_IDLE;
         }
      } else {
         return AgentSideEffects.IdleCommandShape.NO_IDLE;
      }
   }

   static AgentSideEffects.IdleHandling classifyIdleHandling(String commandId, boolean hasActiveNonIdleTask, boolean enableLookAtOwnerIdle) {
      if (!"idle".equals(commandId)) {
         return AgentSideEffects.IdleHandling.NOT_IDLE;
      } else if (hasActiveNonIdleTask) {
         return AgentSideEffects.IdleHandling.IGNORE_ACTIVE_TASK;
      } else {
         return enableLookAtOwnerIdle ? AgentSideEffects.IdleHandling.INSTALL_LOOK_AT_OWNER : AgentSideEffects.IdleHandling.LEAVE_WITHOUT_USER_TASK;
      }
   }

   static void completeHandledIdle(
      AgentSideEffects.IdleHandling handling, String commandWithPrefix, Consumer<AgentSideEffects.CommandExecutionStopReason> onStop
   ) {
      if (handling != null && handling != AgentSideEffects.IdleHandling.NOT_IDLE) {
         String note = handling == AgentSideEffects.IdleHandling.IGNORE_ACTIVE_TASK
            ? "idle was ignored because a real user task is still active; use stop to cancel that task"
            : null;
         Objects.requireNonNull(onStop, "onStop").accept(new AgentSideEffects.CommandExecutionStopReason.Finished(commandWithPrefix, note));
      } else {
         throw new IllegalArgumentException("idle completion requires a handled idle branch");
      }
   }

   private static void reportIgnoredIdleToPlayer(PlayerEngineController mod) {
      MinecraftServer server = mod.getWorld() == null ? null : mod.getWorld().getServer();
      if (server != null && mod.getOwner() instanceof ServerPlayer owner) {
         broadcastChatToPlayer(server, Component.translatable("message.playerengine.agent.idle_ignored_active_task"), owner);
      }
   }

   private static void rejectMixedIdleCommand(PlayerEngineController mod, Consumer<AgentSideEffects.CommandExecutionStopReason> onStop) {
      MinecraftServer server = mod.getWorld() == null ? null : mod.getWorld().getServer();
      if (server != null && mod.getOwner() instanceof ServerPlayer owner) {
         broadcastChatToPlayer(server, Component.translatable("message.playerengine.agent.mixed_idle_commands"), owner);
      }

      completeRejectedMixedIdle(onStop);
   }

   static void completeRejectedMixedIdle(Consumer<AgentSideEffects.CommandExecutionStopReason> onStop) {
      Objects.requireNonNull(onStop, "onStop")
         .accept(new AgentSideEffects.CommandExecutionStopReason.Error("idle", "idle must be sent by itself and cannot be combined with another command"));
   }

   private static Component playerCorrectionFor(String commandWithPrefix, CommandExecutor cmdExecutor, String enrichedMessage) {
      String rawName = rawCommandName(commandWithPrefix, cmdExecutor);
      String suggestion = extractSuggestion(enrichedMessage);
      if (rawName != null && suggestion != null) {
         return Component.translatable("message.playerengine.agent.correction_with_suggestion", new Object[]{rawName, suggestion});
      } else {
         return rawName != null
            ? Component.translatable("message.playerengine.agent.correction_no_suggestion", new Object[]{rawName})
            : Component.translatable("message.playerengine.agent.correction_fallback");
      }
   }

   private static String rawCommandName(String commandWithPrefix, CommandExecutor cmdExecutor) {
      if (commandWithPrefix != null && !commandWithPrefix.isBlank()) {
         try {
            String line = commandWithPrefix;
            if (cmdExecutor.isClientCommand(line)) {
               line = line.substring(cmdExecutor.getCommandPrefix().length());
            }

            String first = line.split(";")[0].trim();
            if (first.isEmpty()) {
               return null;
            }

            int sp = first.indexOf(32);
            return (sp == -1 ? first : first.substring(0, sp)).toLowerCase(Locale.ROOT);
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String extractSuggestion(String enrichedMessage) {
      if (enrichedMessage == null) {
         return null;
      }

      int idx = enrichedMessage.indexOf("Did you mean \"");
      if (idx < 0) {
         return null;
      }

      int start = idx + "Did you mean \"".length();
      int end = enrichedMessage.indexOf(34, start);
      return end <= start ? null : enrichedMessage.substring(start, end);
   }

   public static void teleportOwnerTo(AgentConversationData data) {
      Player owner = data.getMod().getOwner();
      LivingEntity entity = data.getEntity();
      double x = entity.getX() + 0.5;
      double y = entity.getY() + 0.5;
      double z = entity.getZ() + 0.5;
      owner.teleportTo(x, y, z);
   }

   public sealed interface CommandExecutionStopReason
      permits AgentSideEffects.CommandExecutionStopReason.Cancelled,
      AgentSideEffects.CommandExecutionStopReason.Finished,
      AgentSideEffects.CommandExecutionStopReason.Error {
      String commandName();

      record Cancelled(String commandName) implements AgentSideEffects.CommandExecutionStopReason {
      }

      record Error(String commandName, String errMsg) implements AgentSideEffects.CommandExecutionStopReason {
      }

      record Finished(String commandName, String note) implements AgentSideEffects.CommandExecutionStopReason {
      }
   }

   enum IdleCommandShape {
      NO_IDLE,
      SOLE_IDLE,
      MIXED_IDLE;
   }

   enum IdleHandling {
      NOT_IDLE,
      IGNORE_ACTIVE_TASK,
      INSTALL_LOOK_AT_OWNER,
      LEAVE_WITHOUT_USER_TASK;
   }
}
