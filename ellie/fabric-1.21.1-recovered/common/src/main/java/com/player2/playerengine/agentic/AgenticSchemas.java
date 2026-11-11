package com.player2.playerengine.agentic;

import java.util.Set;

public final class AgenticSchemas {
   public static final int PLAN_SCHEMA_VERSION = 1;
   public static final String STEP_GATHER_LOOSE_ITEMS = "gather_loose_items";
   public static final String STEP_RESOLVE_STORAGE_CHEST = "resolve_storage_chest";
   public static final String STEP_DEPOSIT_ITEMS = "deposit_items";
   public static final String STEP_LABEL_CHEST = "label_chest";
   public static final String STEP_SMELT_ITEMS = "smelt_items";
   public static final String STEP_SMITH_ITEMS = "smith_items";
   public static final String STEP_MINE_BLOCK = "mine_block";
   public static final String STEP_SETUP_FARM = "setup_farm";
   public static final String STEP_HARVEST_FARM = "harvest_farm";
   public static final String STEP_PLANT_FARM = "plant_farm";
   public static final Set<String> ALLOWED_STEP_KINDS = ResourceAgenticIntegration.allowResourceKind(Set.of(
      "gather_loose_items",
      "resolve_storage_chest",
      "deposit_items",
      "label_chest",
      "smelt_items",
      "smith_items",
      "mine_block",
      "setup_farm",
      "harvest_farm",
      "plant_farm"
   ));
   public static final String PLANNER_COMMAND_ID = "agentic";
   public static final Set<String> FORBIDDEN_FUTURE_STEP_KINDS = Set.of("register_waypoint", "elliegps");

   private AgenticSchemas() {
   }

}
