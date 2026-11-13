package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.player2api.SetupDirectRoute;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Resumes owner-follow after Minecraft moves the same companion UUID through a portal. */
public final class OwnerFollowTransfer {
   private static final Map<UUID, Transfer> pending = new ConcurrentHashMap<>();
   private record Transfer(MinecraftServer server, UUID owner, FollowPlayerTask source,
      ResourceKey<Level> destination, int started) {}

   private OwnerFollowTransfer() {}

   static boolean netherCrossing(ResourceKey<Level> from, ResourceKey<Level> to) {
      return from.equals(Level.OVERWORLD) && to.equals(Level.NETHER)
         || from.equals(Level.NETHER) && to.equals(Level.OVERWORLD);
   }

   static boolean inPortal(ServerLevel world, BlockPos feet) {
      return world.getBlockState(feet).is(Blocks.NETHER_PORTAL) || world.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL);
   }

   static BlockPos findPortal(ServerLevel world, BlockPos lastOwnerPosition) {
      BlockPos nearest = null;
      double distance = Double.POSITIVE_INFINITY;
      for (BlockPos pos : BlockPos.betweenClosed(lastOwnerPosition.offset(-8, -4, -8), lastOwnerPosition.offset(8, 4, 8))) {
         double candidate = pos.distSqr(lastOwnerPosition);
         if (candidate < distance && world.hasChunkAt(pos) && world.getBlockState(pos).is(Blocks.NETHER_PORTAL)
            && !world.getBlockState(pos.below()).is(Blocks.NETHER_PORTAL)) {
            nearest = pos.immutable();
            distance = candidate;
         }
      }
      return nearest;
   }

   static void request(PlayerEngineController controller, ServerPlayer owner, FollowPlayerTask source) {
      UUID uuid = controller.getEntity().getUUID();
      MinecraftServer server = owner.serverLevel().getServer();
      Transfer next = new Transfer(server, owner.getUUID(), source, owner.level().dimension(), server.getTickCount());
      if (pending.putIfAbsent(uuid, next) == null) {
         ObservatoryTelemetry.event("FOLLOW_DIMENSION_PORTAL", uuid + " -> " + next.destination().location());
      }
   }

   public static void cancel(PlayerEngineController controller) {
      pending.remove(controller.getEntity().getUUID());
   }

   public static void tick(MinecraftServer server) {
      for (var entry : pending.entrySet()) {
         UUID uuid = entry.getKey();
         Transfer transfer = entry.getValue();
         if (transfer.server() != server) continue;
         ServerPlayer owner = server.getPlayerList().getPlayer(transfer.owner());
         if (owner == null || !owner.level().dimension().equals(transfer.destination())) {
            pending.remove(uuid, transfer);
            continue;
         }
         if (server.getTickCount() - transfer.started() >= 600) {
            if (pending.remove(uuid, transfer)) {
               owner.sendSystemMessage(Component.literal("Ellie could not finish following you through the portal."));
               ObservatoryTelemetry.event("FOLLOW_DIMENSION_TIMEOUT", uuid.toString());
            }
            continue;
         }
         PlayerEngineController companion = PlayerEngineController.staticControllers.get(uuid);
         if (companion == null) continue;
         if (companion.getWorld() != owner.serverLevel()) {
            if (companion.getUserTaskChain().getCurrentTask() != transfer.source()) pending.remove(uuid, transfer);
            continue;
         }
         if (companion.getOwner() != null && companion.isOwner(transfer.owner()) && companion.getEntity().isAlive()
            && owner.serverLevel().getEntity(uuid) == companion.getEntity()
            && !inPortal(owner.serverLevel(), owner.blockPosition()) && pending.remove(uuid, transfer)
            && !companion.hasActiveNonIdleUserTask()) {
            SetupDirectRoute.tryRoutePlayerCommand(companion, transfer.owner(), "follow");
            ObservatoryTelemetry.event("FOLLOW_DIMENSION_RESUMED", uuid.toString());
         }
      }
   }

   public static void clear(MinecraftServer server) {
      pending.entrySet().removeIf(entry -> entry.getValue().server() == server);
   }
}
