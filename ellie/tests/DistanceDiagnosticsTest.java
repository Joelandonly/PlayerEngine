import com.player2.playerengine.observatory.DistanceDiagnostics;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.util.*;

/** Tests actual telemetry using isolated objects that implement the intermediary API surface. */
public class DistanceDiagnosticsTest {
 public static class ForcedSet {
  final Set<Long> forced=new HashSet<>();
  public boolean contains(long p){return forced.contains(p);}
 }
 public static class World {
  final ForcedSet set=new ForcedSet();
  public ForcedSet method_17984(){return set;}
 }
 public static class Entity {
  public int field_6012=0;
  public double x,y,z;
  public World world;
  public final UUID uuid=UUID.randomUUID();
  public Entity(World w,double x,double y,double z){this.world=w;this.x=x;this.y=y;this.z=z;}
  public World method_37908(){return world;}
  public double method_23317(){return x;}
  public double method_23318(){return y;}
  public double method_23321(){return z;}
  public UUID method_5667(){return uuid;}
 }
 public static class Tracker {public final List<String> chunks=new ArrayList<>(List.of("A","B","C","D"));}
 public static class Controller {
  public final Tracker chunkLoader=new Tracker();
  public final Entity npc,owner;
  public Controller(Entity npc,Entity owner){this.npc=npc;this.owner=owner;}
  public Entity getEntity(){return npc;}
  public Entity getOwner(){return owner;}
 }
 static void check(boolean b,String desc){if(!b)throw new AssertionError(desc);System.out.println("PASS "+desc);}
 static String get(String name){return ObservatoryTelemetry.view().get(name);}
 public static void main(String[] args){
  World w=new World();Entity npc=new Entity(w,-33,64,-17);Entity owner=new Entity(w,500,64,-17);
  w.set.forced.add(DistanceDiagnostics.packedChunk(-3,-2));
  Controller ctl=new Controller(npc,owner);
  DistanceDiagnostics.sample(ctl,21);
  DistanceDiagnostics.noteControllerTickForTest(ctl,41);
  npc.field_6012=10;
  DistanceDiagnostics.sample(ctl,41);
  check(get("distanceOwner").endsWith("blocks"),"distance from owner is sampled");
  check(get("distanceForcedChunk").startsWith("YES"),"force check uses real chunk set including negative positions");
  check(get("distanceRequestedChunks").equals("4 tracked"),"reports tracked chunk count separately");
  check(get("distanceControllerTick").contains("TICKING"),"controller heartbeat observed");
  check(get("distanceNpcTick").contains("TICKING"),"age advances are observed");
  DistanceDiagnostics.sample(ctl,120);
  check(get("distanceControllerTick").contains("STALLED"),"controller stall detected without controller callbacks");
  check(get("distanceNpcTick").contains("STALLED"),"entity stall detected from unchanged age independently");
  npc.field_6012=20; DistanceDiagnostics.noteControllerTickForTest(ctl,140);DistanceDiagnostics.sample(ctl,140);
  check(get("distanceControllerTick").contains("TICKING")&&get("distanceNpcTick").contains("TICKING"),"tick recovery detected");
  npc.x=32;npc.z=32;DistanceDiagnostics.sample(ctl,160);
  check(get("distanceForcedChunk").startsWith("NO"),"unforced current chunk reported as no");
  owner.world=new World();DistanceDiagnostics.sample(ctl,180);
  check(get("distanceOwner").equals("different dimension"),"dimension mismatch cannot masquerade as numeric distance");
  DistanceDiagnostics.checkMissingForTest(240);
  check(get("distanceControllerTick").contains("MISSING FROM REGISTRY"),"independent server clock detects controller unregistration");
  List<String> lines=new ArrayList<>();DistanceDiagnostics.appendNavigationLines(lines,ObservatoryTelemetry.view());
  check(lines.stream().anyMatch(s->s.startsWith("NPC ENTITY TICK")),"existing Navigation tab gets diagnostic fields");
  System.out.println("DistanceDiagnosticsTest: 11 checks passed");
 }
}
