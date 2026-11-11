package com.player2.playerengine.agentic;

import com.player2.playerengine.agentic.steps.DepositItemsStepFactory;
import com.player2.playerengine.agentic.steps.GatherLooseItemsStepFactory;
import com.player2.playerengine.agentic.steps.HarvestFarmStepFactory;
import com.player2.playerengine.agentic.steps.LabelChestStepFactory;
import com.player2.playerengine.agentic.steps.MineBlockStepFactory;
import com.player2.playerengine.agentic.steps.PlantFarmStepFactory;
import com.player2.playerengine.agentic.steps.ResolveStorageChestStepFactory;
import com.player2.playerengine.agentic.steps.SetupFarmStepFactory;
import com.player2.playerengine.agentic.steps.SmeltStepFactory;
import com.player2.playerengine.agentic.steps.SmithStepFactory;
import com.player2.playerengine.tasks.base.Task;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AgenticStepFactoryRegistry {
   private final Map<String, AgenticStepFactory> factories = new LinkedHashMap<>();

   public AgenticStepFactoryRegistry() {
      this.register("gather_loose_items", new GatherLooseItemsStepFactory());
      this.register("resolve_storage_chest", new ResolveStorageChestStepFactory());
      this.register("deposit_items", new DepositItemsStepFactory());
      this.register("label_chest", new LabelChestStepFactory());
      this.register("smelt_items", new SmeltStepFactory());
      this.register("smith_items", new SmithStepFactory());
      this.register("mine_block", new MineBlockStepFactory());
      this.register("setup_farm", new SetupFarmStepFactory());
      this.register("harvest_farm", new HarvestFarmStepFactory());
      this.register("plant_farm", new PlantFarmStepFactory());
      ResourceAgenticIntegration.registerFactory(this);
   }

   public void register(String kind, AgenticStepFactory factory) {
      this.factories.put(kind, factory);
   }

   public Optional<Task> createTask(AgenticStepSpec step, AgenticExecutionContext context) {
      if (step != null && step.kind() != null) {
         AgenticStepFactory factory = this.factories.get(step.kind());
         return factory == null ? Optional.empty() : factory.createTask(step, context);
      } else {
         return Optional.empty();
      }
   }
}
