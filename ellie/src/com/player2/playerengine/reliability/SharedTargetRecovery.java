package com.player2.playerengine.reliability;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.lang.ref.WeakReference;import java.util.*;import java.util.function.Predicate;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import com.player2.playerengine.reliability.RecoveryWorld.Cell;
/** One recovery lease per NPC. The root task is paused in place; safety chains retain priority. */
public final class SharedTargetRecovery {
 private static final Map<Object,Session> sessions=Collections.synchronizedMap(new WeakHashMap<>());
 private static final ThreadLocal<Object> ticking=new ThreadLocal<>();
 private static long warningAt;
 private static final class Session {
  WeakReference<Object> world=new WeakReference<>(null),root=new WeakReference<>(null),owner=new WeakReference<>(null),action=new WeakReference<>(null),baritone=new WeakReference<>(null),clearBuilder=new WeakReference<>(null),clearSchematic=new WeakReference<>(null);
  final TargetRecoveryPolicy policy=new TargetRecoveryPolicy();
  final Map<String,Long> rejected=new HashMap<>();final Set<String> tried=new HashSet<>();
  Cell target,destination,blocking;Object goal;boolean lease;String mode="WATCH",lookWriter="unknown",inputWriter="unknown",inputs="unavailable";long started,lastAirPlan,latestSample=Long.MIN_VALUE;String sampledTarget="",exhausted="";
 }
 private SharedTargetRecovery(){}
 private static Session session(Object controller)throws Exception{
  Object world=call(controller,"getWorld");Session s=sessions.computeIfAbsent(controller,k->new Session());
  if(s.world.get()!=world){dropLease(s);s.world=new WeakReference<>(world);s.root.clear();s.owner.clear();s.target=null;s.goal=null;s.mode="WATCH";s.tried.clear();s.rejected.clear();s.policy.reset();s.latestSample=Long.MIN_VALUE;s.sampledTarget="";}
  return s;
 }
 public static boolean rejected(Object controller,Object position){
  try{Session s=session(controller);String key=RecoveryWorld.cell(position).key();Long until=s.rejected.get(key);if(until==null)return false;if(System.nanoTime()<until)return true;s.rejected.remove(key);}catch(Exception|LinkageError ignored){}return false;
 }
 public static void reject(Object controller,Object position){try{session(controller).rejected.put(RecoveryWorld.cell(position).key(),System.nanoTime()+120_000_000_000L);}catch(Exception|LinkageError e){warn(e);}}
 public static boolean beforeTask(Object task,Object chain){
  try{
   Object controller=get(task,"controller");if(controller==null)return false;
   Session s=session(controller);List<?> tasks=(List<?>)call(chain,"getTasks");
   if(tasks.size()!=1)return false;
   RecoveryWorld game=new RecoveryWorld(controller);long now=game.tick();
   if(!String.valueOf(call(chain,"getName")).equals("User Tasks")){
    if(!s.mode.equals("WATCH")){release(s,game);s.policy.resume(now,false);event(s,"RECOVERY_YIELD","Higher-priority chain owns movement: "+call(chain,"getName"));}
    return false;
   }
   if(s.root.get()!=task){release(s,game);s.root=new WeakReference<>(task);s.owner.clear();s.target=null;s.policy.reset();s.tried.clear();}
   s.baritone=new WeakReference<>(game.baritone);
   boolean critical=game.criticalAir();
   if(critical&&!s.mode.equals("AIR")){
    if(!takeMovement(s,game))return false;
    release(s,game);
    List<Cell> exits=game.breathableExits();s.destination=exits.isEmpty()?null:exits.getFirst();s.mode="AIR";s.started=now;s.lastAirPlan=now;
    if(s.destination!=null)s.goal=game.path(s.destination);
    event(s,"RECOVERY_AIR",s.destination==null?"No local breathable exit found; yielding to survival chain":"Air overrides work; native route to breathable cell "+s.destination.key());
   }
   if(s.mode.equals("AIR")){
    if(!critical){release(s,game);resetOwner(s);s.policy.resume(now,true);event(s,"RECOVERY_RESUME","Breathing restored; original goal retained");return false;}
    if(s.destination!=null&&now-s.lastAirPlan>=40){s.lastAirPlan=now;game.release(s.goal);List<Cell> exits=game.breathableExits();if(!exits.isEmpty()){int index=Math.min((int)((now-s.started)/40),exits.size()-1);s.destination=exits.get(index);s.goal=game.path(s.destination);}}
    return true;
   }
   if(!s.mode.equals("WATCH"))return recoverTick(s,game,chain,now);
   Object owner=s.owner.get();if(owner==null||s.target==null||((Task)owner).stopped()||((Task)owner).isFinished())return false;
   if(((Task)owner).thisOrChildSatisfies(t->t instanceof ITaskCanForce force&&force.shouldForce(null)))return false;
   if(!((Task)task).containsTask(t->t==owner)){s.owner.clear();s.target=null;return false;}
   if(s.latestSample!=Long.MIN_VALUE&&now>=s.latestSample&&now-s.latestSample<5)return false;s.latestSample=now;
   if(!s.sampledTarget.equals(s.target.key())){s.tried.clear();s.sampledTarget=s.target.key();}
   double[] position=game.position();boolean reachable=game.interact(s.target);
   if(!s.policy.stalled(s.target.key(),game.progressSafely(s.target),position[0],position[1],position[2],now,reachable,game.breakingProgress(s.target)))return false;
   if(rejected(controller,RecoveryWorld.pos(s.target))||(s.policy.attempts()>=3&&s.exhausted.equals(s.target.key())))return false;
   s.inputs=game.activeInputs();
   if(!takeMovement(s,game))return false;
   s.blocking=game.blocking(s.target);diagnostics(s,game,position,reachable);
   return startRecovery(s,game,chain,now);
  }catch(Exception|LinkageError e){
   try{Object controller=get(task,"controller");Session s=sessions.get(controller);if(s!=null&&s.lease){RecoveryWorld game=new RecoveryWorld(controller);release(s,game);resetOwner(s);s.policy.resume(game.tick(),false);}}catch(Exception|LinkageError cleanup){warn(cleanup);}
   warn(e);return false;
  }
 }
 private static boolean startRecovery(Session s,RecoveryWorld game,Object chain,long now)throws Exception{
  if(!s.policy.nextAttempt()){
   s.exhausted=s.target.key();reject(game.controller,RecoveryWorld.pos(s.target));event(s,"RECOVERY_REJECT","Target temporarily rejected for 120 seconds; original goal retained");
   release(s,game);clearTargetCaches(s,game.controller);resetOwner(s);s.policy.resume(now,false);s.tried.clear();s.owner.clear();s.target=null;return false;
  }
  s.started=now;
  if(s.policy.attempts()==2&&startClear(s,game,chain))return true;
  for(Cell cell:game.approaches(s.target))if(s.tried.add(cell.key())){
   s.destination=cell;s.mode="APPROACH";s.goal=game.path(cell);event(s,"RECOVERY_APPROACH","Trying standable line-of-sight candidate "+cell.key()+"; route will be verified natively");return true;
  }
  if(startClear(s,game,chain))return true;
  release(s,game);resetOwner(s);s.policy.resume(now,false);event(s,"RECOVERY_REPATH","No safe alternate/clearance cell; replan native leaf while retaining project");return false;
 }
 private static boolean startClear(Session s,RecoveryWorld game,Object chain)throws Exception{
  if(!game.safeClear(s.blocking,s.target))return false;
  Object builder=call(game.baritone,"getBuilderProcess");s.clearBuilder=new WeakReference<>(builder);
  Task clear=game.clear(s.blocking);clear.controller=(com.player2.playerengine.PlayerEngineController)game.controller;s.action=new WeakReference<>(clear);set(s.root.get(),"sharedRecoveryAction",clear);s.mode="CLEAR";
  event(s,"RECOVERY_CLEAR","Deliberately clearing safe obstruction "+s.blocking.key());clear.tick((TaskChain)chain);s.clearSchematic=new WeakReference<>(get(builder,"schematic"));return true;
 }
 private static boolean recoverTick(Session s,RecoveryWorld game,Object chain,long now)throws Exception{
  if(s.mode.equals("APPROACH")){
   if(game.at(s.destination)&&game.interact(s.target)){release(s,game);resetOwner(s);s.policy.resume(now,false);event(s,"RECOVERY_RESUME","Approach reached and actual interaction reach verified; awaiting task progress");return false;}
   if(now-s.started>=100){game.release(s.goal);s.goal=null;s.blocking=game.blocking(s.target);return startRecovery(s,game,chain,now);}
   return true;
  }
  if(s.mode.equals("CLEAR")){
   if(game.air(s.blocking)){release(s,game);resetOwner(s);s.policy.resume(now,false);event(s,"RECOVERY_RESUME","Obstruction removal verified in world");return false;}
   Task action=(Task)s.action.get();if(action!=null&&now-s.started<200&&!action.stopped()){action.tick((TaskChain)chain);return true;}
   release(s,game);return startRecovery(s,game,chain,now);
  }
  return false;
 }
 private static boolean takeMovement(Session s,RecoveryWorld game)throws Exception{if(!game.acquireMovement())return false;s.lease=true;return true;}
 private static void resetOwner(Session s){Object owner=s.owner.get();if(owner instanceof Task task&&!task.isFinished()){task.interrupt(null,TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);task.reset();}}
 private static void release(Session s,RecoveryWorld game)throws Exception{
  dropLease(s);
 }
 private static void dropLease(Session s){
  try{Object baritone=s.baritone.get();if(baritone!=null&&s.goal!=null){Object custom=call(baritone,"getCustomGoalProcess");if(call(custom,"getGoal")==s.goal)call(custom,"onLostControl");}
   Object action=s.action.get();if(action instanceof Task task&&!task.stopped()){
    Object builder=s.clearBuilder.get();Object controller=get(task,"controller");
    if(builder!=null&&controller!=null&&call(call(controller,"getBaritone"),"getBuilderProcess")==builder&&(get(builder,"schematic")==s.clearSchematic.get()||!Boolean.TRUE.equals(call(builder,"isActive"))))task.stop();
   }
  }catch(Exception|LinkageError e){warn(e);}finally{try{Object root=s.root.get();if(root!=null)set(root,"sharedRecoveryAction",null);}catch(Exception error){warn(error);}s.clearBuilder.clear();s.clearSchematic.clear();s.goal=null;s.action.clear();s.mode="WATCH";s.lease=false;}
 }
 private static void clearTargetCaches(Session s,Object controller)throws Exception{
  Object root=s.root.get();if(root==null||s.target==null)return;
  // Clear selectors, not objectives or counters. Mining reselects without crediting a broken block.
  for(Object task=root;task!=null;task=get(task,"sub")){
   for(String name:List.of("craftingTablePos","activePos"))try{
    Object p=get(task,name);if(p!=null&&RecoveryWorld.cell(p).equals(s.target)){
     set(task,name,null);
     if(task.getClass().getName().endsWith(".MineBlockTask")){
      set(task,"breakChild",null);Class<?> phase=field(task,"phase").getType();Object finding=Enum.valueOf((Class)phase,"FINDING_BLOCK");set(task,"phase",finding);
     }
    }
   }catch(NoSuchFieldException ignored){}
  }
 }
 public static void enter(Object task){
  ticking.set(task);
 }
 public static Task after(Object task,Task proposed){
  ticking.remove();
  try{
   Object controller=get(task,"controller");if(controller==null)return proposed;Session s=session(controller);
   if(!s.mode.equals("WATCH"))return proposed;
   if(((Task)task).isFinished())return proposed;
   Cell target=target(task);if(target==null&&task.getClass().getName().endsWith("$BuildFromCode")&&proposed==null){target=builderTarget(controller);if(target==null&&s.owner.get()==task){s.owner.clear();s.target=null;ObservatoryTelemetry.event("RECOVERY_TARGET_UNAVAILABLE","Builder has no verified path destination; retaining native work without guessing a target");}}if(target!=null){s.owner=new WeakReference<>(task);s.target=target;}
  }catch(Exception|LinkageError e){warn(e);}return proposed;
 }
 private static Cell builderTarget(Object controller){
  try{Object pathing=call(call(controller,"getBaritone"),"getPathingBehavior");Optional<?> path=(Optional<?>)call(pathing,"getPath");if(path.isEmpty())return null;
   return goalTarget(call(pathing,"getGoal"),RecoveryWorld.cell(call(path.get(),"getDest")),0);
  }catch(Exception|LinkageError unavailable){return null;}
 }
 static Cell goalTarget(Object goal,Cell destination,int depth)throws Exception{
  if(goal==null||depth>8)return null;
  if(!Boolean.TRUE.equals(call(goal,"isInGoal",destination.x(),destination.y(),destination.z())))return null;
  String type=goal.getClass().getName();
  if(type.endsWith("$JankyGoalComposite")){Cell primary=goalTarget(get(goal,"primary"),destination,depth+1);return primary==null?goalTarget(get(goal,"fallback"),destination,depth+1):primary;}
  if(type.endsWith(".GoalComposite")){Object goals=call(goal,"goals");for(int i=0;i<java.lang.reflect.Array.getLength(goals);i++){Cell cell=goalTarget(java.lang.reflect.Array.get(goals,i),destination,depth+1);if(cell!=null)return cell;}return null;}
  try{return RecoveryWorld.cell(call(goal,"getGoalPos"));}catch(NoSuchMethodException unknown){return null;}
 }
 private static Cell target(Object task)throws Exception{
  String kind=task.getClass().getSimpleName();
  if(!List.of("DestroyBlockTask","GetCloseToBlockTask","GetWithinRangeOfBlockTask","GetToBlockTask","PlaceBlockTask","CraftMacroResourceTask","CraftInTableTask").contains(kind))return null;
  if(kind.equals("CraftMacroResourceTask")){String phase=String.valueOf(get(task,"phase"));if(!phase.contains("TABLE")&&!phase.equals("CRAFT_3X3_OUTPUT"))return null;}
  String field=switch(kind){case "DestroyBlockTask"->"pos";case "GetWithinRangeOfBlockTask"->"blockPos";case "GetCloseToBlockTask"->"toApproach";case "GetToBlockTask"->"position";case "PlaceBlockTask"->"target";default->"craftingTablePos";};
  Object p=get(task,field);if(p!=null)return RecoveryWorld.cell(p);
  return null;
 }
 public static boolean allowTaskLook(Object controller){
  Object owner=ticking.get();if(owner==null)return true;
  try{Cell target=target(owner);if(target==null)return true;Session s=session(controller);boolean allowed=s.mode.equals("WATCH")&&new RecoveryWorld(controller).interact(target);s.lookWriter="TASK:"+owner.getClass().getSimpleName()+(allowed?":interaction":":suppressed while navigating");return allowed;}
  catch(Exception|LinkageError e){warn(e);return true;}
 }
 public static void baritoneLook(Object behavior){
  try{Object baritone=get(behavior,"baritone");synchronized(sessions){for(Session s:sessions.values())if(s.baritone.get()==baritone){Object owner=ticking.get();s.lookWriter=writer();break;}}}catch(Exception ignored){}
 }
 private static String writer(){Object task=ticking.get();return task!=null?"TASK:"+task.getClass().getSimpleName():"OUTSIDE_TASK:"+StackWalker.getInstance().walk(frames->frames.filter(f->!f.getClassName().equals(SharedTargetRecovery.class.getName())&&!f.getClassName().endsWith(".LookBehavior")&&!f.getClassName().endsWith(".InputOverrideHandler")).limit(3).map(f->f.getClassName()+"."+f.getMethodName()).collect(java.util.stream.Collectors.joining(" <- ")));}
 public static void inputWrite(Object handler,Object input,boolean forced){
  if(!forced)return;try{Object baritone=get(handler,"baritone");synchronized(sessions){for(Session s:sessions.values())if(s.baritone.get()==baritone){s.inputWriter=String.valueOf(input)+" by "+writer();break;}}}catch(Exception ignored){}
 }
 public static void stopped(Object task){
  ticking.remove();try{Object controller=get(task,"controller");if(controller==null)return;Session s=sessions.get(controller);if(s!=null&&s.root.get()==task){dropLease(s);sessions.remove(controller);ObservatoryTelemetry.set("recovery","IDLE");ObservatoryTelemetry.set("recoveryAttempt","0/3");ObservatoryTelemetry.set("targetRecovery","No active recovery; task ended");}}catch(Exception|LinkageError e){warn(e);}
 }
 private static void diagnostics(Session s,RecoveryWorld game,double[] position,boolean reachable)throws Exception{
  Object owner=s.owner.get();String block=String.valueOf(call(game.state(s.target),"method_26204"));
  String detail="TARGET="+block+" @ "+s.target.key()+" TASK="+(owner==null?"unknown":owner.getClass().getSimpleName())+" POSITION="+Arrays.toString(position)+" DISTANCE="+Math.sqrt(Math.pow(position[0]-s.target.x(),2)+Math.pow(position[1]-s.target.y(),2)+Math.pow(position[2]-s.target.z(),2))+" INTERACTION_REACH="+reachable+" LINE_OF_SIGHT="+(s.blocking==null?"no blocking cell found":"BLOCKED")+" BLOCKING_CELL="+(s.blocking==null?"none":s.blocking.key())+" LOOK_HIT="+game.lookHit()+" LOOK_WRITER="+s.lookWriter+" INPUTS_BEFORE_RECOVERY="+s.inputs+" INPUT_WRITER="+s.inputWriter+" NAVIGATION=STALLED";
  ObservatoryTelemetry.set("recoveryTarget",block+" @ "+s.target.key());ObservatoryTelemetry.set("recoveryTask",owner==null?"unknown":owner.getClass().getSimpleName());
  ObservatoryTelemetry.set("recoveryPosition",Arrays.toString(position));ObservatoryTelemetry.set("recoveryInteractionReach",reachable);
  ObservatoryTelemetry.set("recoveryLineOfSight",s.blocking==null?"No blocking cell found":"BLOCKED");ObservatoryTelemetry.set("recoveryBlockingCell",s.blocking==null?"none":s.blocking.key());
  ObservatoryTelemetry.set("recoveryInputsBeforeRecovery",s.inputs);ObservatoryTelemetry.set("recoveryInputWriter",s.inputWriter);
  ObservatoryTelemetry.set("recoveryLookHit",game.lookHit());ObservatoryTelemetry.set("recoveryLookWriter",s.lookWriter);
  ObservatoryTelemetry.set("targetRecovery",detail);event(s,"TARGET_RECOVERY",detail);
 }
 private static void event(Session s,String name,String detail){ObservatoryTelemetry.set("recovery",s.mode);ObservatoryTelemetry.set("recoveryAttempt",s.policy.attempts()+"/3");ObservatoryTelemetry.event(name,detail+" MOVEMENT_OWNER="+(s.mode.equals("WATCH")?"native task":"shared recovery")+" ATTEMPT="+s.policy.attempts()+"/3");}
 private static void warn(Throwable error){long now=System.nanoTime();if(now-warningAt>10_000_000_000L){warningAt=now;ObservatoryTelemetry.event("RECOVERY_BOUNDARY_UNAVAILABLE",error.getClass().getSimpleName()+": "+error.getMessage());}}
}
