import com.player2.playerengine.observatory.ChunkTicketWatchdog;
import com.player2.playerengine.observatory.DistanceDiagnostics;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.util.*;

public final class ChunkTicketWatchdogTest {
 public static class Forced {
  final Set<Long> values=new HashSet<>();
  public boolean contains(long id){return values.contains(id);}
 }
 public static class World {
  final Forced forced=new Forced();int loads;
  public Forced method_17984(){return forced;}
  public boolean method_17988(int x,int z,boolean force){
   loads++;
   if(force)forced.values.add(DistanceDiagnostics.packedChunk(x,z));
   return true;
  }
 }
 public static class RefuseWorld extends World {
  @Override public boolean method_17988(int x,int z,boolean force){loads++;return false;}
 }
 public static class NPC {
  World world=new World();double x=-23,z=-39;boolean alive=true;
  public World method_37908(){return world;}
  public boolean method_5805(){return alive;}
  public UUID method_5667(){return UUID.fromString("00000000-0000-0000-0000-000000000001");}
  public double method_23317(){return x;}
  public double method_23321(){return z;}
 }
 public static class Chunk {
  public int field_9181,field_9180;
  Chunk(int x,int z){field_9181=x;field_9180=z;}
 }
 public static class Tracker {
  public List<Chunk> chunks=new ArrayList<>(List.of(new Chunk(-2,-3),new Chunk(-1,-3)));
 }
 public static class Controller {
  public Tracker chunkLoader=new Tracker();
  public NPC npc=new NPC();
  public NPC getEntity(){return npc;}
 }
 static void ok(boolean yes,String msg){
  if(!yes)throw new AssertionError(msg);
  System.out.println("PASS "+msg);
 }
 public static void main(String[] args){
  Controller ctl=new Controller();
  ok(ChunkTicketWatchdog.reconcile(ctl,20)==2,"restore missing tracked chunks by independent server tick");
  ok(ChunkTicketWatchdog.reconcile(ctl,40)==0 && ctl.npc.world.loads==2,"do not reload existing forced chunks");
  ctl.npc.world.forced.values.remove(DistanceDiagnostics.packedChunk(-2,-3));
  ok(ChunkTicketWatchdog.reconcile(ctl,60)==1,"restore a lost existing ticket without the controller ticking");
  ctl.chunkLoader.chunks.clear();
  ok(ChunkTicketWatchdog.reconcile(ctl,80)==0,"do not create tickets without existing tracker ownership");

  Controller stale=new Controller();stale.npc.x=100;stale.npc.z=100;
  ok(ChunkTicketWatchdog.reconcile(stale,100)==0 && stale.npc.world.loads==0,"never force an untracked current chunk");
  ok(String.valueOf(ObservatoryTelemetry.view().get("chunkTicketsLastFailure")).contains("absent"),
     "publish stale NPC position instead of silently failing");

  Controller refused=new Controller();refused.npc.world=new RefuseWorld();
  ok(ChunkTicketWatchdog.reconcile(refused,120)==0,"refused tickets are not reported as restored");
  ok(String.valueOf(ObservatoryTelemetry.view().get("chunkTicketsLastFailure")).contains("did not establish"),
     "failed vanilla ticket installation is visible in Observatory");

  Controller dead=new Controller();dead.npc.alive=false;
  ok(ChunkTicketWatchdog.reconcile(dead,140)==0 && dead.npc.world.loads==0,
     "do not keep a dead companion loaded");
  System.out.println("ChunkTicketWatchdogTest: 9 checks passed");
 }
}
