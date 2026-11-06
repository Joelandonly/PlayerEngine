import java.lang.reflect.*;import java.util.concurrent.*;
import net.minecraft.*;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.movement.GetOutOfWaterTask;

/** Execute the actual packaged native inner goal with world/cache boundaries substituted. */
public final class WaterEscapeGoalTest {
 static int checks;static Class<?> goalClass;static Constructor<?> ctor;static Method goal,heuristic;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static Object create(PlayerEngineController c)throws Exception{return ctor.newInstance(new GetOutOfWaterTask(),c);}
 static boolean dry(Object g,int x,int y,int z)throws Exception{return (boolean)goal.invoke(g,x,y,z);}
 static double cost(Object g,int x,int y,int z)throws Exception{return (double)heuristic.invoke(g,x,y,z);}
 public static void main(String[] args)throws Exception {
  goalClass=Class.forName("com.player2.playerengine.tasks.movement.GetOutOfWaterTask$EscapeFromWaterGoal");
  ctor=goalClass.getDeclaredConstructor(GetOutOfWaterTask.class,PlayerEngineController.class);ctor.setAccessible(true);
  goal=goalClass.getDeclaredMethod("isInGoal",int.class,int.class,int.class);goal.setAccessible(true);
  heuristic=goalClass.getDeclaredMethod("heuristic",int.class,int.class,int.class);heuristic.setAccessible(true);
  class_3218 w=new class_3218();w.loaded.add("0,0");w.holdLiveReads=true;
  PlayerEngineController controller=new PlayerEngineController(w);Object g=create(controller);
  ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"water-escape-path-worker"));
  try{
   if(args.length>0&&args[0].equals("--expect-live-wait")){
    Future<Double> query=worker.submit(()->cost(g,8,64,8));
    check(w.liveReadEntered.await(2,TimeUnit.SECONDS),"published v14 goal enters live-world read from worker");
    try{query.get(150,TimeUnit.MILLISECONDS);throw new AssertionError("expected pending server-dependent read");}catch(TimeoutException expected){}
    w.releaseLiveRead.countDown();query.get(2,TimeUnit.SECONDS);
    System.out.println("V14 water goal reproduced a worker waiting for simulated live-world servicing; Minecraft scheduler not reproduced");return;
   }
   check(worker.submit(()->dry(g,8,64,8)&&cost(g,8,64,8)==0).get(2,TimeUnit.SECONDS),"dry loaded neighborhood remains a valid exit");
   w.water(8,64,8);check(worker.submit(()->!dry(g,8,64,8)&&cost(g,8,64,8)==1).get(2,TimeUnit.SECONDS),"water center rejected");w.states.clear();
   for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}){
    w.water(8+d[0],64,8+d[1]);check(worker.submit(()->!dry(g,8,64,8)&&cost(g,8,64,8)==.5).get(2,TimeUnit.SECONDS),"adjacent/diagonal water exclusion retained");w.states.clear();
   }
   check(worker.submit(()->!dry(g,16,64,8)&&cost(g,16,64,8)==1).get(2,TimeUnit.SECONDS),"unloaded destination is not a verified exit");
   check(worker.submit(()->!dry(g,15,64,8)&&cost(g,15,64,8)==.5).get(2,TimeUnit.SECONDS),"unknown neighbor at chunk border excludes false dry exit");
   check(worker.submit(()->dry(g,14,64,8)).get(2,TimeUnit.SECONDS),"known dry interior remains reachable");
   w.loaded.add("1,0");check(worker.submit(()->dry(g,15,64,8)).get(2,TimeUnit.SECONDS),"border exit allowed once neighbor data is available");
   w.loaded.clear();check(worker.submit(()->!dry(g,8,64,8)).get(2,TimeUnit.SECONDS),"unloaded old context rejected");
   w.loaded.add("0,0");controller.world=new class_3218();controller.world.loaded.add("0,0");
   check(worker.submit(()->!dry(g,8,64,8)).get(2,TimeUnit.SECONDS),"stale goal cannot reuse previous world data after dimension/world change");
   Object fresh=create(controller);check(worker.submit(()->dry(fresh,8,64,8)).get(2,TimeUnit.SECONDS),"fresh new-world goal remains usable");
   Object absent=create(new PlayerEngineController(null));check(worker.submit(()->!dry(absent,8,64,8)).get(2,TimeUnit.SECONDS),"missing world is not a dry exit");
   check(w.liveReads==0&&controller.world.liveReads==0,"all real packaged goal/heuristic calls avoid live-world block reads");
   check(w.cachedReads>0&&controller.world.cachedReads>0,"actual packaged methods use loaded cache boundaries");
   System.out.println("WaterEscapeGoalTest: "+checks+" packaged goal checks passed; world/cache/scheduler boundaries substituted, live Minecraft unverified");
  }finally{w.releaseLiveRead.countDown();worker.shutdownNow();}
 }
}
