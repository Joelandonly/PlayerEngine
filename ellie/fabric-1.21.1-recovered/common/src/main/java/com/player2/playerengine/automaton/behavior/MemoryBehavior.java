package com.player2.playerengine.automaton.behavior;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.cache.IWaypoint;
import com.player2.playerengine.automaton.api.cache.Waypoint;
import com.player2.playerengine.automaton.api.event.events.BlockInteractEvent;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;

public final class MemoryBehavior extends Behavior {
   public MemoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      if (event.getType() == BlockInteractEvent.Type.USE && BlockStateInterface.getBlock(this.ctx, event.getPos()) instanceof BedBlock) {
         this.baritone
            .getWorldProvider()
            .getCurrentWorld()
            .getWaypoints()
            .addWaypoint(new Waypoint("bed", IWaypoint.Tag.BED, BetterBlockPos.from(event.getPos())));
      }
   }
}
