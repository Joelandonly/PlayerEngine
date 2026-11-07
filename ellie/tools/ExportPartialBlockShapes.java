import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

/** Maintainer-only vanilla 1.21.1 fixture export; not part of the mod or a game simulation.
 * Compile/run against the Mojang-mapped vanilla JAR and its version-matched libraries. */
public final class ExportPartialBlockShapes {
 public static void main(String[] args)throws Exception {
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  Set<String> names=Set.of("stone","air","oak_slab","oak_stairs","snow","white_carpet","moss_carpet",
   "farmland","dirt_path","soul_sand","chest","ender_chest","white_bed","cake","oak_trapdoor",
   "iron_trapdoor","enchanting_table","stonecutter","anvil","grindstone","brewing_stand","flower_pot",
   "lily_pad","daylight_detector","repeater","comparator","rail","oak_pressure_plate","heavy_weighted_pressure_plate",
   "oak_fence","cobblestone_wall","glass_pane","iron_bars","oak_fence_gate","oak_door","campfire",
   "soul_campfire","cactus","magma_block","honey_block","powder_snow","pointed_dripstone","scaffolding",
   "hopper","cauldron","water_cauldron","composter","lectern","bell","lantern","soul_lantern","chain",
   "end_portal_frame","decorated_pot","conduit","lightning_rod","sculk_sensor","candle","sea_pickle",
   "amethyst_cluster","player_head","big_dripleaf","turtle_egg","mud","soul_soil","stone_pressure_plate");
  List<String> lines=new ArrayList<>();lines.add("# Vanilla 1.21.1 collision AABBs, EmptyBlockGetter, default collision context. World/entity behavior is NOT recorded.");
  lines.add("state\tboxes\tland_pathfindable\tfull_collision_cube");
  for(var block:BuiltInRegistries.BLOCK){
   String id=BuiltInRegistries.BLOCK.getKey(block).getPath();if(!names.contains(id))continue;
   for(var state:block.getStateDefinition().getPossibleStates()){
    List<String> boxes=new ArrayList<>();
    for(AABB b:state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs())
     boxes.add(b.minX+","+b.minY+","+b.minZ+","+b.maxX+","+b.maxY+","+b.maxZ);
    lines.add(state+"\t"+String.join(";",boxes)+"\t"+state.isPathfindable(net.minecraft.world.level.pathfinder.PathComputationType.LAND)
      +"\t"+state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));
   }
  }
  Files.write(Path.of(args[0]),lines);System.out.println("Exported "+(lines.size()-2)+" vanilla block-state shapes");
 }
}
