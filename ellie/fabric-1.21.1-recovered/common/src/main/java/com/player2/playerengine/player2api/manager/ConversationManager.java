package com.player2.playerengine.player2api.manager;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.AgentSideEffects;
import com.player2.playerengine.player2api.BotBlacklistPolicy;
import com.player2.playerengine.player2api.CallByNameMentionRouter;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.Event;
import com.player2.playerengine.player2api.LLMCompleter;
import com.player2.playerengine.player2api.OwnerStopIntent;
import com.player2.playerengine.player2api.OwnerStopTargetResolution;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.RemoteOwnerCommandGate;
import com.player2.playerengine.player2api.UserBlacklistPolicy;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.status.StatusUtils;
import com.player2.playerengine.player2api.utils.SttLogging;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.ChatEvent.Received;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConversationManager {
   public static final Logger LOGGER = LogManager.getLogger();
   public static ConcurrentHashMap<UUID, AgentConversationData> queueData = new ConcurrentHashMap<>();
   public static final float messagePassingMaxDistance = 64.0F;
   private static boolean hasInit = false;
   private static final ConcurrentHashMap<String, LLMCompleter> llmCompletersByBillingKey = new ConcurrentHashMap<>();
   private static final CopyOnWriteArrayList<LLMCompleter> extraLLMCompleters = new CopyOnWriteArrayList<>();

   public static void init() {
      if (!hasInit) {
         hasInit = true;
         ChatEvent.RECEIVED.register((Received)(player, component) -> {
            String message = component.plainCopy().getString();
            String sender = player.getName().getString();
            onUserChatMessage(new Event.UserMessage(message, sender, false, player.getUUID()));
            return EventResult.pass();
         });
      }
   }

   private static LLMCompleter getOrCreateCompleterForBillingKey(String billingKey) {
      return llmCompletersByBillingKey.computeIfAbsent(billingKey, k -> {
         LOGGER.info("ConversationManager: creating LLMCompleter bucket for billingKey={}", k);
         return new LLMCompleter();
      });
   }

   public static void registerLLMCompleter(LLMCompleter completer) {
      if (completer != null && !extraLLMCompleters.contains(completer)) {
         extraLLMCompleters.add(completer);
      }
   }

   public static void unregisterLLMCompleter(LLMCompleter completer) {
      if (completer != null) {
         extraLLMCompleters.remove(completer);
      }
   }

   public static void shutdownAndResetLLMCompleters() {
      for (LLMCompleter c : new ArrayList<>(llmCompletersByBillingKey.values())) {
         c.shutdown();
      }

      llmCompletersByBillingKey.clear();

      for (LLMCompleter c : new ArrayList<>(extraLLMCompleters)) {
         c.shutdown();
      }

      extraLLMCompleters.clear();
   }

   public static void shutdownCompleterForBillingKey(String billingKey) {
      if (billingKey != null) {
         LLMCompleter removed = llmCompletersByBillingKey.remove(billingKey);
         if (removed != null) {
            LOGGER.info("ConversationManager: shutting down LLMCompleter bucket for billingKey={}", billingKey);
            removed.shutdown();
         }
      }
   }

   public static AgentConversationData getOrCreateEventQueueData(PlayerEngineController mod) {
      return queueData.computeIfAbsent(mod.getPlayer().getUUID(), k -> {
         LOGGER.info("EventQueueManager/getOrCreateEventQueueData: creating new queue data for entId={}", mod.getPlayer().getStringUUID());
         return new AgentConversationData(mod);
      });
   }

   private static Stream<AgentConversationData> filterQueueData(Predicate<AgentConversationData> pred) {
      return queueData.values().stream().filter(pred);
   }

   private static Stream<AgentConversationData> getCloseDataByUUID(UUID sender) {
      return filterQueueData(data -> data.getDistance(sender) < 64.0F);
   }

   private static void maybeNotifyUserBlacklistCallByName(
      MinecraftServer server, String speakerName, HashSet<UUID> notifiedBotOwnerUuids, AgentConversationData blockedTarget
   ) {
      if (server != null && speakerName != null && !speakerName.isBlank()) {
         Player owner = blockedTarget.getMod().getOwner();
         if (owner != null) {
            UUID ownerUuid = owner.getUUID();
            if (notifiedBotOwnerUuids.add(ownerUuid)) {
               for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                  if (sp.getGameProfile().getName().equalsIgnoreCase(speakerName.trim())) {
                     sp.sendSystemMessage(Component.translatable("message.playerengine.blacklist.user_blocked"));
                     return;
                  }
               }
            }
         }
      }
   }

   public static void onUserChatMessage(Event.UserMessage msg) {
      LOGGER.info("User message event={}", msg);
      if (!handleAuthenticatedOwnerStop(msg)) {
         boolean callByName = Player2ServerConfigHolder.get().isCallByNameChat();
         List<AgentConversationData> nearby = filterQueueData(d -> RemoteOwnerCommandGate.include(isCloseToPlayer(d, msg.userName()), msg, d))
            .collect(Collectors.toList());
         if (nearby.isEmpty()) {
            logMessageNotDelivered(msg, callByName, "no_nearby_companion", nearby, null);
         } else {
            MinecraftServer server = nearby.stream().map(d -> d.getMod().getPlayer().getServer()).filter(Objects::nonNull).findFirst().orElse(null);
            if (!callByName) {
               int queued = 0;

               for (AgentConversationData data : nearby) {
                  if (server != null && BotBlacklistPolicy.isBlocked(server, msg.userName(), data)) {
                     logMessageBlocked(msg, data.getName(), "bot_blacklist");
                  } else if (server != null && UserBlacklistPolicy.isBlocked(server, msg.userName(), data)) {
                     logMessageBlocked(msg, data.getName(), "user_blacklist");
                  } else {
                     data.onEvent(msg);
                     queued++;
                  }
               }

               if (queued == 0) {
                  logMessageNotDelivered(msg, false, "all_nearby_blocked", nearby, null);
               }
            } else {
               CallByNameMentionRouter.ResolvedTargets resolved = CallByNameMentionRouter.resolveTargets(msg, msg.userName(), nearby);
               if (resolved != null && resolved.targets() != null && !resolved.targets().isEmpty()) {
                  if (resolved.cleanedMessage() == null) {
                     logMessageNotDelivered(msg, true, "call_by_name_cleaned_message_null", nearby, resolved);
                  } else {
                     HashSet<UUID> userBlacklistNotifiedOwners = new HashSet<>();
                     int queued = 0;

                     for (AgentConversationData data : resolved.targets()) {
                        if (server != null && BotBlacklistPolicy.isBlocked(server, msg.userName(), data)) {
                           logMessageBlocked(msg, data.getName(), "bot_blacklist");
                        } else if (server != null && UserBlacklistPolicy.isBlocked(server, msg.userName(), data)) {
                           maybeNotifyUserBlacklistCallByName(server, msg.userName(), userBlacklistNotifiedOwners, data);
                           logMessageBlocked(msg, data.getName(), "user_blacklist");
                        } else {
                           data.onEvent(resolved.cleanedMessage());
                           queued++;
                        }
                     }

                     if (queued == 0) {
                        logMessageNotDelivered(msg, true, "call_by_name_all_targets_blocked", nearby, resolved);
                     }
                  }
               } else {
                  logMessageNotDelivered(msg, true, "call_by_name_no_mention", nearby, resolved);
               }
            }
         }
      }
   }

   private static boolean handleAuthenticatedOwnerStop(Event.UserMessage msg) {
      UUID ownerUuid = msg == null ? null : msg.authenticatedUserUuid();
      if (ownerUuid != null && msg.message() != null) {
         HashSet<ConversationManager.StableCompanionKey> matchingKeys = new HashSet<>();

         for (AgentConversationData data : queueData.values()) {
            Character character = characterForOwner(data, ownerUuid);
            ConversationManager.StableCompanionKey key = stableKey(ownerUuid, character);
            if (key != null && OwnerStopIntent.matches(msg.message(), character)) {
               matchingKeys.add(key);
            }
         }

         OwnerStopTargetResolution.Resolution resolution = OwnerStopTargetResolution.resolve(
            matchingKeys.stream().map(ConversationManager.StableCompanionKey::characterId).toList()
         );
         if (resolution.kind() == OwnerStopTargetResolution.Kind.NONE) {
            return false;
         }

         if (resolution.kind() == OwnerStopTargetResolution.Kind.AMBIGUOUS) {
            MinecraftServer server = null;

            for (AgentConversationData data : queueData.values()) {
               ConversationManager.StableCompanionKey key = stableKeyForOwner(data, ownerUuid);
               if (key != null && matchingKeys.contains(key)) {
                  data.deferInfo(
                     new Event.InfoMessage(
                        "The owner's immediate stop request matched multiple companions with the same name, so no companion was stopped. Ask for an unambiguous companion name."
                     )
                  );
                  if (server == null && data.getMod().getPlayer() != null) {
                     server = data.getMod().getPlayer().getServer();
                  }
               }
            }

            notifyAuthenticatedOwner(ownerUuid, server, Component.translatable("message.playerengine.agent.owner_stop_ambiguous"));
            LOGGER.warn("Authenticated owner stop was ambiguous across {} stable character ids; cancelled none", matchingKeys.size());
            return true;
         } else {
            ConversationManager.StableCompanionKey targetKey = new ConversationManager.StableCompanionKey(ownerUuid, resolution.characterId());
            List<AgentConversationData> targets = new ArrayList<>();

            for (AgentConversationData data : queueData.values()) {
               if (targetKey.equals(stableKeyForOwner(data, ownerUuid))) {
                  targets.add(data);
               }
            }

            if (targets.isEmpty()) {
               return false;
            }

            MinecraftServer server = null;
            String displayName = targets.get(0).getName();
            boolean requestStillDraining = false;

            for (AgentConversationData data : targets) {
               requestStillDraining |= data.cancelPendingModelActionsForOperatorStop();
               data.getMod().isStopping = true;
               data.getMod().stop();
               if (server == null && data.getMod().getPlayer() != null) {
                  server = data.getMod().getPlayer().getServer();
               }
            }

            notifyAuthenticatedOwner(
               ownerUuid,
               server,
               requestStillDraining
                  ? Component.translatable("message.playerengine.agent.owner_stop_ack_delayed", new Object[]{displayName})
                  : Component.translatable("message.playerengine.agent.owner_stop_ack", new Object[]{displayName})
            );
            LOGGER.info("Handled authenticated owner stop for stable character id={} controllers={}", targetKey.characterId(), targets.size());
            return true;
         }
      } else {
         return false;
      }
   }

   private static ConversationManager.StableCompanionKey stableKeyForOwner(AgentConversationData data, UUID ownerUuid) {
      return stableKey(ownerUuid, characterForOwner(data, ownerUuid));
   }

   private static Character characterForOwner(AgentConversationData data, UUID ownerUuid) {
      if (data != null && ownerUuid != null) {
         try {
            return data.isOwner(ownerUuid) ? data.getCharacter() : null;
         } catch (RuntimeException staleData) {
            LOGGER.warn("Skipping stale companion while resolving authenticated owner stop: type={}", staleData.getClass().getSimpleName());
            return null;
         }
      } else {
         return null;
      }
   }

   private static ConversationManager.StableCompanionKey stableKey(UUID ownerUuid, Character character) {
      return character != null && character.id() != null && !character.id().isBlank()
         ? new ConversationManager.StableCompanionKey(ownerUuid, character.id())
         : null;
   }

   private static void notifyAuthenticatedOwner(UUID ownerUuid, MinecraftServer server, Component message) {
      if (ownerUuid != null && server != null && message != null) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ownerUuid.equals(player.getUUID())) {
               AgentSideEffects.broadcastChatToPlayer(server, message, player);
               return;
            }
         }
      }
   }

   private static void logMessageNotDelivered(
      Event.UserMessage msg, boolean callByName, String reason, List<AgentConversationData> nearby, CallByNameMentionRouter.ResolvedTargets resolved
   ) {
      String nearbyNames = nearby.stream().map(AgentConversationData::getName).collect(Collectors.joining(", "));
      String targetNames = resolved != null && resolved.targets() != null
         ? resolved.targets().stream().map(AgentConversationData::getName).collect(Collectors.joining(", "))
         : "";
      if (msg.fromVoice()) {
         LOGGER.warn(
            "STT/voice: message not delivered to companion (reason={}, callByName={}, user={}, nearby=[{}], targets=[{}], preview=\"{}\"). If callByName is enabled, include the companion name (e.g. \"Lina, ...\") in speech.",
            reason,
            callByName,
            msg.userName(),
            nearbyNames,
            targetNames,
            SttLogging.messagePreview(msg.message())
         );
      } else {
         LOGGER.warn(
            "Chat: message not delivered to companion (reason={}, callByName={}, user={}, nearby=[{}], targets=[{}], preview=\"{}\")",
            reason,
            callByName,
            msg.userName(),
            nearbyNames,
            targetNames,
            SttLogging.messagePreview(msg.message())
         );
      }
   }

   private static void logMessageBlocked(Event.UserMessage msg, String companionName, String blockReason) {
      if (msg.fromVoice()) {
         LOGGER.warn(
            "STT/voice: message blocked for companion={} (reason={}, user={}, preview=\"{}\")",
            companionName,
            blockReason,
            msg.userName(),
            SttLogging.messagePreview(msg.message())
         );
      } else {
         LOGGER.debug("Chat: message blocked for companion={} (reason={}, user={})", companionName, blockReason, msg.userName());
      }
   }

   public static void onAICharacterMessage(Event.CharacterMessage msg, UUID senderId) {
      UUID sendingUUID = msg.sendingCharacterData().getUUID();
      MinecraftServer server = msg.sendingCharacterData().getMod().getPlayer().getServer();
      String initiator = msg.originatingUserName();
      getCloseDataByUUID(sendingUUID)
         .filter(data -> !data.getUUID().equals(senderId))
         .filter(
            data -> initiator == null
               || initiator.isBlank()
               || server == null
               || !BotBlacklistPolicy.isBlocked(server, initiator, data) && !UserBlacklistPolicy.isBlocked(server, initiator, data)
         )
         .forEach(data -> {
            LOGGER.info("onCharMsg/ msg={}, sender={}, running onCharMsg for ={}", msg.message(), senderId, data.getName());
            data.onAICharacterMessage(msg);
         });
   }

   private static void process(Consumer<Event.CharacterMessage> onCharacterEvent, BiConsumer<String, ServerPlayer> onErrEvent) {
      Map<String, AgentConversationData> bestPerBucket = new HashMap<>();

      for (AgentConversationData data : queueData.values()) {
         if (data.getPriority() != 0L
            && data.getEntity() != null
            && data.getEntity().isAlive()
            && !data.getEntity().isRemoved()
            && data.getMod().getOwner() != null) {
            Player2PayerResolution.ApiBillingContext billing = data.previewBilling();
            String billingKey = billing != null ? billing.billingKey() : null;
            if (billingKey == null) {
               billingKey = "__no_billing__:" + data.getUUID();
            }

            AgentConversationData current = bestPerBucket.get(billingKey);
            if (current == null || data.getPriority() > current.getPriority()) {
               bestPerBucket.put(billingKey, data);
            }
         }
      }

      for (Entry<String, AgentConversationData> entry : bestPerBucket.entrySet()) {
         String billingKey = entry.getKey();
         AgentConversationData data = entry.getValue();
         LLMCompleter completer = getOrCreateCompleterForBillingKey(billingKey);
         if (completer.isAvailible()) {
            Player owner = data.getMod().getOwner();
            MinecraftServer srv = owner != null ? owner.getServer() : null;
            ServerPlayer ownerServerPlayer = srv != null ? srv.getPlayerList().getPlayer(owner.getUUID()) : null;
            data.process(onCharacterEvent, errMsg -> onErrEvent.accept(errMsg, ownerServerPlayer), completer);
         }
      }
   }

   public static void injectOnTick(MinecraftServer server) {
      queueData.forEach((k, v) -> {
         if (v.getMod().getPlayer().getServer() != server) {
            despwnCompanion(k);
         }
      });
      Consumer<Event.CharacterMessage> onCharacterEvent = data -> AgentSideEffects.onEntityMessage(server, data);
      BiConsumer<String, ServerPlayer> onErrEvent = (errMsg, player) -> AgentSideEffects.onError(server, errMsg, player);
      process(onCharacterEvent, onErrEvent);
   }

   public static void sendGreeting(PlayerEngineController mod, Character character) {
      LOGGER.info("Sending greeting character={}", character);
      AgentConversationData data = getOrCreateEventQueueData(mod);
      data.onGreeting();
   }

   public static void sendReturnMessage(PlayerEngineController mod, Character character, String ownerName) {
      LOGGER.info("Sending return message character={} owner={}", character, ownerName);
      AgentConversationData data = getOrCreateEventQueueData(mod);
      data.onReturn(ownerName);
   }

   public static void sendDeathRevival(PlayerEngineController mod, Character character, String deathCause) {
      LOGGER.info("Sending death revival character={} cause={}", character, deathCause);
      AgentConversationData data = getOrCreateEventQueueData(mod);
      data.onDeathRevival(deathCause);
   }

   public static void resetMemory(PlayerEngineController mod) {
      mod.getAIPersistantData().clearHistory();
   }

   private static boolean isCloseToPlayer(AgentConversationData data, String userName) {
      LOGGER.info("Passing msg btw {} <-> {}", data.getName(), userName);
      return StatusUtils.getDistanceToUsername(data.getMod(), userName) < 64.0F;
   }

   public static void syncQueueData(Collection<UUID> validUuids) {
      queueData.keySet().retainAll(validUuids);
   }

   public static void despwnCompanion(UUID id) {
      queueData.remove(id);
   }

   public static Collection<AgentConversationData> getDataByOwner(UUID ownerId) {
      return ownerId == null ? Collections.emptyList() : queueData.values().stream().filter(data -> {
         if (data != null && data.getMod() != null) {
            Player owner = data.getMod().getOwner();
            if (owner == null) {
               return false;
            }

            LOGGER.info("getDataByOwner: ownerId={}, test={}", ownerId, owner.getUUID());
            return owner != null && ownerId.equals(owner.getUUID());
         } else {
            return false;
         }
      }).collect(Collectors.toList());
   }

   public static ConversationManager.QueueClearSummary clearPendingWork() {
      int queuesCleared = 0;

      for (AgentConversationData data : queueData.values()) {
         data.resetForClear();
         queuesCleared++;
      }

      int bucketsShutdown = llmCompletersByBillingKey.size();

      for (LLMCompleter c : new ArrayList<>(llmCompletersByBillingKey.values())) {
         c.shutdown();
      }

      llmCompletersByBillingKey.clear();
      LOGGER.info("ConversationManager.clearPendingWork: queuesCleared={} bucketsShutdown={}", queuesCleared, bucketsShutdown);
      return new ConversationManager.QueueClearSummary(queuesCleared, bucketsShutdown);
   }

   public static ConversationManager.QueueClearSummary clearPendingWorkFor(UUID ownerUuid) {
      if (ownerUuid == null) {
         return new ConversationManager.QueueClearSummary(0, 0);
      }

      int queuesCleared = 0;
      HashSet<String> seenBillingKeys = new HashSet<>();

      for (AgentConversationData data : queueData.values()) {
         Player owner = data.getMod() != null ? data.getMod().getOwner() : null;
         if (owner != null && ownerUuid.equals(owner.getUUID())) {
            try {
               Player2PayerResolution.ApiBillingContext billing = data.previewBilling();
               String billingKey = billing != null ? billing.billingKey() : null;
               if (billingKey != null) {
                  seenBillingKeys.add(billingKey);
               }
            } catch (Exception e) {
               LOGGER.warn("clearPendingWorkFor: previewBilling threw for owner={}, msg={}", ownerUuid, e.getMessage());
            }

            data.resetForClear();
            queuesCleared++;
         }
      }

      int bucketsShutdown = 0;

      for (String billingKey : seenBillingKeys) {
         LLMCompleter removed = llmCompletersByBillingKey.remove(billingKey);
         if (removed != null) {
            removed.shutdown();
            bucketsShutdown++;
         }
      }

      LOGGER.info("ConversationManager.clearPendingWorkFor owner={}: queuesCleared={} bucketsShutdown={}", ownerUuid, queuesCleared, bucketsShutdown);
      return new ConversationManager.QueueClearSummary(queuesCleared, bucketsShutdown);
   }

   public record QueueClearSummary(int queuesCleared, int bucketsShutdown) {
   }

   private record StableCompanionKey(UUID ownerUuid, String characterId) {
   }
}
