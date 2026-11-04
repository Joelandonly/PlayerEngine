import com.player2.playerengine.*;import com.player2.playerengine.tasks.setup.*;import java.lang.reflect.*;
/** Run the real setup selection code with a recording catalogue and a forbidden macro boundary. */
public final class SetupNativeAcquisitionTest {
 public static final class Dimension{public String method_29177(){return "minecraft:overworld";}}
 public static final class World{public Dimension method_27983(){return new Dimension();}}
 public static final class Extra{public final java.util.List<java.util.function.Predicate<Object>> guards=new java.util.ArrayList<>();public Object getBreakMutex(){return this;}public java.util.List<java.util.function.Predicate<Object>> getBreakAvoiders(){return guards;}}
 public static final class Controller extends PlayerEngineController{final Extra extra=new Extra();Controller(){super(null);}public World getWorld(){return new World();}public Extra getExtraBaritoneSettings(){return extra;}}
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  var setup=new EllieSetupTask();setup.controller=new PlayerEngineController(null);
  Method resolve=EllieSetupTask.class.getDeclaredMethod("resolve",EllieSetupTask.Step.class);resolve.setAccessible(true);int cases=0;
  for(var step:EllieSetupTask.plannedSteps()){
   Object task=resolve.invoke(setup,step);check(task instanceof TaskCatalogue.NativeAcquisition,"setup must select native catalogue for "+step.resource);
   var request=(TaskCatalogue.NativeAcquisition)task;check(request.id.equals(step.resource)&&request.count==step.amount,"native request quantity must survive for "+step.resource);check(!request.reclaimsPlacedOutput(),"setup must craft supplies instead of chasing placed output for "+step.resource);cases++;
  }
  Class<?> world=Class.forName("com.player2.playerengine.tasks.setup.StarterBaseWorld");var ctor=world.getDeclaredConstructor(PlayerEngineController.class,StarterBaseLayout.Pos.class,String.class);ctor.setAccessible(true);Object environment=ctor.newInstance(setup.controller,new StarterBaseLayout.Pos(0,64,0),"minecraft:overworld");
  Method acquire=world.getDeclaredMethod("acquire",String.class);acquire.setAccessible(true);
  for(String id:new String[]{"crafting_table","furnace","chest"}){var request=(TaskCatalogue.NativeAcquisition)acquire.invoke(environment,id);check(request.id.equals(id)&&request.count==1,"workstation acquisition must use native catalogue");check(!request.reclaimsPlacedOutput(),"station reacquisition must not strip placed output");cases++;}
  Controller natural=new Controller();Object naturalWorld=ctor.newInstance(natural,new StarterBaseLayout.Pos(0,64,0),"minecraft:overworld");
  Method protect=world.getDeclaredMethod("protect",java.util.List.class);protect.setAccessible(true);var plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(10,64,10));protect.invoke(naturalWorld,plan);
  check(natural.extra.guards.size()==1&&natural.extra.guards.getFirst().test(new net.minecraft.class_2338(10,63,10)),"natural workstation floors must be protected from navigation excavation");cases++;
  Method release=world.getDeclaredMethod("releaseProtection");release.setAccessible(true);release.invoke(naturalWorld);check(natural.extra.guards.isEmpty(),"terminal root must remove its native break guard");cases++;
  System.out.println("SetupNativeAcquisitionTest: "+(cases-2)+" native production selections and 2 guard lifecycle checks passed with macro resolution forbidden; game crafting not simulated");
 }
}
