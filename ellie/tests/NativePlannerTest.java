import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.*;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class NativePlannerTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static AgenticGoalRequest request(String text){return new AgenticGoalRequest(1,UUID.randomUUID(),UUID.randomUUID(),text,"command",0);}
 public static void main(String[] args){
  PlayerEngineController mod=new PlayerEngineController(null);
  var empty=AgenticPlannerService.plan(mod,request(""));check(!empty.hasExecutablePlan(),"empty goal");
  var gather=AgenticPlannerService.plan(mod,request("collect loose drops"));check(gather.hasExecutablePlan(),"pickup must have a local executable plan: "+gather);
  var storage=AgenticPlannerService.plan(mod,request("prepare a storage chest"));check(storage.hasExecutablePlan(),"storage must have a local executable plan: "+storage);
  var unknown=AgenticPlannerService.plan(mod,request("invent a castle"));check(!unknown.hasExecutablePlan(),"unsupported goal must fail locally");
  var mine=AgenticPlannerService.plan(mod,request("mine diamond"));check(!mine.hasExecutablePlan()&&mine.failureMessage().contains("get"),"resource goals redirect to native get");
  AtomicInteger callbacks=new AtomicInteger();AgenticPlannerService.planAsync(mod,request("collect loose drops"),outcome->{check(outcome.hasExecutablePlan(),"callback plan");callbacks.incrementAndGet();});check(callbacks.get()==1,"one local callback");
  System.out.println("NativePlannerTest: 6 passed with AI settings forbidden and no API service in the harness");
 }
}
