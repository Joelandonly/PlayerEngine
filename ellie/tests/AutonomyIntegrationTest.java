import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.*;
import java.util.*;

/** Deterministic decision tests. No claim that a real Minecraft chest was opened. */
public final class AutonomyIntegrationTest {
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 static AgenticGoalRequest goal(String value){return new AgenticGoalRequest(1,UUID.randomUUID(),UUID.randomUUID(),value,"command",0);}
 public static void main(String[] args){
  check(ResourceAgenticIntegration.parse("get cobblestone 64").equals(new ResourceAgenticIntegration.Parsed("cobblestone",64)),"parse exact get syntax");
  check(ResourceAgenticIntegration.parse("\"get cobblestone 64\"").equals(new ResourceAgenticIntegration.Parsed("cobblestone",64)),"parse quoted get syntax");
  check(ResourceAgenticIntegration.parse("craft minecraft:chest 8").item().equals("chest"),"namespaced vanilla item");
  check(ResourceAgenticIntegration.parse("get diamond 9999")==null,"reject unbounded item count");
  check(ResourceAgenticIntegration.parse("get command_block 1")==null,"reject unsafe target");
  check(ResourceAgenticIntegration.parse("collect loose drops")==null,"do not hijack existing loose pickup intent");
  check(StorageIntelligence.fresh(7000,6500,6000),"fresh verified storage usable");
  check(!StorageIntelligence.fresh(10000,2000,6000),"stale stock must be rechecked");
  check(!StorageIntelligence.fresh(500,600,6000),"negative world time cannot masquerade as fresh stock");
  check(ResourceAgenticIntegration.addResourceSequence(new Object[]{List.of("mine_block")}).contains(List.of("get_resource")),"single get resource allowed in sequence");
  check(ResourceAgenticIntegration.allowResourceKind(Set.of("mine_block")).contains("get_resource"),"registration extends not replaces existing kinds");
  var bot=new PlayerEngineController(null);
  var result=AgenticPlannerService.plan(bot,goal("get cobblestone 64"));
  check(result.hasExecutablePlan(),"new planner route must pass full existing validator: "+result);
  check(result.validation().sanitizedPlan().steps().get(0).kind().equals("get_resource"),"new plan runs through native resource factory");
  check(AgenticPlannerService.plan(bot,goal("\"get coal 12\"")).hasExecutablePlan(),"quoted command accepted through planner");
  var old=AgenticPlannerService.plan(bot,goal("collect loose drops"));
  check(old.hasExecutablePlan(),"prior fallback remains intact");
  System.out.println("AutonomyIntegrationTest: 13 parser, freshness, schema, native planner and existing fallback checks passed");
 }
}
