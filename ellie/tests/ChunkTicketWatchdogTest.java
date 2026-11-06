import com.player2.playerengine.observatory.*;
import com.player2.playerengine.util.ChunkController;
import java.util.*;
import net.minecraft.*;
/** Native ChunkController delegation + independent maintenance; world/ticking are modeled, not Minecraft gameplay. */
public final class ChunkTicketWatchdogTest {
 public static class NPC {
  public class_3218 world=new class_3218();public double x=-23,z=-39;public boolean alive=true,removed;public int field_6012;public final UUID uuid=UUID.randomUUID();
  public class_3218 method_37908(){return world;}public boolean method_5805(){return alive;}public boolean method_31481(){return removed;}public UUID method_5667(){return uuid;}public double method_23317(){return x;}public double method_23321(){return z;}public double method_23318(){return 64;}
 }
 public static class Runner{public boolean enabled=true;public boolean isActive(){return enabled;}}
 public static class Tracker{public Object entity;public List<class_1923> chunks=new ArrayList<>();}
 public static class Server {public int tick;public int method_3780(){return tick;}}
 public static class Controller{public final NPC npc=new NPC();public final Tracker chunkLoader=new Tracker();public final Runner runner=new Runner();public final NPC owner=new NPC();Controller(){chunkLoader.entity=npc;}public NPC getEntity(){return npc;}public Runner getTaskRunner(){return runner;}public NPC getOwner(){return owner;}}
 static int checks;static void ok(boolean yes,String msg){checks++;if(!yes)throw new AssertionError(msg+" fields="+ObservatoryTelemetry.view());}
 static long packed(int x,int z){return DistanceDiagnostics.packedChunk(x,z);}
 public static void main(String[] args){
  ChunkTicketWatchdog.clear();Controller c=new Controller();
  ok(ChunkTicketWatchdog.reconcile(c,1)==4&&c.chunkLoader.chunks.size()==4,"independent tick bootstraps exact native four-cell footprint");
  c.npc.world.forced.values.clear();c.npc.world.manager.ticking.clear();
  ok(ChunkTicketWatchdog.reconcile(c,2)==4,"lost native flags restored without controller tick");
  c.npc.world.manager.ticking.clear();ChunkTicketWatchdog.reconcile(c,21);
  ok(c.npc.world.manager.ticking.size()==4,"saved forced flags alone cannot hide missing effective tickets");
  var old=new HashSet<>(c.npc.world.forced.values);c.npc.x=100;c.npc.z=100;ChunkTicketWatchdog.reconcile(c,22);
  ok(c.npc.world.forced.values.size()==4&&Collections.disjoint(old,c.npc.world.forced.values),"stale tracker footprint refreshed; old claims released before new acquisition");
  for(int i=0;i<60;i++){c.npc.x=i%2==0?-23:400.5;c.npc.z=i%2==0?-39:400.5;ChunkTicketWatchdog.reconcile(c,23+i);ok(c.npc.world.forced.values.size()<=4,"repeated boundary/teleport movement does not leak chunks");}
  class_3218 previous=c.npc.world;c.npc.world=new class_3218();ChunkTicketWatchdog.reconcile(c,100);
  ok(previous.forced.values.isEmpty()&&c.npc.world.forced.values.size()==4,"dimension change releases old world and retains new world footprint");
  ChunkTicketWatchdog.stopped(c);ok(c.npc.world.forced.values.isEmpty(),"explicit controller stop immediately releases ownership");
  c.runner.enabled=false;ChunkTicketWatchdog.reconcile(c,101);ok(c.npc.world.forced.values.isEmpty(),"stopped runner releases tickets");
  ChunkTicketWatchdog.load(c.npc.world,c.npc.uuid,2,3);ok(c.npc.world.forced.values.isEmpty(),"native loader cannot reacquire after stop");
  c.runner.enabled=true;ChunkTicketWatchdog.reconcile(c,102);ok(c.npc.world.forced.values.size()==4,"accepted new work can restore unavailable NPC chunks without executing a second controller");
  c.npc.alive=false;ChunkTicketWatchdog.reconcile(c,103);ok(c.npc.world.forced.values.isEmpty(),"death releases every owned claim");
  c.npc.alive=true;c.npc.removed=true;ChunkTicketWatchdog.reconcile(c,104);ok(c.npc.world.forced.values.isEmpty(),"despawn never resurrects or force-loads removed NPC");
  Controller missing=new Controller();ChunkTicketWatchdog.reconcile(missing,110);ChunkTicketWatchdog.retainControllers(List.of(),111);ok(missing.npc.world.forced.values.isEmpty(),"registry loss cleans owned footprint");
  class_3218 a=new class_3218(),b=new class_3218();UUID first=UUID.randomUUID(),second=UUID.randomUUID();ChunkController nativeLoader=new ChunkController();
  nativeLoader.load(a,first,4,5);nativeLoader.load(a,first,4,5);nativeLoader.load(a,second,4,5);nativeLoader.load(b,first,4,5);nativeLoader.unload(a,first,4,5);
  ok(a.forced.values.size()==1&&b.forced.values.size()==1,"real patched native loader deduplicates ownership, supports shared chunks and distinct worlds");
  nativeLoader.unload(a,second,4,5);ok(a.forced.values.isEmpty()&&b.forced.values.size()==1,"final owner releases correct world only");
  nativeLoader.clear();ok(b.forced.values.isEmpty()&&nativeLoader.size()==0,"native clear releases instead of forgetting forced chunks");
  class_3218 external=new class_3218();external.method_17988(4,5,true);nativeLoader.load(external,first,4,5);nativeLoader.unload(external,first,4,5);ok(external.forced.values.size()==1,"pre-existing external force flag preserved");
  Controller refused=new Controller();refused.npc.world.refuse=true;ok(ChunkTicketWatchdog.reconcile(refused,120)==0&&ObservatoryTelemetry.view().get("chunkTicketsLastFailure").contains("forced flag"),"failed force mutation logged, never claimed successful");
  refused.npc.world.refuse=false;ChunkTicketWatchdog.release(refused.npc.uuid);
  Controller reset=new Controller();ChunkTicketWatchdog.reconcile(reset,130);ChunkTicketWatchdog.reset(reset.chunkLoader);ok(reset.npc.world.forced.values.isEmpty()&&reset.chunkLoader.chunks.isEmpty(),"tracker reset releases all four cells rather than only current cell");
  for(int distance:new int[]{100,250,400})for(boolean explored:new boolean[]{true,false}){
   Controller scenario=new Controller();scenario.owner.world=scenario.npc.world;scenario.owner.x=scenario.npc.x+distance;scenario.owner.z=scenario.npc.z;
   if(explored)for(int x=-2;x<=-1;x++)for(int z=-3;z<=-2;z++)scenario.npc.world.generated.add(packed(x,z));
   int before=scenario.npc.world.generates;Server server=new Server();
   com.player2.playerengine.PlayerEngineController.staticControllers.put(scenario.npc.uuid,scenario);
   for(int tick=1000;tick<1021;tick++){
    server.tick=tick;DistanceDiagnostics.onServerTick(server);
    // Model the vanilla entity-tick eligibility boundary; never call AI from watchdog.
    if(scenario.npc.world.manager.ticking.contains(packed(-2,-3))){scenario.npc.field_6012++;DistanceDiagnostics.onControllerTick(scenario);}
   }
   ok(scenario.npc.field_6012==21&&scenario.npc.world.forced.values.size()==4,"actual global-hook path, modeled owner teleport "+distance+" explored="+explored);
   ok(ObservatoryTelemetry.view().get("distanceControllerTick").contains("TICKING")&&ObservatoryTelemetry.view().get("distanceNpcTick").contains("TICKING"),"global hook observes resumed modeled NPC/controller heartbeat separately from forced flags");
   ok(ObservatoryTelemetry.view().get("distanceOwner").startsWith(distance+".0"),"owner really moved "+distance+" blocks in modeled world");
   ok(explored?scenario.npc.world.generates==before:scenario.npc.world.generates==before+4,"modeled generation differs without changing execution policy");
   // Simulate accepted work while effective tickets are absent, then verify maintenance resumes eligibility.
   scenario.npc.world.forced.values.clear();scenario.npc.world.manager.ticking.clear();server.tick=1040;DistanceDiagnostics.onServerTick(server);
   ok(scenario.npc.world.manager.ticking.contains(packed(-2,-3)),"global callback restores eligibility while controller tick is absent");
   com.player2.playerengine.PlayerEngineController.staticControllers.clear();server.tick=1041;DistanceDiagnostics.onServerTick(server);
   ok(scenario.npc.world.forced.values.isEmpty(),"global registry cleanup releases remote NPC footprint");
   server.tick=1100;DistanceDiagnostics.onServerTick(server);
   ok(ObservatoryTelemetry.view().get("distanceControllerTick").contains("MISSING FROM REGISTRY"),"actual global callback reports vanished controller instead of leaving stale TICKING telemetry");
  }
  Controller boundary=new Controller();for(double coordinate:new double[]{-.01,0,.01,15.99,16,16.01}){
   boundary.npc.x=boundary.npc.z=coordinate;ChunkTicketWatchdog.reconcile(boundary,2000);
   int chunk=(int)Math.floor(coordinate/16);ok(boundary.npc.world.forced.values.contains(packed(chunk,chunk))&&boundary.npc.world.forced.values.size()<=4,"continuous/exact/negative boundary "+coordinate+" retains current cell within bounded footprint");
  }
  class_3218 failedOld=boundary.npc.world;failedOld.refuse=true;boundary.npc.world=new class_3218();ChunkTicketWatchdog.reconcile(boundary,2001);
  ok(!failedOld.forced.values.isEmpty(),"failed release retains accounting for retry instead of silently forgetting it");
  failedOld.refuse=false;ChunkTicketWatchdog.retainControllers(List.of(boundary),2002);
  ok(failedOld.forced.values.isEmpty()&&boundary.npc.world.forced.values.size()<=4,"old-world cleanup retries independently after failed release");
  ChunkTicketWatchdog.clear();System.out.println("ChunkTicketWatchdogTest: "+checks+" checks passed; real native ChunkController delegation, simulated world/ticket execution (Minecraft gameplay unverified)");
 }
}
