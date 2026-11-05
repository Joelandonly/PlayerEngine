import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Method;

/** Reproduce completion before observable inventory output, without trusting callbacks. */
public final class WorkshopOutputHandoffTest {
 static final Method TICK;
 static {try{TICK=StarterBaseTask.class.getDeclaredMethod("onTick");TICK.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static class World extends SetupTerraformTest.World {
  boolean nativeEnded,never;int observations,orders;
  public int count(String resource){if(!resource.equals("crafting_table"))return 100;if(!nativeEnded)return 0;return !never&&++observations>=7?1:0;}
  public Task acquire(String resource){orders++;return new SetupTerraformTest.Op(()->nativeEnded=true);}
 }
 static void run(StarterBaseTask task)throws Exception{
  for(int i=0;i<250&&!task.isTerminal();i++){Task child=(Task)TICK.invoke(task);if(child instanceof SetupTerraformTest.Op op)op.execute();}
 }
 public static void main(String[] args)throws Exception{
  World delayed=new World();StarterBaseTask task=new StarterBaseTask(delayed);run(task);
  check(task.isSuccessful()&&delayed.orders==1&&delayed.observations>=7,"delayed actual output permits three verified stations without repeating acquisition");
  World absent=new World();absent.never=true;StarterBaseTask failed=new StarterBaseTask(absent);run(failed);
  check(failed.isTerminal()&&!failed.isSuccessful()&&absent.orders==1&&failed.controlledReason().contains("after 40 ticks"),"native success without actual output is bounded BLOCKED, never completion or repeated acquisition");
  System.out.println("WorkshopOutputHandoffTest: delayed output and false native completion regressions passed");
 }
}
