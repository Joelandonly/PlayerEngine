package com.player2.playerengine.schematic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.tasks.setup.CoreGoalJournal;
import com.player2.playerengine.tasks.setup.SetupBuildZone;
import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import com.player2.playerengine.reliability.WorksiteAnchor;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;

/** Server-thread game boundary. File parsing alone is asynchronous; no worker reads live chunks. */
public final class SchematicWorld implements SchematicProjectTask.Environment {
    private static final String P="com.player2.playerengine.";
    private final PlayerEngineController controller;
    private final Object world;
    private SchematicPlan plan;
    private Pos origin;
    private String dimension,character,key;
    private Path worldRoot;
    private CoreGoalJournal journal;
    private final List<Pos> positions=new ArrayList<>();
    private final List<SetupBuildZone> zones=new ArrayList<>();
    private final Map<State,Object> states=new HashMap<>();
    private final Map<String,Object> items=new HashMap<>();
    private final Set<Pos> protectedPositions=new HashSet<>();
    private final AtomicReference<Set<Pos>> protectedSnapshot=new AtomicReference<>(Set.of());
    private Object extra,ownedSchematic;
    private Predicate<Object> breakGuard;
    private int paletteCursor,scanCursor,savedLayer,sectionY=-1,sectionCursor;
    private boolean scanComplete,closed,missingWater,missingSolid,fluids;
    private String mismatch;
    private int siteConflicts;
    private final List<String> siteConflictSamples=new ArrayList<>();
    private Map<String,Integer> requirements=new TreeMap<>(),scanBill=new TreeMap<>();
    private int sectionBase,sectionFinal;
    private String sectionMismatch;
    private final Map<String,Integer> sectionBill=new TreeMap<>();
    private final List<Cell> sectionPost=new ArrayList<>();

    public SchematicWorld(PlayerEngineController controller)throws Exception {
        this.controller=Objects.requireNonNull(controller);world=call(controller,"getWorld");
        if(world==null||call(controller,"getEntity")==null)throw new IllegalStateException("NPC/world unavailable");
    }
    public CompletableFuture<SchematicPlan> load(String file)throws Exception {
        Path game=(Path)callStatic(P+"automaton.utils.DirUtil","getGameDir");Path directory=game.resolve("schematics");Files.createDirectories(directory);
        return CompletableFuture.supplyAsync(()->{try{return SchematicFiles.read(directory,file);}catch(Exception e){throw new CompletionException(e);}});
    }
    public void checkWorld()throws Exception {if(closed)throw new IllegalStateException("Project scope closed");if(call(controller,"getWorld")!=world||call(controller,"getEntity")==null)throw new IllegalStateException("NPC/world changed; rerun the saved project in its dimension");}
    public void initialize(SchematicPlan plan,Pos origin)throws Exception {
        this.plan=plan;this.origin=origin;
        int min=((Number)call(world,"method_31607")).intValue(),max=((Number)call(world,"method_31600")).intValue();
        if(origin.y()<min||(long)origin.y()+plan.height>max)throw new IllegalStateException("Blueprint exceeds world build height "+min+".."+(max-1));
        dimension=String.valueOf(call(call(world,"method_27983"),"method_29177"));
        Object store=callStatic(P+"tasks.deferred.DeferredJobStore","get");if(store==null)throw new IllegalStateException("World job store unavailable");
        worldRoot=(Path)call(store,"worldRoot");character=String.valueOf(call(call(controller,"getAIPersistantData"),"getCharacterId"));
        key="schematic_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((plan.sha256+"|"+dimension+"|"+origin.key()).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24);
        journal=CoreGoalJournal.open(worldRoot,character,key);if(!journal.isPersistent())throw new IllegalStateException("Cannot persist schematic project: "+journal.error());
        String[] saved=journal.get("detail","").split("\\|",-1);
        if(saved.length==3&&saved[0].equals(plan.sha256)&&saved[1].equals(origin.key())){
            int n=Integer.parseInt(saved[2]);if(n<0||n>64)throw new IllegalStateException("Invalid saved depot count");
            for(int i=0;i<n;i++){
                CoreGoalJournal d=CoreGoalJournal.open(worldRoot,character,key+"_depot_"+i);
                if(!d.get("resource","").equals(dimension))throw new IllegalStateException("Saved depot dimension mismatch");
                positions.add(Pos.parse(d.get("detail","")));
            }
        }
        zones.add(new SetupBuildZone(controller,origin.x(),origin.z(),plan.width,plan.length));
        for(Pos p:positions)protectDepot(p);
        WorksiteAnchor.open(this,controller,position(origin),"schematic:"+plan.sha256);
        extra=call(controller,"getExtraBaritoneSettings");breakGuard=p->{try{return protectedSnapshot.get().contains(point(p));}catch(Exception e){return false;}};
        synchronized(call(extra,"getBreakMutex")){((List<Object>)call(extra,"getBreakAvoiders")).add(breakGuard);}
        checkpoint("IMPORT_VALIDATED",0);
    }
    public static Object position(Pos p)throws Exception {return FoodAcquisition.construct("net.minecraft.class_2338",p.x(),p.y(),p.z());}
    public static Pos point(Object p)throws Exception{return new Pos(((Number)call(p,"method_10263")).intValue(),((Number)call(p,"method_10264")).intValue(),((Number)call(p,"method_10260")).intValue());}
    private Pos absolute(Pos p){return origin.add(p.x(),p.y(),p.z());}
    private boolean loaded(Pos p)throws Exception{return Boolean.TRUE.equals(call(world,"method_22340",position(p)));}
    private Object state(Pos p)throws Exception {if(!loaded(p))throw new IllegalStateException("BLOCKED_CHUNK_UNLOADED at "+p.key());return call(world,"method_8320",position(p));}
    public static Map<String,String> properties(Object state)throws Exception {
        Map<String,String> values=new TreeMap<>();
        for(Object property:(Collection<?>)call(state,"method_28501")){
            Object value=call(state,"method_11654",property);values.put(String.valueOf(call(property,"method_11899")),String.valueOf(call(property,"method_11901",value)));
        }return values;
    }
    private static Object registry(String field)throws Exception{return Class.forName("net.minecraft.class_7923").getField(field).get(null);}
    private static Object lookup(String field,String id)throws Exception {
        Object reg=registry(field),identifier=callStatic("net.minecraft.class_2960","method_60654",id),value=call(reg,"method_10223",identifier);
        if(value==null||!id.equals(String.valueOf(call(reg,"method_10221",value))))throw new IllegalArgumentException("Unknown registered ID "+id);return value;
    }
    private static String idOfBlock(Object state)throws Exception{return String.valueOf(call(registry("field_41175"),"method_10221",call(state,"method_26204")));}
    private static String idOfItem(Object item)throws Exception{return String.valueOf(call(registry("field_41178"),"method_10221",item));}
    private Object item(String id)throws Exception {Object found=items.get(id);if(found==null){found=lookup("field_41178",id);items.put(id,found);}return found;}
    private Object resolved(State spec)throws Exception {
        Object cached=states.get(spec);if(cached!=null)return cached;
        Object block=lookup("field_41175",spec.id()),result=call(block,"method_9564");Map<String,Object> definitions=new HashMap<>();
        for(Object prop:(Collection<?>)call(result,"method_28501"))definitions.put(String.valueOf(call(prop,"method_11899")),prop);
        for(var entry:spec.properties().entrySet()){
            Object prop=definitions.get(entry.getKey());if(prop==null)throw new IllegalArgumentException("Unknown block property "+spec.id()+"["+entry.getKey()+"]");
            Optional<?> value=(Optional<?>)call(prop,"method_11900",entry.getValue());if(value.isEmpty())throw new IllegalArgumentException("Invalid block state "+spec);
            result=call(result,"method_11657",prop,value.get());
        }
        if(!spec.air()&&!spec.water()){
            Object asItem=call(block,"method_8389");if(idOfItem(asItem).equals("minecraft:air"))throw new IllegalArgumentException("No survival placement item for "+spec.id());
            if(!Class.forName("net.minecraft.class_1747").isInstance(asItem))throw new IllegalArgumentException("Native schematic placement does not support this non-block item: "+spec.id());
            items.put(spec.id(),asItem);
        }
        if(spec.doubleSlab()&&spec.waterlogged())throw new IllegalArgumentException("Double slabs cannot retain waterlogging in survival: "+spec);
        if(spec.id().equals("minecraft:lava"))throw new IllegalArgumentException("Lava placement is not supported by this survival importer");
        if(spec.properties().getOrDefault("occupied","false").equals("true"))throw new IllegalArgumentException("Occupied bed state requires an entity; export an unoccupied blueprint");
        states.put(spec,result);return result;
    }
    private String placementItem(State desired)throws Exception {return idOfItem(call(call(resolved(desired),"method_26204"),"method_8389"));}
    private static State dryFinal(State desired){if(!desired.waterlogged())return desired;Map<String,String> props=new TreeMap<>(desired.properties());props.put("waterlogged","false");return new State(desired.id(),props);}
    private boolean finalMatch(Object current,State desired)throws Exception {return current.equals(resolved(desired));}
    private boolean baseMatch(Object current,State desired)throws Exception {
        if(desired.water())return true; // fluid work is explicitly deferred until the solid structure exists
        if(finalMatch(current,desired)||(desired.waterlogged()&&finalMatch(current,dryFinal(desired))))return true;
        Object base=resolved(desired.placementState());
        if(desired.id().equals("minecraft:chest"))return SchematicBuildSupport.sameIgnoringChestJoin(current,base);
        return current.equals(base);
    }
    private int missingItems(Object current,State desired)throws Exception {
        if(desired.placementItems()==0)return 0;
        if(finalMatch(current,desired))return 0;
        if(desired.doubleSlab()&&idOfBlock(current).equals(desired.id()))return 1;
        if(baseMatch(current,desired))return 0;
        return desired.placementItems();
    }
    private boolean protectedConflict(Pos p,Object current,State desired)throws Exception {
        if(Boolean.TRUE.equals(call(current,"method_26215"))||(!desired.water()&&baseMatch(current,desired)))return false;
        if(call(world,"method_8321",position(p))!=null)return true;
        Object store=callStatic(P+"structureprotection.PlayerPlacedBlockStore","get");
        return store!=null&&Boolean.TRUE.equals(call(store,"contains",dimension,position(p)));
    }
    private void recordSiteConflict(Pos p,Object current,State desired)throws Exception {
        if(!protectedConflict(p,current,desired))return;
        siteConflicts++;
        if(siteConflictSamples.size()<8)siteConflictSamples.add("at "+p.key()+"; expected="+desired+" actual="+current);
        protectedPositions.add(p);
    }
    public boolean preflight()throws Exception {
        checkWorld();
        if(paletteCursor<plan.palette.size()){
            int end=Math.min(plan.palette.size(),paletteCursor+32);
            for(;paletteCursor<end;paletteCursor++){State s=plan.palette.get(paletteCursor);resolved(s);resolved(s.placementState());}
            return false;
        }
        if(scanComplete)return true;
        int end=Math.min(plan.width*plan.height*plan.length,scanCursor+500);
        for(;scanCursor<end;scanCursor++){
            int x=scanCursor%plan.width,z=(scanCursor/plan.width)%plan.length,y=scanCursor/(plan.width*plan.length);
            State desired=plan.at(x,y,z);Pos p=origin.add(x,y,z);Object current=state(p);
            boolean matches=finalMatch(current,desired);
            if(!matches&&mismatch==null)mismatch=p.key()+" expected="+desired+" actual="+current;
            recordSiteConflict(p,current,desired);
            int n=missingItems(current,desired);if(n>0)scanBill.merge(placementItem(desired),n,Math::addExact);
            missingSolid|=!desired.air()&&!desired.water()&&!matches;
            missingWater|=(desired.source()||desired.waterlogged())&&!matches;
            if(!desired.air()&&(matches||baseMatch(current,desired)))protectedPositions.add(p);
        }
        if(scanCursor<plan.width*plan.height*plan.length)return false;
        if(missingWater){if(dimension.endsWith(":the_nether"))throw new IllegalStateException("Water cannot be placed in the Nether");scanBill.put("minecraft:water_bucket",1);}
        if(missingSolid&&plan.layers.size()>2)scanBill.merge("minecraft:cobblestone",64,Math::addExact);
        requirements=Map.copyOf(scanBill);scanComplete=true;publishProtection();
        return true;
    }
    public void recheck(){scanCursor=0;scanComplete=false;scanBill=new TreeMap<>();mismatch=null;missingWater=missingSolid=false;siteConflicts=0;siteConflictSamples.clear();}
    public Map<String,Integer> requirements(){return requirements;}
    public String worldMismatch(){return mismatch;}
    public String buildSiteConflict(){return siteConflicts==0?null:"Protected build-site conflicts="+siteConflicts+"; "+String.join(" | ",siteConflictSamples);}
    public List<Pos> depotPositions(){return List.copyOf(positions);}
    public boolean atDepot()throws Exception {
        Pos p=positions.isEmpty()?origin:positions.getFirst();Object e=call(controller,"getEntity");
        double dx=((Number)call(e,"method_23317")).doubleValue()-(p.x()+.5),dy=((Number)call(e,"method_23318")).doubleValue()-p.y(),dz=((Number)call(e,"method_23321")).doubleValue()-(p.z()+.5);
        return dx*dx+dy*dy+dz*dz<=36;
    }
    public Task travelToDepot()throws Exception {return task("tasks.movement.GetToBlockTask",position(positions.isEmpty()?origin:positions.getFirst()),false);}
    private boolean air(Pos p)throws Exception{return Boolean.TRUE.equals(call(state(p),"method_26215"));}
    private boolean solid(Pos p)throws Exception {Object s=state(p);String id=idOfBlock(s);return !id.contains("magma")&&!id.contains("cactus")&&!id.contains("campfire")&&Boolean.TRUE.equals(call(s,"method_26234",world,position(p)));}
    private boolean outside(Pos p){return p.x()<origin.x()||p.x()>=origin.x()+plan.width||p.z()<origin.z()||p.z()>=origin.z()+plan.length;}
    private boolean depotGeometry(Pos p)throws Exception {
        if(!outside(p)||!loaded(p)||!loaded(p.add(0,-2,0))||!air(p)||!air(p.add(0,1,0)))return false;
        if(!solid(p.add(0,-1,0))&&!(air(p.add(0,-1,0))&&solid(p.add(0,-2,0))))return false;
        for(Pos old:positions)if(Math.abs(old.x()-p.x())+Math.abs(old.z()-p.z())<2)return false;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            Pos side=p.add(d[0],0,d[1]);if(!loaded(side)||idOfBlock(state(side)).contains("chest"))return false;
        }
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){Pos side=p.add(d[0],0,d[1]);if(air(side)&&air(side.add(0,1,0))&&solid(side.add(0,-1,0)))return true;}
        return false;
    }
    public void allocateDepot(int replaceIndex)throws Exception {
        if(positions.size()>=64&&replaceIndex<0)throw new IllegalStateException("Project needs more than 64 depot chests; split the preparation into smaller projects");
        Pos selected=null;
        for(int radius=3;radius<=18&&selected==null;radius++)for(int dx=-radius;dx<=radius&&selected==null;dx++)for(int dz=-radius;dz<=radius&&selected==null;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy:new int[]{0,-1,1,-2,2}){Pos p=origin.add(dx,dy,dz);if(depotGeometry(p)){selected=p;break;}}
        }
        if(selected==null)throw new IllegalStateException("BLOCKED_DEPOT_SITE: no reachable single-chest site or supported one-block foundation near the project");
        int index=replaceIndex<0?positions.size():replaceIndex;
        if(replaceIndex<0)positions.add(selected);else positions.set(index,selected);
        CoreGoalJournal d=CoreGoalJournal.open(worldRoot,character,key+"_depot_"+index);d.put("SCHEMATIC_DEPOT",0,dimension,selected.key());
        if(!d.isPersistent())throw new IllegalStateException("Cannot save project depot");
        protectDepot(selected);checkpoint("DEPOT_PLANNED",savedLayer);report("SCHEMATIC_DEPOT","index="+index+" pos="+selected.key()+" capacity=27 main storage slots");
    }
    private void protectDepot(Pos p){zones.add(new SetupBuildZone(controller,p.x(),p.z(),1,1));protectedPositions.add(p);protectedPositions.add(p.add(0,-1,0));publishProtection();}
    private void publishProtection(){protectedSnapshot.set(Set.copyOf(protectedPositions));}
    public boolean depotPresent(int index)throws Exception {
        Pos p=positions.get(index);Object state=state(p);if(Boolean.TRUE.equals(call(state,"method_26215")))return false;
        if(!idOfBlock(state).equals("minecraft:chest")||!properties(state).getOrDefault("type","single").equals("single"))throw new IllegalStateException("BLOCKED_DEPOT_CHANGED at "+p.key()+"; expected independent project chest");
        if(!air(p.add(0,1,0)))throw new IllegalStateException("BLOCKED_DEPOT_OBSTRUCTED at "+p.key());
        return true;
    }
    public List<SchematicProjectTask.Depot> depots()throws Exception {
        List<SchematicProjectTask.Depot> result=new ArrayList<>();
        for(int i=0;i<positions.size();i++){
            if(!depotPresent(i))throw new IllegalStateException("Project chest disappeared: "+positions.get(i).key());
            Pos p=positions.get(i);Object inventory=call(world,"method_8321",position(p));
            if(inventory==null||((Number)call(inventory,"method_5439")).intValue()!=27)throw new IllegalStateException("Cannot verify actual project chest slots");
            Map<String,Integer> stock=new TreeMap<>(),merge=new TreeMap<>();int free=0;
            for(int slot=0;slot<27;slot++){
                Object stack=call(inventory,"method_5438",slot);if(Boolean.TRUE.equals(call(stack,"method_7960"))){free++;continue;}
                String id=idOfItem(call(stack,"method_7909"));int n=((Number)call(stack,"method_7947")).intValue(),max=((Number)call(stack,"method_7914")).intValue();
                stock.merge(id,n,Math::addExact);merge.merge(id,Math.max(0,max-n),Math::addExact);
            }
            result.add(new SchematicProjectTask.Depot(p,stock,merge,free));
        }return result;
    }
    public int maxStack(String id)throws Exception {int max=((Number)call(call(item(id),"method_7854"),"method_7914")).intValue();if(max<1)throw new IllegalStateException("Unknown stack limit: "+id);return max;}
    public Map<String,Integer> inventory()throws Exception {Map<String,Integer> result=new TreeMap<>();for(var e:FoodAcquisition.inventory(controller).entrySet())result.merge(idOfItem(e.getKey()),e.getValue(),Math::addExact);return result;}
    public int freeSlots()throws Exception {var snapshot=com.player2.playerengine.agentic.InventorySpace.inspect(controller);if(!snapshot.known())throw new IllegalStateException("Actual main inventory slots unavailable");return snapshot.free();}
    public Task acquire(String id,int target)throws Exception {
        Object itemTarget=FoodAcquisition.construct(P+"util.ItemTarget",item(id),target);
        Object probe=callStatic(P+"tasks.crafting.CraftMacroTasks","macroOutcome",controller,itemTarget);
        if(String.valueOf(call(probe,"outcome")).equals("MACRO_CREATED"))return (Task)call(probe,"task");
        Object nativeTask=callStatic(P+"TaskCatalogue","getItemTask",itemTarget);
        if(nativeTask instanceof Task t)return t;
        throw new IllegalStateException("BLOCKED_ACQUISITION_UNSUPPORTED: no enabled recipe/catalogue acquisition for "+id);
    }
    public Task placeDepot(int index){return new DepotPlacement(this,positions.get(index));}
    public Task transfer(int index,String id,int amount,boolean deposit)throws Exception {
        Object direction=Class.forName(P+"containeraccess.ScanReportFormatter$TransferDirection").getField(deposit?"DEPOSIT":"WITHDRAW").get(null);
        Object query=FoodAcquisition.construct(P+"containeraccess.StorageItemArgs$ItemQuery",item(id),id,amount);
        return (Task)FoodAcquisition.construct(P+"tasks.container.BoundedContainerTransferTask",direction,position(positions.get(index)),List.of(query));
    }
    private static Task task(String name,Object...args)throws Exception{return (Task)FoodAcquisition.construct(P+name,args);}
    private final class DepotPlacement extends Task {
        private final SchematicWorld env;private final Pos pos;private Task active;private boolean done;private int ticks;
        DepotPlacement(SchematicWorld env,Pos pos){this.env=env;this.pos=pos;}
        protected void onStart(){}
        protected Task onTick(){try{
            if(++ticks>2400){fail("Depot placement budget exhausted");return null;}
            if(env.depotPresent(positions.indexOf(pos))){done=true;return null;}
            if(active!=null&&!active.isFinished()&&!active.stopped())return active;
            if(active!=null&&active.stopped()&&!active.isFinished()){fail("Depot native placement stopped without verified block");return null;}
            if(!solid(pos.add(0,-1,0))){
                if(inventory().getOrDefault("minecraft:cobblestone",0)<1)active=acquire("minecraft:cobblestone",1);
                else{
                    Object block=lookup("field_41175","minecraft:cobblestone");Object blocks=Array.newInstance(Class.forName("net.minecraft.class_2248"),1);Array.set(blocks,0,block);
                    active=task("tasks.construction.PlaceBlockTask",position(pos.add(0,-1,0)),blocks,false,false);
                }
            }else active=task("tasks.construction.PlaceItemBlockAtPosTask",position(pos),lookup("field_41175","minecraft:chest"));
            active.controller=controller;return active;
        }catch(Exception|LinkageError e){fail("Depot placement: "+e.getMessage());return null;}}
        protected void onStop(Task next){}
        public boolean isFinished(){return done;}
        protected boolean isEqual(Task t){return t instanceof SchematicWorld.DepotPlacement d&&pos.equals(d.pos);}
        protected String toDebugString(){return "Project chest @ "+pos.key();}
    }

    public SchematicProjectTask.Section section(int y)throws Exception {
        if(sectionY!=y){sectionY=y;sectionCursor=sectionBase=sectionFinal=0;sectionBill.clear();sectionPost.clear();sectionMismatch=null;}
        int size=plan.width*plan.length,end=Math.min(size,sectionCursor+500);
        for(;sectionCursor<end;sectionCursor++){
            int x=sectionCursor%plan.width,z=sectionCursor/plan.width;State desired=plan.at(x,y,z);Cell cell=new Cell(new Pos(x,y,z),desired);Pos p=absolute(cell.pos());Object current=state(p);
            recordSiteConflict(p,current,desired);
            boolean base=baseMatch(current,desired),complete=finalMatch(current,desired);
            if(!fluids&&(desired.water()||desired.waterlogged()))complete=desired.water()||finalMatch(current,dryFinal(desired));
            if(base)sectionBase++;if(complete)sectionFinal++;
            if(!complete&&sectionMismatch==null)sectionMismatch=p.key()+" expected="+desired+" actual="+current;
            if(!desired.air()&&base)protectedPositions.add(p);
            int n=missingItems(current,desired);
            if(!base&&n>0)sectionBill.merge(placementItem(desired),n,Math::addExact);
            if(!complete&&base&&(desired.interactiveOpen()||desired.doubleSlab()||(fluids&&(desired.source()||desired.waterlogged()))))sectionPost.add(!fluids&&desired.waterlogged()?new Cell(cell.pos(),dryFinal(desired)):cell);
        }
        if(sectionCursor<size)return null;
        publishProtection();var result=new SchematicProjectTask.Section(size,sectionBase,sectionFinal,sectionMismatch,sectionBill,sectionPost);
        sectionCursor=sectionBase=sectionFinal=0;sectionBill.clear();sectionPost.clear();sectionMismatch=null;return result;
    }
    public void enableFluids(){fluids=true;sectionY=-1;}
    public void startSection(int y)throws Exception {
        pauseBuilder();Object builder=call(call(controller,"getBaritone"),"getBuilderProcess");
        Map<Long,Object> desired=new HashMap<>(),finals=new HashMap<>();Map<Long,State> specs=new HashMap<>();
        for(int z=0;z<plan.length;z++)for(int x=0;x<plan.width;x++){
            State target=plan.at(x,y,z);long key=((long)x<<32)|(z&0xffffffffL);specs.put(key,target);
            desired.put(key,resolved(target.placementState()));finals.put(key,resolved(fluids?target:dryFinal(target)));
        }
        ownedSchematic=SchematicBuildSupport.section(plan.width,plan.length,desired,finals,specs);
        SchematicBuildSupport.bindEntity(call(controller,"getEntity"),builder);
        call(builder,"build","Ellie schematic "+plan.filename+" layer "+y,ownedSchematic,position(origin.add(0,y,0)));
    }
    public void pauseBuilder()throws Exception {
        if(ownedSchematic==null)return;Object builder=call(call(controller,"getBaritone"),"getBuilderProcess");
        if(get(builder,"schematic")==ownedSchematic||get(builder,"realSchematic")==ownedSchematic)call(builder,"onLostControl");
        ownedSchematic=null;
    }
    public boolean builderActive()throws Exception {
        if(ownedSchematic==null)return false;Object builder=call(call(controller,"getBaritone"),"getBuilderProcess");
        return (get(builder,"schematic")==ownedSchematic||get(builder,"realSchematic")==ownedSchematic)&&Boolean.TRUE.equals(call(builder,"isActive"));
    }
    public Task postAction(Cell cell)throws Exception {
        State desired=cell.state();Pos p=absolute(cell.pos());
        if(desired.source()||desired.waterlogged()){
            Pos clicked=desired.source()?p.add(0,-1,0):p;
            Object up=Class.forName("net.minecraft.class_2350").getField("field_11036").get(null);
            Object click=Class.forName(P+"automaton.api.utils.input.Input").getField("CLICK_RIGHT").get(null);
            return task("tasks.InteractWithBlockTask",item("minecraft:water_bucket"),up,position(clicked),click,false,true);
        }
        if(desired.doubleSlab()){
            Object up=Class.forName("net.minecraft.class_2350").getField("field_11036").get(null);
            return task("tasks.InteractWithBlockTask",item(desired.id()),up,position(p),false);
        }
        if(desired.interactiveOpen())return task("tasks.InteractWithBlockTask",position(p),false);
        throw new IllegalStateException("Unsupported final-state action "+desired);
    }
    public boolean cellComplete(Cell cell)throws Exception{return finalMatch(state(absolute(cell.pos())),cell.state());}
    public void checkpoint(String status,int layer)throws Exception {
        if(journal==null)return;savedLayer=layer;journal.put(status,layer,dimension,plan.sha256+"|"+origin.key()+"|"+positions.size());
        if(!journal.isPersistent())throw new IllegalStateException("Project checkpoint unavailable: "+journal.error());
    }
    public void verified(String evidence){SharedTargetRecovery.verifiedWorldOutput(controller,evidence);}
    public void report(String event,String detail){ObservatoryTelemetry.set("schematicProject",detail);if(event.equals("SCHEMATIC_SITE_WARNING")||event.equals("SCHEMATIC_SITE_CLEAR"))ObservatoryTelemetry.set("schematicBuildSite",detail);ObservatoryTelemetry.event(event,detail);System.out.println("[EllieSchematic] "+event+": "+detail);if(event.equals("SCHEMATIC_PREPARED")||event.equals("SCHEMATIC_COMPLETED")||event.equals("SCHEMATIC_BLOCKED")||event.equals("SCHEMATIC_SITE_WARNING"))try{call(controller,"reportAgenticProgress",detail,true);}catch(Exception ignored){}}
    public void close(){
        if(closed)return;
        try{pauseBuilder();}catch(Exception e){report("SCHEMATIC_CLEANUP",e.getMessage());}
        if(extra!=null&&breakGuard!=null)try{synchronized(call(extra,"getBreakMutex")){((List<?>)call(extra,"getBreakAvoiders")).remove(breakGuard);}}catch(Exception e){report("SCHEMATIC_CLEANUP",e.getMessage());}
        for(SetupBuildZone zone:zones)zone.close();zones.clear();protectedSnapshot.set(Set.of());WorksiteAnchor.close(this);closed=true;
    }
}
