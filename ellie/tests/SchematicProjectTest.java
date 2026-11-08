import com.player2.playerengine.schematic.*;
import com.player2.playerengine.tasks.base.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;

/** Executes the real project coordinator; native workers/world/inventories are explicit substitutes. */
public final class SchematicProjectTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final State AIR=new State("minecraft:air",Map.of()),STONE=new State("minecraft:cobblestone",Map.of()),TRAP=new State("minecraft:oak_trapdoor",Map.of("open","true")),WATER=new State("minecraft:water",Map.of("level","0"));
 static SchematicPlan small(){int[] cells=new int[18];Arrays.fill(cells,0,9,1);cells[9]=2;cells[10]=3;return new SchematicPlan("small.litematic","abc",3,2,3,List.of(AIR,STONE,TRAP,WATER),cells);}
 static SchematicPlan slabs(){int[] cells=new int[420];Arrays.fill(cells,0,404,1);return new SchematicPlan("slabs.litematic","slab-log-replay",20,1,21,List.of(AIR,new State("minecraft:cobblestone_slab",Map.of("type","bottom"))),cells);}
 static class SlabEnv extends Env {
  final List<Integer> slabTargets=new ArrayList<>();
  SlabEnv(){super(slabs());allocateDepot(-1);placedDepots.add(0);inv.put("minecraft:cobblestone_slab",66);}
  public Task acquire(String id,int target){
   if(!id.equals("minecraft:cobblestone_slab"))return super.acquire(id,target);
   slabTargets.add(target);acquires++;
   return new Leaf(()->{if(!falseAcquire){int have=inv.getOrDefault(id,0);inv.put(id,have+6*((Math.max(0,target-have)+5)/6));}});
  }
 }
 static final class Leaf extends Task {final Runnable work;boolean done;Leaf(Runnable work){this.work=work;}void advance(){if(!done){work.run();done=true;}}public boolean isFinished(){return done;}public String toString(){return "substituted native worker";}}
 static class Env implements SchematicProjectTask.Environment {
  SchematicPlan plan;Pos origin;final Map<String,Integer> inv=new TreeMap<>();final List<Map<String,Integer>> chests=new ArrayList<>();final List<Pos> depots=new ArrayList<>();final Set<Integer> placedDepots=new HashSet<>();final Map<Pos,State> world=new HashMap<>();final List<String> events=new ArrayList<>();
  Map<String,Integer> bill=Map.of();boolean near=true,closed,fluids,active,full,obstructed,changed,unloaded,stall,falseDeposit,falseAcquire,finishedPostNoOutput,wrongFinal,supplyLost,conflictOnRecheck,lateConflict;int layer,verified,places,acquires,transfers,closeCalls;String mismatch,lastAcquire,siteConflict;
  Env(SchematicPlan p){plan=p;inv.put("minecraft:diamond_pickaxe",1);}
  public CompletableFuture<SchematicPlan> load(String f){return CompletableFuture.completedFuture(plan);}
  public void initialize(SchematicPlan p,Pos o){origin=o;}
  public void checkWorld(){if(changed)throw new IllegalStateException("world changed");if(unloaded)throw new IllegalStateException("chunk unavailable");}
  State current(Cell c){return world.getOrDefault(c.pos(),AIR);}
  boolean same(Cell c){return current(c).equals(c.state());}
  boolean base(Cell c){return c.state().water()||same(c)||current(c).equals(c.state().placementState());}
  public boolean preflight(){
   Map<String,Integer> remaining=new TreeMap<>();mismatch=null;for(int y=0;y<plan.height;y++)for(int z=0;z<plan.length;z++)for(int x=0;x<plan.width;x++){
    Cell c=new Cell(new Pos(x,y,z),plan.at(x,y,z));if(!same(c)&&mismatch==null)mismatch=c.pos()+" expected="+c.state();
    if(!base(c)&&c.state().placementItems()>0)remaining.merge(c.state().id(),c.state().placementItems(),Integer::sum);
    if((c.state().source()||c.state().waterlogged())&&!same(c))remaining.put("minecraft:water_bucket",1);
   }bill=Map.copyOf(remaining);return true;
  }
  public void recheck(){if(conflictOnRecheck)siteConflict="wall_torch at 104,106,289 expected air";}public String buildSiteConflict(){return siteConflict;}public Map<String,Integer> requirements(){return bill;}public String worldMismatch(){return mismatch;}
  public List<Pos> depotPositions(){return List.copyOf(depots);}
  public void allocateDepot(int replacement){if(replacement>=0){depots.set(replacement,new Pos(20+replacement*2,64,20+places));return;}if(depots.size()>=64)throw new IllegalStateException("storage cap");depots.add(new Pos(20+depots.size()*2,64,20));chests.add(new TreeMap<>());}
  public boolean depotPresent(int i){if(obstructed&&placedDepots.contains(i))throw new IllegalStateException("BLOCKED_DEPOT_OBSTRUCTED");return placedDepots.contains(i);}
  public List<SchematicProjectTask.Depot> depots(){List<SchematicProjectTask.Depot> all=new ArrayList<>();for(int i=0;i<depots.size();i++){
   Map<String,Integer> stock=chests.get(i),merge=new HashMap<>();int used=0;for(var e:stock.entrySet()){int max=maxStack(e.getKey());used+=(e.getValue()+max-1)/max;if(e.getValue()%max!=0)merge.put(e.getKey(),max-e.getValue()%max);}
   all.add(new SchematicProjectTask.Depot(depots.get(i),stock,merge,Math.max(0,27-used)));
  }return all;}
  public int maxStack(String id){return id.contains("bucket")||id.contains("pickaxe")?1:64;}
  public Map<String,Integer> inventory(){return Map.copyOf(inv);}public int freeSlots(){if(full)return 0;int n=0;for(var e:inv.entrySet())n+=(e.getValue()+maxStack(e.getKey())-1)/maxStack(e.getKey());return Math.max(0,36-n);}
  public boolean atDepot(){return near;}public Task travelToDepot(){return new Leaf(()->near=true);}
  public Task acquire(String id,int target){lastAcquire=id;acquires++;return new Leaf(()->{if(!falseAcquire)inv.put(id,target);});}
  public Task placeDepot(int i){places++;return new Leaf(()->{placedDepots.add(i);inv.merge("minecraft:chest",-1,Integer::sum);});}
  public Task transfer(int i,String id,int n,boolean deposit){transfers++;return new Leaf(()->{inv.merge(id,deposit?-n:n,Integer::sum);if(!falseDeposit)chests.get(i).merge(id,deposit?n:-n,Integer::sum);});}
  public SchematicProjectTask.Section section(int y){
   if(active&&!stall){for(int z=0;z<plan.length;z++)for(int x=0;x<plan.width;x++){
    Cell c=new Cell(new Pos(x,y,z),plan.at(x,y,z));if(base(c))continue;String id=c.state().id();int have=inv.getOrDefault(id,0);if(c.state().air()||have>0){world.put(c.pos(),c.state().placementState());if(!c.state().air())inv.put(id,have-1);active=false;break;}
   }}
   int base=0,fin=0;Map<String,Integer> need=new TreeMap<>();List<Cell> post=new ArrayList<>();String problem=null;
   for(int z=0;z<plan.length;z++)for(int x=0;x<plan.width;x++){
    Cell c=new Cell(new Pos(x,y,z),plan.at(x,y,z));boolean b=base(c),f=same(c)||(!fluids&&c.state().water());if(b)base++;if(f)fin++;else problem="state mismatch at "+c.pos();
    if(!b&&c.state().placementItems()>0)need.merge(c.state().id(),c.state().placementItems(),Integer::sum);
    if(!f&&b&&(c.state().interactiveOpen()||c.state().source()||c.state().doubleSlab()))post.add(c);
   }return new SchematicProjectTask.Section(plan.width*plan.length,base,fin,problem,need,post);
  }
  public void startSection(int y){layer=y;active=true;if(lateConflict)siteConflict="protected chest appeared at active target";}public void pauseBuilder(){active=false;}public boolean builderActive(){return active;}public void enableFluids(){fluids=true;}
  public Task postAction(Cell c){return new Leaf(()->{if(!finishedPostNoOutput){world.put(c.pos(),c.state());if(c.state().source()){inv.put("minecraft:water_bucket",0);inv.merge("minecraft:bucket",1,Integer::sum);}}});}
  public boolean cellComplete(Cell c){return same(c);}
  public void checkpoint(String s,int l){if(wrongFinal&&s.equals("BUILDING")&&l>=plan.layers.size())world.put(new Pos(0,0,0),AIR);if(supplyLost&&s.equals("PREPARED"))for(var c:chests)c.clear();}
  public void verified(String evidence){verified++;}public void report(String e,String d){events.add(e+":"+d);}public void close(){closed=true;active=false;closeCalls++;}
 }
 public record Point(int x,int y,int z){public int method_10263(){return x;}public int method_10264(){return y;}public int method_10260(){return z;}}
 public static final class Chain{public Task mainTask;}
 public record Controller(Chain chain){public Chain getUserTaskChain(){return chain;}}
 static final Method START,TICK,STOP;
 static{try{START=SchematicProjectTask.class.getDeclaredMethod("onStart");TICK=SchematicProjectTask.class.getDeclaredMethod("onTick");STOP=SchematicProjectTask.class.getDeclaredMethod("onStop",Task.class);for(Method m:List.of(START,TICK,STOP))m.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static SchematicProjectTask task(Env e,boolean build)throws Exception{var t=new SchematicProjectTask("small.litematic",0,64,0,build,e);START.invoke(t);return t;}
 static void tick(SchematicProjectTask t)throws Exception{Task child=(Task)TICK.invoke(t);if(child instanceof Leaf leaf)leaf.advance();}
 static void run(SchematicProjectTask t,int bound)throws Exception{for(int i=0;i<bound&&!t.isFinished();i++)tick(t);check(t.isFinished(),"Coordinator failed to terminate within "+bound+" ticks");}
 public static void main(String[]a)throws Exception{
  Env e=new Env(small());var t=task(e,false);run(t,500);check(t.isSuccessful()&&e.closed,"prepare completes through actual storage checks");check(e.chests.getFirst().get("minecraft:cobblestone")==9,"full bill staged");check(e.inv.get("minecraft:diamond_pickaxe")==1,"owner gear retained");check(e.world.isEmpty(),"prepare does not construct blueprint");
  e=new Env(small());e.near=false;t=task(e,true);run(t,2000);check(t.isSuccessful(),t.controlledReason());check(e.verified>0&&e.fluids,"new world placements credited, fluids deferred");check(e.inv.get("minecraft:diamond_pickaxe")==1,"build retained unrelated tool");check(e.world.get(new Pos(0,1,0)).equals(TRAP)&&e.world.get(new Pos(1,1,0)).equals(WATER),"final open trapdoor and source verified");
  e=new Env(small());for(Cell c:e.plan.targets)e.world.put(c.pos(),c.state());t=task(e,true);run(t,500);check(t.isSuccessful()&&e.verified==0,"existing completed blocks don't count as new work");check(e.acquires==1,"only depot chest acquired for completed build");
  e=new Env(small());for(int x=0;x<3;x++)e.world.put(new Pos(x,0,0),STONE);e.inv.put("minecraft:cobblestone",6);t=task(e,false);run(t,500);check(t.isSuccessful()&&e.chests.getFirst().get("minecraft:cobblestone")==6,"partial world/inventory reduces live bill");
  e=new Env(small());e.falseAcquire=true;t=task(e,true);run(t,100);check(!t.isSuccessful()&&t.controlledReason().contains("ACQUIRE"),"native finished without acquisition is blocked");
  e=new Env(small());e.falseDeposit=true;t=task(e,false);run(t,100);check(!t.isSuccessful()&&t.controlledReason().contains("DEPOSIT"),"inventory loss alone cannot verify deposit");
  e=new Env(small());e.full=true;t=task(e,false);run(t,100);check(!t.isSuccessful()&&t.controlledReason().contains("INVENTORY_CAPACITY"),"actual full inventory blocks without dumping");
  e=new Env(small());e.stall=true;t=task(e,true);run(t,2000);check(!t.isSuccessful()&&t.controlledReason().contains("PLACEMENT_STALLED"),"busy native builder is bounded");
  e=new Env(small());e.finishedPostNoOutput=true;t=task(e,true);run(t,2000);check(!t.isSuccessful()&&t.controlledReason().contains("POST"),"completed click without world change is blocked");
  e=new Env(small());e.obstructed=true;t=task(e,false);run(t,100);check(!t.isSuccessful()&&t.controlledReason().contains("OBSTRUCTED"),"obstructed project storage is explicit");
  e=new Env(small());t=task(e,true);tick(t);e.changed=true;run(t,100);check(!t.isSuccessful()&&e.closed,"world change cleans up owner scope");
  e=new Env(small());e.unloaded=true;t=task(e,true);run(t,100);check(!t.isSuccessful(),"unloaded chunk cannot imply completion");
  e=new Env(small());e.supplyLost=true;t=task(e,true);run(t,2000);check(t.isSuccessful(),"lost prepared stock reacquired using bounded repair");
  e=new Env(small());t=task(e,true);for(int i=0;i<4;i++)tick(t);check(t.prepareForTransientResume(TaskSuspensionCause.GESTURE_OVERLAY),"existing transient suspension contract");t.afterChildrenStopped();run(t,2000);check(t.isSuccessful(),"suspension resumes through fresh postconditions");
  e=new Env(small());t=task(e,true);tick(t);STOP.invoke(t,new Object[]{null});check(t.isTerminal()&&!t.isSuccessful()&&e.closed,"ordinary stop retains explicit interruption");
  e=new Env(small());e.wrongFinal=true;t=task(e,true);run(t,2000);check(!t.isSuccessful()&&t.controlledReason().contains("FINAL_WORLD_MISMATCH"),"damage to a previous section cannot pass final verification");
  e=new Env(small());t=task(e,false);Chain chain=new Chain();chain.mainTask=t;Controller controller=new Controller(chain);tick(t);tick(t);
  check(SchematicProjectTask.insideProject(controller,new Point(1,80,1)),"active project owns only its blueprint column");check(!SchematicProjectTask.insideProject(controller,new Point(4,80,1)),"other worksite columns unaffected");
  check(SchematicProjectTask.reservedSource(controller,new Point(20,64,20)),"project depot excluded from acquisition sources");
  for(int i=0;i<100&&!"minecraft:water_bucket".equals(e.lastAcquire);i++)tick(t);
  check(SchematicProjectTask.rejectWaterSource(controller,new Point(1,65,1)),"bucket collection cannot steal already built project water");check(!SchematicProjectTask.rejectWaterSource(controller,new Point(10,65,1)),"outside natural water sources remain allowed");
  run(t,1000);check(!SchematicProjectTask.insideProject(controller,new Point(1,80,1))&&!SchematicProjectTask.reservedSource(controller,new Point(20,64,20)),"terminal project releases exclusions");
  e=new Env(small());e.siteConflict="wall_torch at 104,106,289 expected air";t=task(e,false);run(t,500);
  check(t.isSuccessful()&&e.chests.getFirst().get("minecraft:cobblestone")==9,"prepare stages materials despite a protected selection-air conflict");
  check(t.controlledReason().contains("104,106,289")&&t.controlledReason().contains("construction has not started"),"prepared outcome names unresolved site without claiming construction success");
  check(e.events.stream().filter(x->x.startsWith("SCHEMATIC_SITE_WARNING")).count()==1,"stable site conflict warns once across preflight/recheck");
  check(e.world.isEmpty()&&!e.active,"prepare never clears or builds the obstructed selection");
  e=new Env(small());e.siteConflict="wall_torch at 104,106,289 expected air";t=task(e,true);run(t,100);
  check(!t.isSuccessful()&&t.controlledReason().startsWith("BLOCKED_SCHEMATIC_PROTECTED_SITE")&&e.acquires==0&&e.depots.isEmpty(),"build rejects the same conflict before stock acquisition or any placement");
  e=new Env(small());e.conflictOnRecheck=true;t=task(e,false);run(t,500);check(t.isSuccessful()&&t.controlledReason().contains("104,106,289"),"new protection at preparation's final check remains a reported site issue");
  e=new Env(small());e.conflictOnRecheck=true;t=task(e,true);run(t,500);check(!t.isSuccessful()&&!e.active,"build rechecks protection after material stocking");
  e=new Env(small());e.lateConflict=true;t=task(e,true);run(t,500);check(!t.isSuccessful()&&t.controlledReason().contains("PROTECTED_SITE")&&e.closed&&!e.active,"late protected conflict stops the owned builder");
  int[] large=new int[2000];Arrays.fill(large,1);var big=new SchematicPlan("big.litematic","def",40,1,50,List.of(AIR,STONE),large);e=new Env(big);t=task(e,false);run(t,1000);check(t.isSuccessful()&&e.depots.size()==2,"more than carrying and single-chest capacity is staged in multiple chests");
  SlabEnv slab=new SlabEnv();t=task(slab,false);run(t,500);
  check(t.isSuccessful()&&slab.chests.getFirst().get("minecraft:cobblestone_slab")==404,"logged 66 carried slabs count toward the full 404-slab project");
  check(slab.slabTargets.equals(List.of(64,64,64,64,64,8)),"successive six-output crafts request only remaining material in bounded batches");
  check(slab.inv.get("minecraft:cobblestone_slab")==4&&slab.inv.get("minecraft:diamond_pickaxe")==1,"recipe surplus and owner gear are retained");
  check(slab.events.stream().anyMatch(s->s.contains("required=404 stored=66 carried=0 remainingToGather=338 batchTarget=64")),"next-batch logging preserves verified stored slabs instead of showing a total of zero");
  slab=new SlabEnv();slab.inv.put("minecraft:cobblestone_slab",0);slab.chests.getFirst().put("minecraft:cobblestone_slab",66);t=task(slab,false);
  for(int i=0;i<100&&slab.chests.getFirst().get("minecraft:cobblestone_slab")<198;i++)tick(t);
  int before=slab.chests.getFirst().get("minecraft:cobblestone_slab");STOP.invoke(t,new Object[]{null});t=task(slab,false);run(t,500);
  check(before>=198&&t.isSuccessful()&&slab.chests.getFirst().get("minecraft:cobblestone_slab")==404,"restarting midway reuses actual chest stock without redoing completed batches");
  slab=new SlabEnv();slab.inv.put("minecraft:cobblestone_slab",0);slab.chests.getFirst().put("minecraft:cobblestone_slab",66);slab.falseAcquire=true;t=task(slab,false);run(t,100);
  check(!t.isSuccessful()&&t.controlledReason().contains("stored=66")&&t.controlledReason().contains("remainingToGather=338"),"failed later batch reports specific remaining work and retained storage");
  check(slab.chests.getFirst().get("minecraft:cobblestone_slab")==66,"failure never discards or falsely replaces staged stock");
  System.out.println("SchematicProjectTest: "+checks+" full coordinator success/failure checks; game actions and world substituted, no live gameplay claim");
 }
}
