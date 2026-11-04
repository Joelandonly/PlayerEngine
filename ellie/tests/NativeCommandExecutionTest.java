import com.player2.playerengine.*;import com.player2.playerengine.commands.base.*;import com.player2.playerengine.commands.EatFoodCommand;
import com.player2.playerengine.nativecommands.*;import com.player2.playerengine.player2api.*;
import com.player2.playerengine.tasks.base.*;import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;
import com.player2.playerengine.tasks.misc.EatFoodTask;import net.minecraft.*;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class NativeCommandExecutionTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static final class Stack {final class_1792 item;Stack(String id){item=new class_1792(id);}public boolean method_7960(){return false;}public class_1792 method_7909(){return item;}}
 public static final class Inventory {List<Stack> stacks=new ArrayList<>();public int method_5439(){return stacks.size();}public Stack method_5438(int i){return stacks.get(i);}}
 public static final class Context {int hunger=10;public Context getEntityContext(){return this;}public Context hungerManager(){return this;}public int getFoodLevel(){return hunger;}}
 public static final class Controller extends PlayerEngineController {final Inventory inventory=new Inventory();final Context context=new Context();Controller(){super(null);}public Inventory getInventory(){return inventory;}public Context getBaritone(){return context;}}
 static final class Probe extends Command {int calls;Probe(String name){super(name,"Probe",new ArgBase[0]);}protected void call(PlayerEngineController mod,ArgParser parser){calls++;finish();}}
 static Object tick(BuildStructureTask root)throws Exception{var method=BuildStructureTask.class.getDeclaredMethod("onTick");method.setAccessible(true);return method.invoke(root);}
 static void complete(Object child,Optional<String> result)throws Exception{NativeAccess.set(child,"result",Optional.of(result));NativeAccess.set(child,"done",true);}
 public static void main(String[] args)throws Exception{
  Controller mod=new Controller();UUID owner=UUID.randomUUID();AgentConversationData data=new AgentConversationData(mod,owner);Probe probe=new Probe("get");mod.getCommandExecutor().registerNewCommand(probe);
  check(SetupDirectRoute.tryRoute(data,new Event.UserMessage("Ellie, get cobblestone 64",owner))&&probe.calls==1,"owner command must execute without an interpreter");
  check(!SetupDirectRoute.tryRoute(data,new Event.UserMessage("Ellie, get diamond 2",UUID.randomUUID()))&&probe.calls==1,"non-owner must not run native tasks");
  check(SetupDirectRoute.tryRoute(data,new Event.UserMessage("Ellie, gamer",owner))&&probe.calls==1,"unavailable command must be consumed without a model request");
  check(!SetupDirectRoute.tryRoute(data,new Event.UserMessage("Ellie, hello there",owner)),"ordinary chat remains separate");
  AtomicInteger errors=new AtomicInteger();EatFoodCommand eat=new EatFoodCommand();eat.run(mod,"eat_food",()->{throw new AssertionError("empty inventory reported success");},error->errors.incrementAndGet());check(errors.get()==1,"empty inventory must report an error, never silently return");
  mod.inventory.stacks.add(new Stack("minecraft:rotten_flesh"));mod.inventory.stacks.add(new Stack("minecraft:bread"));
  check(((EatFoodTask)NativeFood.task(mod,"")).item.name().equals("minecraft:bread"),"no-argument selection must choose carried suitable food");
  check(((EatFoodTask)NativeFood.task(mod,"bread")).item.name().equals("minecraft:bread"),"explicit carried food");
  try{NativeFood.task(mod,"apple");throw new AssertionError("absent item was accepted");}catch(IllegalArgumentException expected){}
  mod.context.hunger=20;try{NativeFood.task(mod,"");throw new AssertionError("full hunger accepted");}catch(IllegalArgumentException expected){}
  BuildStructureTask unsupported=new BuildStructureTask(new class_2338(0,64,0),"castle","custom castle",mod);tick(unsupported);
  check(((TrackedTaskOutcomeProvider)unsupported).isTerminal()&&!((TrackedTaskOutcomeProvider)unsupported).isSuccessful()&&((TrackedTaskOutcomeProvider)unsupported).controlledReason().contains("Unsupported local blueprint"),"unsupported build must fail locally with typed outcome");
  BuildStructureTask root=new BuildStructureTask(new class_2338(0,64,0),"small shelter","small shelter",mod);Object child=tick(root);check(child instanceof Task&&!root.isFinished(),"supported template must hand off to native physical builder: "+((TrackedTaskOutcomeProvider)root).controlledReason());
  complete(child,Optional.empty());tick(root);check(((TrackedTaskOutcomeProvider)root).isSuccessful(),"verified physical result required");
  BuildStructureTask failed=new BuildStructureTask(new class_2338(0,64,0),"small shelter","small shelter",mod);Object bad=tick(failed);complete(bad,Optional.of("blocked cell"));tick(failed);check(!((TrackedTaskOutcomeProvider)failed).isSuccessful()&&((TrackedTaskOutcomeProvider)failed).controlledReason().equals("blocked cell"),"physical failure must never become command success or request a model retry");
  var outcome=SetupDirectRoute.class.getDeclaredMethod("outcomeText",String.class,String.class,com.player2.playerengine.supervisor.GoalLifecycle.Snapshot.class,com.player2.playerengine.supervisor.GoalLifecycle.Snapshot.class);outcome.setAccessible(true);
  var rejected=new com.player2.playerengine.supervisor.GoalLifecycle.Snapshot(1,"test","mine",com.player2.playerengine.supervisor.GoalLifecycle.Status.FAILED,"blocked target");
  check(((String)outcome.invoke(null,"mine","completed",null,rejected)).contains("Completion was not verified"),"failed native receipt cannot be reported as completion");
  System.out.println("NativeCommandExecutionTest: 14 checks pass: real owner route, food selection/errors, real patched builder orchestration; game boundaries substituted");
 }
}
