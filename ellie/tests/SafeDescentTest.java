package ellie.descent;
import java.util.*;
import com.player2.playerengine.reliability.SafeDescent;
import com.player2.playerengine.reliability.SafeDescent.Surface;
import com.player2.playerengine.reliability.SafeDescent.Probe;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import net.minecraft.class_2338;
import net.minecraft.class_2350.class_2351;
import static com.player2.playerengine.reliability.SafeDescent.*;

/** Deterministic geometry/game-boundary tests, not a Minecraft physics simulation. */
public final class SafeDescentTest {
 public static int checks;
 public static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static Surface cell(int kind){return new Surface(kind,1,"fixture");}
 static Probe floor(int height){return (x,y,z)->cell(y<=height?SOLID:AIR);}
 static boolean allowed(double from,int to,Probe p)throws Exception{return assess(from,0,to,0,p)==null;}
 public record Pos(int x,int y,int z){}
 public static final class Shape {
  final double top;Shape(double top){this.top=top;}
  public boolean method_1110(){return top==0;}
  public double method_1105(class_2351 axis){return top;}
 }
 public static final class Block {
  final String id;Block(String id){this.id=id;}
  public String toString(){return "Block{minecraft:"+id+"}";}
 }
 public static final class State {
  final String id;final double top;final int level;
  boolean inspectNeighbors;
  State(String id,double top,int level){this.id=id;this.top=top;this.level=level;}
  public Block method_26204(){return new Block(id);}
  public Shape method_26220(BlockView w,class_2338 p){
   if(inspectNeighbors)w.method_8320(new class_2338(p.x()+1,p.y(),p.z()));
   return new Shape(top);
  }
  public String toString(){return method_26204()+"[level="+level+"]";}
 }
 public interface BlockView {State method_8320(class_2338 p);}
 public static final class World implements BlockView {
  final Map<Pos,State> cells=new HashMap<>();public boolean loaded=true;
  boolean forbidLiveReads;int liveReads,cachedCollisionReads;
  public boolean method_22340(class_2338 p){return loaded;}
  State cachedState(class_2338 p){return cells.getOrDefault(new Pos(p.x(),p.y(),p.z()),new State("air",0,0));}
  public State method_8320(class_2338 p){liveReads++;if(forbidLiveReads)throw new IllegalStateException("worker queried live world; chunk request could wait for server");return cachedState(p);}
  public void put(int y,String id,double top){cells.put(new Pos(1,y,0),new State(id,top,0));}
 }
 public static final class CachedAccess implements BlockView {
  final World world;CachedAccess(World w){world=w;}
  public State method_8320(class_2338 p){world.cachedCollisionReads++;return world.cachedState(p);}
 }
 public static final class Bsi {public BlockView access;Bsi(World w){access=new CachedAccess(w);}}
 public static final class Context {
  public World world;public Bsi bsi;public boolean hasWaterBucket;
  Context(World w){world=w;bsi=new Bsi(w);}
  public boolean isLoaded(int x,int z){return world.loaded;}
  public State get(int x,int y,int z){return world.cachedState(new class_2338(x,y,z));}
 }
 public static final class Result {
  public int x=1,y,z=0;public double cost=2;
  Result(int y){this.y=y;}
 }
 public static final class Entity {
  public boolean grounded=true;public double y=134;
  public boolean method_24828(){return grounded;}
  public double method_23318(){return y;}
 }
 public static final class EntityContext {
  public World w;public Entity e=new Entity();
  EntityContext(World w){this.w=w;}
  public World world(){return w;}
  public Entity entity(){return e;}
 }
 public static final class Inputs {public int cleared;public void clearAllKeys(){cleared++;}}
 public static final class Baritone {public Inputs inputs=new Inputs();public Inputs getInputOverrideHandler(){return inputs;}}
 public static class Movement {
  public Pos src=new Pos(0,134,0),dest=new Pos(1,4,0);
  public Baritone baritone=new Baritone();public EntityContext ctx;
  Movement(World w){ctx=new EntityContext(w);}
 }
 static boolean edge(Context ctx,int from,int to){Result r=new Result(to);SafeDescent.filter(ctx,from,r);return r.cost<IMPOSSIBLE;}
 public static void main(String[] args)throws Exception {
  Probe noQuery=(x,y,z)->{throw new AssertionError("level bridge/ascent must not query landing");};
  check(allowed(134,134,noQuery),"outward level bridge over a void remains allowed");
  check(allowed(134,135,noQuery),"supported ascent remains native");
  for(int drop=1;drop<=3;drop++)check(allowed(134,134-drop,floor(133-drop)),"safe drop "+drop);
  for(int drop:new int[]{4,20,130})check(!allowed(134,134-drop,floor(133-drop)),"reject dry drop "+drop);
  check(!allowed(134,133,floor(3)),"one downward step into a deep shaft is not a safe landing");
  check(allowed(134,133,floor(132)),"solid support below excavated destination remains safe");
  check(!allowed(134,131,(x,y,z)->y==130?new Surface(SOLID,.5,"bottom slab"):cell(AIR)),"slab collision top increases actual drop to 3.5");
  check(allowed(134,132,(x,y,z)->y==131?new Surface(SOLID,.5,"bottom slab"):cell(AIR)),"safe 2.5-block slab landing");
  for(int bad:new int[]{UNKNOWN,HAZARD}){
   check(!allowed(134,133,(x,y,z)->cell(bad)),"unsafe destination "+bad);
   check(!allowed(134,133,(x,y,z)->cell(y==132?bad:AIR)),"unsafe support "+bad);
  }
  check(allowed(134,4,(x,y,z)->cell(WATER)),"existing source-water landing");
  check(allowed(134,4,(x,y,z)->cell(CLIMB)),"continuous ladder descent");
  check(!allowed(134,4,(x,y,z)->cell(y==100?AIR:CLIMB)),"ladder at bottom cannot protect a gap above it");
  check(!allowed(500,0,(x,y,z)->cell(CLIMB)),"bounded climb scan");
  for(int y=134;y>4;y--)check(allowed(y,y-1,floor(y-2)),"gradual stairs step "+y);

  World w=new World();w.put(3,"stone",1);Context ctx=new Context(w);
  check(!edge(ctx,134,4),"actual planner adapter rejects deep dry landing");
  check(ObservatoryTelemetry.events.stream().anyMatch(s->s.contains("DESCENT_EDGE_REJECTED")&&s.contains("dest=1,4,0")),"candidate rejection includes target/reason before movement");
  ctx.hasWaterBucket=true;check(!edge(ctx,134,4),"filled bucket cannot license an unvalidated planned fall");
  check(edge(ctx,134,134),"planner retains outward bridge");
  check(edge(ctx,7,4),"planner reads actual solid collision support");
  w.loaded=false;check(!edge(ctx,7,4),"unknown chunk rejected");w.loaded=true;
  w.put(3,"magma_block",1);check(!edge(ctx,7,4),"magma landing rejected");
  w.put(3,"fire_coral_block",1);check(edge(ctx,7,4),"safe coral must not be mistaken for fire");
  w.put(4,"lava",0);check(!edge(ctx,134,4),"lava destination rejected");
  w.put(4,"water",0);check(edge(ctx,134,4),"existing source water accepted by adapter");
  w.cells.put(new Pos(1,4,0),new State("water",0,3));check(!edge(ctx,134,4),"flowing water alone not credited");
  w.cells.clear();w.put(3,"stone",1);
  Movement m=new Movement(w);
  check(SafeDescent.rejectMovement(m),"live unsafe move veto before preparation");
  check(ObservatoryTelemetry.events.stream().anyMatch(s->s.contains("DESCENT_REJECTED")&&s.contains("130.0")),"specific rejection telemetry");
  m.src=new Pos(0,7,0);m.ctx.e.y=7;check(!SafeDescent.rejectMovement(m),"live three-block drop allowed");
  w.cells.clear();check(SafeDescent.rejectMovement(m),"stale path: removed landing rejected");
  m.src=new Pos(0,134,0);m.ctx.e.y=134;m.ctx.e.grounded=false;
  check(!SafeDescent.rejectMovement(m),"already falling: leave existing emergency controller intact");
  m.ctx=null;m.dest=new Pos(1,134,0);check(!SafeDescent.rejectMovement(m),"runtime outward bridge bypasses world and descent APIs");
  m.dest=new Pos(1,135,0);check(!SafeDescent.rejectMovement(m),"runtime ascent bypasses guard");
  m.dest=new Pos(1,4,0);check(SafeDescent.rejectMovement(m),"failed live boundary rejects downward movement");
  Result broken=new Result(4);SafeDescent.filter(new Object(),134,broken);check(broken.cost==IMPOSSIBLE,"failed planner boundary rejects candidate");

  // A bounded graph search exercises the real filter; adjacency/path executor are substituted.
  w.put(3,"stone",1);w.put(6,"stone",1);w.put(9,"stone",1);
  // 130 represents the far end of a same-height outward bridge at Y=13.
  Map<Integer,List<Integer>> graph=Map.of(13,List.of(4,130),130,List.of(10),10,List.of(7),7,List.of(4));
  List<Integer> route=route(ctx,graph,13,4);
  check(route.equals(List.of(13,130,10,7,4)),"reject fatal shortcut, retain bridge then safe staircase");
  check(route(ctx,Map.of(13,List.of(4)),13,4).isEmpty(),"no safe route returns bounded failure without jumping");
  System.out.println("SafeDescentTest: "+checks+" geometry/adapter checks passed; graph, world and physics substituted");
 }
 static List<Integer> route(Context ctx,Map<Integer,List<Integer>> graph,int start,int goal){
  ArrayDeque<List<Integer>> todo=new ArrayDeque<>();Set<Integer> seen=new HashSet<>();todo.add(List.of(start));
  while(!todo.isEmpty()){
   List<Integer> p=todo.remove();int node=p.getLast();if(!seen.add(node))continue;if(node==goal)return p;
   int y=node==130?13:node;
   for(int next:graph.getOrDefault(node,List.of()))if(edge(ctx,y,next==130?13:next)){
    List<Integer> copy=new ArrayList<>(p);copy.add(next);todo.add(copy);
   }
  }
  return List.of();
 }
}
