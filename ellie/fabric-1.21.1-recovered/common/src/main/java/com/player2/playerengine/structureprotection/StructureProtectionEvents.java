package com.player2.playerengine.structureprotection;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.BlockEvent.Break;
import dev.architectury.event.events.common.BlockEvent.Place;
import net.minecraft.server.level.ServerPlayer;

public final class StructureProtectionEvents {
   private StructureProtectionEvents() {
   }

   private static boolean enabled() {
      return BaritoneAPI.getGlobalSettings().respectStructuresEnabled.get();
   }

   public static void register() {
      BlockEvent.PLACE.register((Place)(level, pos, state, placer) -> {
         if (!enabled()) {
            return EventResult.pass();
         }

         if (level.isClientSide()) {
            return EventResult.pass();
         }

         if (placer instanceof ServerPlayer sp) {
            if (PlayerEngineController.staticControllers.containsKey(sp.getUUID())) {
               return EventResult.pass();
            }

            PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
            if (store != null) {
               store.add(level.dimension().location().toString(), pos);
            }

            return EventResult.pass();
         } else {
            return EventResult.pass();
         }
      });
      BlockEvent.BREAK.register((Break)(level, pos, state, player, xp) -> {
         if (!enabled()) {
            return EventResult.pass();
         }

         PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
         if (store != null) {
            store.remove(level.dimension().location().toString(), pos);
         }

         return EventResult.pass();
      });
   }
}
