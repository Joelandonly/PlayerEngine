package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngine;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.StructureManager;

public final class WaypointOriginClassifier {
   private WaypointOriginClassifier() {
   }

   public static WaypointOriginEvidence.Verdict classify(ServerLevel level, BlockPos canonicalPos, BlockPos secondaryPos) {
      try {
         if (WorldgenLootMarker.hasUnopenedLootTable(level, canonicalPos)) {
            PlayerEngine.LOGGER.debug("EllieGPS: Tier 2 REJECT_WORLDGEN at canonical pos {}", canonicalPos);
            return WaypointOriginEvidence.Verdict.REJECT_WORLDGEN;
         }

         if (secondaryPos != null && WorldgenLootMarker.hasUnopenedLootTable(level, secondaryPos)) {
            PlayerEngine.LOGGER.debug("EllieGPS: Tier 2 REJECT_WORLDGEN at secondary pos {}", secondaryPos);
            return WaypointOriginEvidence.Verdict.REJECT_WORLDGEN;
         }

         StructureManager sm = level.structureManager();
         if (sm.hasAnyStructureAt(canonicalPos)) {
            Map<Structure, ?> structures = sm.getAllStructuresAt(canonicalPos);

            for (Structure structure : structures.keySet()) {
               StructureStart start = sm.getStructureWithPieceAt(canonicalPos, structure);
               if (start != StructureStart.INVALID_START) {
                  PlayerEngine.LOGGER.debug("EllieGPS: Tier 3 REJECT_STRUCTURE at {} (structure: {})", canonicalPos, structure);
                  return WaypointOriginEvidence.Verdict.REJECT_STRUCTURE;
               }
            }
         }

         if (secondaryPos != null && sm.hasAnyStructureAt(secondaryPos)) {
            Map<Structure, ?> structures = sm.getAllStructuresAt(secondaryPos);

            for (Structure structure : structures.keySet()) {
               StructureStart start = sm.getStructureWithPieceAt(secondaryPos, structure);
               if (start != StructureStart.INVALID_START) {
                  PlayerEngine.LOGGER.debug("EllieGPS: Tier 3 REJECT_STRUCTURE at secondary pos {} (structure: {})", secondaryPos, structure);
                  return WaypointOriginEvidence.Verdict.REJECT_STRUCTURE;
               }
            }
         }

         PlayerEngine.LOGGER.debug("EllieGPS: classifier REGISTER for pos {}", canonicalPos);
         return WaypointOriginEvidence.Verdict.REGISTER;
      } catch (Exception e) {
         PlayerEngine.LOGGER.debug("EllieGPS: classifier exception at {} -> UNKNOWN: {}", canonicalPos, e.getMessage());
         return WaypointOriginEvidence.Verdict.UNKNOWN;
      }
   }

   public static WaypointOriginEvidence buildEvidence(ServerLevel level, BlockPos canonicalPos, BlockPos secondaryPos, long captureGameTime) {
      WaypointOriginEvidence.Verdict verdict = classify(level, canonicalPos, secondaryPos);

      String reasonToken = switch (verdict) {
         case REJECT_WORLDGEN -> "worldgen_loot";
         case REJECT_STRUCTURE -> "structure_piece";
         case UNKNOWN -> "origin_unverified";
         case REGISTER -> "";
      };
      return new WaypointOriginEvidence(false, verdict, reasonToken, captureGameTime);
   }
}
