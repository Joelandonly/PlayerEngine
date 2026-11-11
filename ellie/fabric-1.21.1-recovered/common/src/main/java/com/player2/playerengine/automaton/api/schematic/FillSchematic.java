package com.player2.playerengine.automaton.api.schematic;

import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FillSchematic extends AbstractSchematic {
   private final BlockOptionalMeta bom;

   public FillSchematic(int x, int y, int z, BlockOptionalMeta bom) {
      super(x, y, z);
      this.bom = bom;
   }

   public BlockOptionalMeta getBom() {
      return this.bom;
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      if (this.bom.matches(current)) {
         return current;
      }

      if (current.getBlock() != Blocks.AIR) {
         return Blocks.AIR.defaultBlockState();
      }

      for (BlockState placeable : approxPlaceable) {
         if (this.bom.matches(placeable)) {
            return placeable;
         }
      }

      return this.bom.getAnyBlockState();
   }
}
