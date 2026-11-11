package com.player2.playerengine;

import com.player2.playerengine.automaton.KeepName;
import com.player2.playerengine.automaton.command.defaults.DefaultCommands;
import com.player2.playerengine.automaton.entity.CustomFishingBobberEntity;
import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.AgentSideEffects;
import com.player2.playerengine.player2api.Event;
import com.player2.playerengine.player2api.MarkerParser;
import com.player2.playerengine.player2api.Player2ClientApiBridge;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.auth.TokenStorage;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.manager.TTSManager;
import com.player2.playerengine.player2api.network.Player2DisconnectHandler;
import com.player2.playerengine.player2api.network.Player2ServerNetworking;
import com.player2.playerengine.player2api.network.TtsClientPreferenceStore;
import com.player2.playerengine.player2api.utils.SttLogging;
import com.player2.playerengine.retrieval.RagIndex;
import com.player2.playerengine.structureprotection.StructureProtectionEvents;
import com.player2.playerengine.util.ExecutorShutdown;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.PlayerEvent.PlayerJoin;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import dev.architectury.platform.Platform;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.utils.Env;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@KeepName
public final class PlayerEngine {
   public static final Logger LOGGER = LogManager.getLogger("PlayerEngine");
   public static final String MOD_ID = "playerengine";
   public static final String MOD_NAME = "PlayerEngine";
   public static final ResourceLocation CLIENT_PLAYER2_PROXY_REQUEST_PACKET_ID = ResourceLocation.fromNamespaceAndPath("playerengine", "client_player2_proxy_request");
   public static final ResourceLocation CLIENT_PLAYER2_PROXY_RESPONSE_PACKET_ID = ResourceLocation.fromNamespaceAndPath("playerengine", "client_player2_proxy_response");
   public static final ResourceLocation TTS_PREFERENCE_PACKET_ID = ResourceLocation.fromNamespaceAndPath("playerengine", "tts_preference");
   public static final ResourceLocation TTS_SEGMENT_DONE_PACKET_ID = id("segment_done");
   public static final ResourceLocation TTS_MESSAGE_DONE_PACKET_ID = id("message_done");
   public static final TagKey<Item> EMPTY_BUCKETS = TagKey.create(Registries.ITEM, id("empty_buckets"));
   public static final TagKey<Item> WATER_BUCKETS = TagKey.create(Registries.ITEM, id("water_buckets"));
   private static ThreadPoolExecutor threadPool = newWorkerPool();
   private static final AtomicBoolean backgroundExecutorsShutDown = new AtomicBoolean(false);
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create("playerengine", Registries.ENTITY_TYPE);
   public static RegistrySupplier<EntityType<CustomFishingBobberEntity>> FISHING_BOBBER = ENTITY_TYPES.register(
      "custom_fishing_bobber",
      () -> Builder.<CustomFishingBobberEntity>of(CustomFishingBobberEntity::new, MobCategory.CREATURE)
         .sized(EntityType.FISHING_BOBBER.getWidth(), EntityType.FISHING_BOBBER.getHeight())
         .clientTrackingRange(64)
         .updateInterval(1)
         .build("custom_fishing_bobber")
   );
   private static final Map<UUID, PlayerEngine.TtsFallbackState> TTS_FALLBACK_TIMERS = new ConcurrentHashMap<>();

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("playerengine", path);
   }

   public static ThreadPoolExecutor getExecutor() {
      return threadPool;
   }

   public static void resetBackgroundExecutorsShutdownGate() {
      backgroundExecutorsShutDown.set(false);
      if (threadPool.isShutdown()) {
         threadPool = newWorkerPool();
      }
   }

   private static ThreadPoolExecutor newWorkerPool() {
      AtomicInteger threadCounter = new AtomicInteger(0);
      return new ThreadPoolExecutor(
         4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "PlayerEngine Worker " + threadCounter.incrementAndGet())
      );
   }

   public static void shutdownBackgroundExecutors() {
      if (backgroundExecutorsShutDown.compareAndSet(false, true)) {
         ConversationManager.shutdownAndResetLLMCompleters();
         TTSManager.shutdownAndReset();
         ExecutorShutdown.shutdownNowAwait("PlayerEngine.workerPool", threadPool);
         AuthenticationManager.shutdownAndReset();
      }
   }

   public static void onInitialize() {
      Player2ServerConfigHolder.load();
      PlayerEvent.PLAYER_QUIT.register(Player2DisconnectHandler::onPlayerQuit);
      PlayerEvent.PLAYER_JOIN.register((PlayerJoin)player -> {
         if (player instanceof ServerPlayer sp) {
            Player2ServerNetworking.sendConfigSync(sp);
         }
      });
      StructureProtectionEvents.register();
      DefaultCommands.registerAll();
      ENTITY_TYPES.register();
      PlayerEngineStorageMigration.runOnce();
      copyToolOverridesReadmeIfAbsent();
      RagIndex.initialize();
      MCCommands.onInit();
      TickEvent.SERVER_POST.register(PlayerEngineController::staticServerTick);
      TickEvent.SERVER_POST.register(PlayerEngine::tickTtsFallbackTimers);
      ConversationManager.init();
      ModIntelligenceService.registerEventHandlers();
      if (Platform.getEnvironment() == Env.SERVER) {
         NetworkManager.registerS2CPayloadType(ResourceLocation.fromNamespaceAndPath("playerengine", "stream_tts"));
         NetworkManager.registerS2CPayloadType(ResourceLocation.fromNamespaceAndPath("playerengine", "response_stt"));
         NetworkManager.registerS2CPayloadType(CLIENT_PLAYER2_PROXY_REQUEST_PACKET_ID);
         NetworkManager.registerS2CPayloadType(Player2ServerNetworking.SYNC_SERVER_PLAYER2);
      }

      NetworkManager.registerReceiver(
         Side.C2S,
         CLIENT_PLAYER2_PROXY_RESPONSE_PACKET_ID,
         (buf, context) -> Player2ClientApiBridge.handleClientProxyResponse(buf, (ServerPlayer)context.getPlayer())
      );
      NetworkManager.registerReceiver(
         Side.C2S,
         ResourceLocation.fromNamespaceAndPath("playerengine", "user_message"),
         (buf, context) -> {
            String username = context.getPlayer().getName().getString();
            String message = buf.readUtf();
            LOGGER.info(
               "Server: received user_message packet (voice/STT) from {} len={} preview=\"{}\"", username, message.length(), SttLogging.messagePreview(message)
            );
            ConversationManager.onUserChatMessage(new Event.UserMessage(message, username, true, context.getPlayer().getUUID()));
            AgentSideEffects.broadcastChatToAllPlayers(
               context.getPlayer().getServer(),
               Component.translatable("message.playerengine.chat.player_message", new Object[]{context.getPlayer().getName().getString(), message})
            );
         }
      );
      NetworkManager.registerReceiver(
         Side.C2S,
         ResourceLocation.fromNamespaceAndPath("playerengine", "request_stt"),
         (buf, context) -> {
            String clientId = buf.readUtf();
            String username = context.getPlayer().getName().getString();
            LOGGER.info("Server: received request_stt packet from {} clientId={}", username, clientId);
            String storedToken = TokenStorage.getToken(username, clientId);
            if (storedToken == null || storedToken.isBlank()) {
               LOGGER.warn("Server: no stored STT token for user={} clientId={} (Player2 login may be required)", username, clientId);
            }

            RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getPlayer().registryAccess());
            buf2.writeUtf(storedToken == null ? "" : storedToken);
            LOGGER.info("Server: sending response_stt packet to {} (tokenPresent={})", username, storedToken != null && !storedToken.isBlank());
            ((ServerPlayer)context.getPlayer())
               .connection
               .send(NetworkManager.toPacket(Side.S2C, ResourceLocation.fromNamespaceAndPath("playerengine", "response_stt"), buf2));
         }
      );
      NetworkManager.registerReceiver(Side.C2S, TTS_PREFERENCE_PACKET_ID, (buf, context) -> {
         boolean enabled = buf.readBoolean();
         TtsClientPreferenceStore.setTtsEnabled(context.getPlayer().getUUID(), enabled);
      });
      NetworkManager.registerReceiver(Side.C2S, TTS_SEGMENT_DONE_PACKET_ID, (buf, context) -> {
         ServerPlayer sender = (ServerPlayer)context.getPlayer();
         String botUuidStr = buf.readUtf();
         int segIndex = buf.readVarInt();

         UUID botUuid;
         try {
            botUuid = UUID.fromString(botUuidStr);
         } catch (IllegalArgumentException e) {
            LOGGER.warn("PlayerEngine: segment_done invalid bot UUID: {}", botUuidStr);
            return;
         }

         AgentConversationData botData = ConversationManager.queueData.get(botUuid);
         if (botData != null) {
            ServerPlayer prompter = resolvePrompter(botData);
            if (prompter != null && prompter.getUUID().equals(sender.getUUID())) {
               fireSegment(botUuid, botData, segIndex, false);
            }
         }
      });
      NetworkManager.registerReceiver(
         Side.C2S,
         TTS_MESSAGE_DONE_PACKET_ID,
         (buf, context) -> {
            if (Player2ServerConfigHolder.get().isBotTtsPlaybackAckEnabled()) {
               ServerPlayer sender = (ServerPlayer)context.getPlayer();
               String botUuidStr = buf.readUtf();
               boolean degraded = buf.readBoolean();

               UUID botUuid;
               try {
                  botUuid = UUID.fromString(botUuidStr);
               } catch (IllegalArgumentException e) {
                  LOGGER.warn("PlayerEngine: message_done invalid bot UUID: {}", botUuidStr);
                  return;
               }

               AgentConversationData botData = ConversationManager.queueData.get(botUuid);
               if (botData != null) {
                  ServerPlayer prompter = resolvePrompter(botData);
                  if (prompter != null && prompter.getUUID().equals(sender.getUUID())) {
                     LOGGER.info(
                        "PlayerEngine: message_done ACK for bot={} from prompter={} degraded={}", botData.getName(), sender.getName().getString(), degraded
                     );
                     botData.clearTtsCooldown();
                     botData.cancelFallbackTimer();
                     if (degraded) {
                        MinecraftServer server = sender.getServer();
                        if (server != null) {
                           AgentSideEffects.broadcastChatToAllPlayers(
                              server, Component.translatable("message.playerengine.agent.partial_speech", new Object[]{botData.getName()})
                           );
                        }

                        botData.reportPartialSpeechToModel();
                     }
                  }
               }
            }
         }
      );
   }

   private static ServerPlayer resolvePrompter(AgentConversationData botData) {
      String prompterName = botData.getChainInitiatorUsername();
      if (prompterName != null && !prompterName.isBlank()) {
         LivingEntity bot = botData.getMod().getPlayer();
         MinecraftServer server = bot != null ? bot.getServer() : null;
         return server == null ? null : Player2PayerResolution.findByName(server, prompterName);
      } else {
         return null;
      }
   }

   public static void scheduleTtsFallbackTimer(MinecraftServer server, UUID botUuid, int messageLength) {
      int estimateSec = (int)Math.ceil(messageLength / 25.0) + 1;
      int slackSec = 5;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos((long)estimateSec + slackSec);
      TTS_FALLBACK_TIMERS.put(botUuid, new PlayerEngine.TtsFallbackState(deadline));
      AgentConversationData botData = ConversationManager.queueData.get(botUuid);
      if (botData != null) {
         botData.setFallbackTimerCancel(() -> clearFallbackTimer(botUuid));
      }
   }

   public static void clearFallbackTimer(UUID botUuid) {
      PlayerEngine.TtsFallbackState state = TTS_FALLBACK_TIMERS.get(botUuid);
      if (state != null) {
         state.active = false;
         state.cleared = true;
      }
   }

   public static void tickTtsFallbackTimers(MinecraftServer server) {
      if (!TTS_FALLBACK_TIMERS.isEmpty()) {
         long now = System.nanoTime();

         for (Entry<UUID, PlayerEngine.TtsFallbackState> e : TTS_FALLBACK_TIMERS.entrySet()) {
            PlayerEngine.TtsFallbackState state = e.getValue();
            if (now >= state.deadlineNanos) {
               UUID botUuid = e.getKey();
               if (state.active) {
                  AgentConversationData botData = ConversationManager.queueData.get(botUuid);
                  if (botData != null) {
                     List<MarkerParser.SegmentBoundary> boundaries = botData.getPendingSegmentActions();

                     for (int i = 0; i < boundaries.size(); i++) {
                        fireSegment(botUuid, botData, i, true);
                     }

                     if (!state.cleared) {
                        state.cleared = true;
                        botData.clearTtsCooldown();
                     }

                     LOGGER.info("PlayerEngine: TTS fallback timer fired for bot={} (no prompter ACK)", botData.getName());
                  }
               }

               TTS_FALLBACK_TIMERS.remove(botUuid);
            }
         }
      }
   }

   private static void fireSegment(UUID botUuid, AgentConversationData botData, int segIndex, boolean fromTimer) {
      List<MarkerParser.SegmentBoundary> boundaries = botData.getPendingSegmentActions();
      if (segIndex >= 0 && segIndex < boundaries.size()) {
         PlayerEngine.TtsFallbackState state = TTS_FALLBACK_TIMERS.computeIfAbsent(botUuid, k -> {
            PlayerEngine.TtsFallbackState s = new PlayerEngine.TtsFallbackState(System.nanoTime());
            s.active = false;
            s.cleared = true;
            return s;
         });
         if (state.firedSegments.add(segIndex)) {
            MarkerParser.SegmentBoundary b = boundaries.get(segIndex);
            String action = b.action().name().toLowerCase(Locale.ROOT);
            LOGGER.info("PlayerEngine: firing bodylang segment bot={} seg={} action={} fromTimer={}", botData.getName(), segIndex, action, fromTimer);
            AgentSideEffects.onCommandListGenerated(botData.getMod(), "bodylang " + action, botData::onCommandFinish);
         }
      }
   }

   private static void copyToolOverridesReadmeIfAbsent() {
      try {
         Path dest = PlayerEnginePaths.userFile("tool_overrides.README.md");
         if (!Files.exists(dest)) {
            Files.createDirectories(dest.getParent());

            try (InputStream in = PlayerEngine.class.getClassLoader().getResourceAsStream("tool_overrides.README.md")) {
               if (in != null) {
                  Files.copy(in, dest);
                  LOGGER.info("PlayerEngine: copied tool_overrides.README.md to {}", dest);
               }
            }
         }
      } catch (IOException e) {
         LOGGER.warn("PlayerEngine: could not copy tool_overrides.README.md: {}", e.getMessage());
      }
   }

   private static final class TtsFallbackState {
      final long deadlineNanos;
      final Set<Integer> firedSegments = ConcurrentHashMap.newKeySet();
      volatile boolean cleared = false;
      volatile boolean active = true;

      TtsFallbackState(long deadlineNanos) {
         this.deadlineNanos = deadlineNanos;
      }
   }
}
