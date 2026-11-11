package com.player2.playerengine.tasks.farming;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;

public final class HarvestBehaviorRegistry {
   public static final HarvestBehaviorRegistry DEFAULT = new HarvestBehaviorRegistry(
      new TorchflowerHarvestBehavior(), new PitcherCropHarvestBehavior(), new CropBlockHarvestBehavior()
   );
   private final List<HarvestBehavior> behaviors;

   public HarvestBehaviorRegistry(HarvestBehavior... behaviors) {
      this(Arrays.asList(Objects.requireNonNull(behaviors, "behaviors")));
   }

   public HarvestBehaviorRegistry(List<? extends HarvestBehavior> behaviors) {
      List<? extends HarvestBehavior> supplied = Objects.requireNonNull(behaviors, "behaviors");
      if (supplied.stream().anyMatch(Objects::isNull)) {
         throw new IllegalArgumentException("harvest behaviors cannot contain null");
      }

      this.behaviors = List.copyOf(supplied);
   }

   public Optional<HarvestBehavior> resolve(BlockState state) {
      Objects.requireNonNull(state, "state");
      return this.behaviors.stream().filter(behavior -> behavior.recognizes(state)).findFirst();
   }

   public List<HarvestBehavior> behaviors() {
      return this.behaviors;
   }
}
