package com.player2.playerengine.player2api;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.nativecommands.NativeCommandText;
import java.util.UUID;
import com.player2.playerengine.supervisor.CoreGoalSupervisor;
import com.player2.playerengine.supervisor.GoalLifecycle;
/** Binary-compatible extension of the original owner-verified setup bypass. */
public final class SetupDirectRoute {
 private SetupDirectRoute(){}
 public static String resolve(String input){return NativeCommandText.resolve(input);}
 public static boolean tryRoute(AgentConversationData data,Event event){
  if(!(event instanceof Event.UserMessage message))return false;
  String resolved=resolve(message.message());if(resolved==null)return false;
  UUID user=message.authenticatedUserUuid();
  if(user==null||!data.isOwner(user)){logEvent("COMMAND_ROUTE_REJECTED","Unverified/non-owner exact request");return false;}
  PlayerEngineController controller=data.getMod();if(controller==null)return false;
  try {
   if(resolved.startsWith("__local_build__ "))resolved=NativeCommandText.localBuild(controller,resolved.substring("__local_build__ ".length()));
   String line=resolved;String id=line.split("\\s+",2)[0];
   // OWNER_SAFEZONE_DIRECT: owner-authenticated native management, NEVER an LLM command.
   if(id.equals("safezone")) {
    String result=com.player2.playerengine.safezone.SafeZoneNativeCommand.execute(controller,user,line);
    logEvent("SAFEZONE_COMMAND",result);notifyUser(controller,result);return true;
   }
   // WATERFILL_OWNER_NATIVE: region editing requires the same authenticated owner
   // as safezone; never dispatch through an untrusted LLM-selected executor.
   if(id.equals("waterfill")) {
    String result=com.player2.playerengine.nativecommands.WaterFillNativeCommand.execute(controller,user,line);
    logEvent("WATERFILL_COMMAND",result);notifyUser(controller,result);return true;
   }
   CommandExecutor executor=controller.getCommandExecutor();
   if(executor==null||executor.get(id)==null){notifyUser(controller,"Command unavailable: "+id+". Nothing was started. Use setup, setup_farm, get, mine or a supported build_structure template.");logEvent("COMMAND_ROUTE_UNAVAILABLE",id);return true;}
   GoalLifecycle.Snapshot previous=CoreGoalSupervisor.snapshot(controller);
   logEvent("COMMAND_ROUTE","Exact owner request -> "+id+" (model selection bypassed)");
   executor.execute(executor.getCommandPrefix()+line,
    ()->{logEvent("COMMAND_DISPATCH",id+" accepted; awaiting native task result");notifyUser(controller,id+" accepted.");},
    ()->{String detail=outcomeText(id,id+" completed.",previous,CoreGoalSupervisor.snapshot(controller));logEvent("COMMAND_OUTCOME",detail);notifyUser(controller,detail);},
    note->{String detail=note!=null&&note.startsWith("[info-result]")?note.substring("[info-result]".length()):String.valueOf(note);detail=outcomeText(id,detail,previous,CoreGoalSupervisor.snapshot(controller));logEvent("COMMAND_OUTCOME",id+": "+detail);notifyUser(controller,detail);},
    error->{logEvent("COMMAND_ROUTE_ERROR",id+": "+error.getMessage());notifyUser(controller,id+" failed: "+error.getMessage());});
   return true;
  }catch(Exception|LinkageError error){logEvent("COMMAND_ROUTE_ERROR",error.getClass().getSimpleName()+": "+error.getMessage());notifyUser(controller,"Command could not start: "+error.getMessage());return true;}
 }
 private static String outcomeText(String id,String reported,GoalLifecycle.Snapshot before,GoalLifecycle.Snapshot after){
  if(after!=null&&(before==null||after.generation()>before.generation())&&after.status()!=GoalLifecycle.Status.SUCCEEDED)
   return id+" ended with "+after.status()+": "+after.detail()+". Completion was not verified.";
  return reported;
 }
 private static void notifyUser(PlayerEngineController controller,String text){
  try{controller.getClass().getMethod("reportAgenticProgress",String.class,boolean.class).invoke(controller,text,true);}
  catch(ReflectiveOperationException|RuntimeException error){controller.log(text);}
 }
 private static void logEvent(String name,String text){
  System.out.println("[EllieNativeRoute] "+name+": "+text);
  try{Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("event",String.class,String.class).invoke(null,name,text);}catch(ReflectiveOperationException|RuntimeException ignored){}
 }
}
