package com.player2.playerengine.schematic;

import com.player2.playerengine.tasks.base.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;

/** One native task owns preparation and construction. Child tasks retain acquisition/navigation. */
public final class SchematicProjectTask extends Task implements TrackedTaskOutcomeProvider,TransientlyResumableTask {
    public record Depot(Pos pos,Map<String,Integer> stock,Map<String,Integer> mergeRoom,int freeSlots) {
        public Depot {stock=Map.copyOf(stock);mergeRoom=Map.copyOf(mergeRoom);if(freeSlots<0)throw new IllegalArgumentException("Unknown storage capacity");}
        public int room(String item,int maxStack){return mergeRoom.getOrDefault(item,0)+freeSlots*maxStack;}
    }
    public record Section(int total,int baseCorrect,int finalCorrect,String mismatch,Map<String,Integer> materials,List<Cell> post) {
        public Section {materials=Map.copyOf(materials);post=List.copyOf(post);}
        public boolean complete(){return finalCorrect==total;}
    }
    public interface Environment {
        CompletableFuture<SchematicPlan> load(String file)throws Exception;
        void initialize(SchematicPlan plan,Pos origin)throws Exception;
        void checkWorld()throws Exception;
        boolean preflight()throws Exception;
        void recheck();
        Map<String,Integer> requirements();
        String worldMismatch();
        /** Material preparation may proceed while the selected build site is obstructed. */
        String buildSiteConflict();
        List<Pos> depotPositions();
        void allocateDepot(int replaceIndex)throws Exception;
        boolean depotPresent(int index)throws Exception;
        List<Depot> depots()throws Exception;
        int maxStack(String item)throws Exception;
        Map<String,Integer> inventory()throws Exception;
        int freeSlots()throws Exception;
        boolean atDepot()throws Exception;
        Task travelToDepot()throws Exception;
        Task acquire(String item,int absoluteTarget)throws Exception;
        Task placeDepot(int index)throws Exception;
        Task transfer(int index,String item,int amount,boolean deposit)throws Exception;
        Section section(int y)throws Exception; // null while a bounded world scan is incomplete
        void startSection(int y)throws Exception;
        void enableFluids();
        void pauseBuilder()throws Exception;
        boolean builderActive()throws Exception;
        Task postAction(Cell cell)throws Exception;
        boolean cellComplete(Cell cell)throws Exception;
        void checkpoint(String status,int layer)throws Exception;
        void verified(String evidence);
        void report(String event,String detail);
        void close();
    }
    private enum Kind {ACQUIRE,DEPOSIT,WITHDRAW,PLACE,TRAVEL,POST}
    private static final class Action {
        Kind kind;Task task;String item;int target,index,ticks,idle,stockTarget,endedTicks;Cell cell;
        final Map<String,Integer> highWater=new HashMap<>();
        Action(Kind kind,Task task){this.kind=kind;this.task=Objects.requireNonNull(task,"Native action unavailable");}
    }
    private final String filename;
    private final Pos origin;
    private final boolean build;
    private Environment environment;
    private CompletableFuture<SchematicPlan> importing;
    private SchematicPlan plan;
    private Action action;
    private boolean initialized,preflightDone,prepared,terminal,successful;
    private int importTicks,allTicks,layerIndex,buildIdle,stockRechecks,placementAlternatives,preparedRepairs;
    private int bestBase,bestFinal;
    private boolean checkingPrepared,sectionBaseline,fluids;
    private int settling,fluidIndex;
    private List<Cell> fluidTargets=List.of();
    private String reason="Not started",phase="IMPORT";
    private String lastSiteNotice="";
    private Map<String,Integer> required=Map.of();
    private Map<String,Integer> verifiedStock=Map.of();
    private volatile String materialStatus="";

    public SchematicProjectTask(String filename,int x,int y,int z,boolean build){
        this.filename=Objects.requireNonNull(filename);this.origin=new Pos(x,y,z);this.build=build;
        if(Math.abs((long)x)>29_999_000||Math.abs((long)z)>29_999_000||y< -2048||y>2048)throw new IllegalArgumentException("Invalid schematic origin");
    }
    public SchematicProjectTask(String filename,int x,int y,int z,boolean build,Environment environment){this(filename,x,y,z,build);this.environment=Objects.requireNonNull(environment);}
    protected void onStart(){
        try{if(environment==null)environment=new SchematicWorld(controller);if(importing==null)importing=environment.load(filename);}catch(Exception|LinkageError e){block("IMPORT",e.getMessage());}
    }
    protected Task onTick(){
        if(terminal)return null;
        try{
            if(++allTicks>240_000)return block("WORK_BUDGET","Cumulative active work budget exhausted; saved project can be retried");
            environment.checkWorld();
            if(plan==null){
                if(++importTicks>1200)return block("IMPORT_TIMEOUT","File parsing did not finish");
                if(!importing.isDone())return null;
                plan=importing.join();fluidTargets=plan.targets.stream().filter(c->c.state().source()||c.state().waterlogged()).toList();
            }
            if(!initialized){environment.initialize(plan,origin);initialized=true;event("SCHEMATIC_PLAN",plan.width+"x"+plan.height+"x"+plan.length+" targets="+plan.targets.size()+" bill="+plan.materialCounts);}
            if(allTicks%100==0){
                if(action!=null)reportMaterial(action,environment.inventory());
                event("SCHEMATIC_PROGRESS","objective="+(build?"build":"prepare")+" file="+filename+" phase="+phase+" layer="+layerIndex+"/"+plan.layers.size()+" inventory="+environment.inventory()+" emptyMain="+environment.freeSlots()+" subtask="+getTaskTree()+" noPlacement="+buildIdle+" failure="+reason);
            }
            if(action!=null)return pollAction();
            if(!preflightDone){
                phase="PREFLIGHT";if(!environment.atDepot())return travel();if(!environment.preflight())return null;
                required=Map.copyOf(environment.requirements());preflightDone=true;
                if(!checkBuildSite())return null;
            }
            if(!prepared)return prepare();
            if(!build)return success("Required materials verified in project storage; construction has not started. "+siteSummary());
            return construct();
        }catch(Exception|LinkageError e){return block("BOUNDARY",rootMessage(e));}
    }

    private Task prepare()throws Exception {
        phase="PREPARE";
        List<Pos> positions=environment.depotPositions();
        if(positions.isEmpty()){if(!environment.atDepot())return travel();environment.allocateDepot(-1);return null;}
        for(int i=0;i<positions.size();i++)if(!environment.depotPresent(i)){
            int chest=environment.inventory().getOrDefault("minecraft:chest",0);
            if(chest<1)return acquire("minecraft:chest",1);
            Action next=new Action(Kind.PLACE,environment.placeDepot(i));next.index=i;next.item="minecraft:chest";next.target=chest;
            return begin(next);
        }
        if(!environment.atDepot())return travel();
        List<Depot> depots=inspectDepots();Map<String,Integer> stored=verifiedStock;
        int slots=0;
        for(var need:required.entrySet()){
            int max=environment.maxStack(need.getKey());int missing=Math.max(0,need.getValue()-stored.getOrDefault(need.getKey(),0));
            int merge=depots.stream().mapToInt(d->d.mergeRoom.getOrDefault(need.getKey(),0)).sum();
            slots+=ceil(Math.max(0,missing-merge),max);
        }
        if(depots.stream().mapToInt(Depot::freeSlots).sum()<slots){environment.allocateDepot(-1);return null;}
        Map<String,Integer> held=environment.inventory();
        for(var need:required.entrySet()){
            String id=need.getKey();int missing=need.getValue()-stored.getOrDefault(id,0);
            int put=Math.min(missing,held.getOrDefault(id,0));
            if(put>0)return transfer(depots,id,put,true,held);
        }
        for(var need:required.entrySet()){
            String id=need.getKey();int missing=need.getValue()-stored.getOrDefault(id,0);
            if(missing<=0)continue;
            if(environment.freeSlots()<4)return block("INVENTORY_CAPACITY","Need four actual free main slots for gathering/crafting; owner gear was retained");
            int batch=Math.min(missing,Math.min(64,environment.maxStack(id)));
            return acquire(id,Math.addExact(held.getOrDefault(id,0),batch));
        }
        // Re-read actual placed blocks once the stock target is met; no stale resume bill.
        if(!checkingPrepared){environment.recheck();checkingPrepared=true;return null;}
        if(!environment.preflight())return null;
        if(!checkBuildSite())return null;
        Map<String,Integer> revised=Map.copyOf(environment.requirements());
        if(!revised.equals(required)){
            if(++stockRechecks>2)return block("WORLD_CHANGED","Blueprint requirements kept changing during preparation");
            required=revised;checkingPrepared=false;return null;
        }
        prepared=true;checkingPrepared=false;materialStatus="";environment.checkpoint("PREPARED",layerIndex);
        event("SCHEMATIC_PREPARED","Fresh storage stock verified: "+stored+"; depots="+positions+"; "+siteSummary());
        return null;
    }

    private String siteSummary(){
        String conflict=environment.buildSiteConflict();
        return conflict==null?"No protected build-site conflicts found":"Build site requires attention: "+conflict;
    }
    private boolean checkBuildSite(){
        String conflict=environment.buildSiteConflict();
        if(conflict!=null&&build){
            block("PROTECTED_SITE",conflict+". Move/remove the conflicting blocks yourself or choose another origin; schematic_prepare can stage materials separately.");
            return false;
        }
        String notice=conflict==null?"CLEAR":conflict;
        if(!notice.equals(lastSiteNotice)){
            lastSiteNotice=notice;
            event(conflict==null?"SCHEMATIC_SITE_CLEAR":"SCHEMATIC_SITE_WARNING",siteSummary());
        }
        return true;
    }

    private Task construct()throws Exception {
        if(!checkBuildSite())return null;
        if(fluids&&fluidIndex<fluidTargets.size()){
            Cell cell=fluidTargets.get(fluidIndex);phase="FLUID_SOURCES";
            if(environment.cellComplete(cell)){fluidIndex++;return null;}
            return finalAction(cell);
        }
        if(layerIndex>=plan.layers.size()){
            if(!fluids){environment.pauseBuilder();environment.enableFluids();fluids=true;layerIndex=0;sectionBaseline=false;event("SCHEMATIC_FLUID_PHASE","Solid layers verified; now place sources and verify fluid states");return null;}
            // A full final world pass catches damage to previously completed sections.
            phase="FINAL_VERIFY";
            if(!checkingPrepared){environment.pauseBuilder();environment.recheck();checkingPrepared=true;return null;}
            if(!environment.preflight())return null;
            if(!checkBuildSite())return null;
            Map<String,Integer> remaining=environment.requirements();
            if(environment.worldMismatch()!=null)return block("FINAL_WORLD_MISMATCH",environment.worldMismatch()+"; remaining="+remaining);
            return success("Schematic block states and required air/fluid cells verified in the actual world");
        }
        int y=plan.layers.get(layerIndex);Section section=environment.section(y);
        if(!checkBuildSite()||section==null)return null;
        phase="BUILD_LAYER_"+y;
        if(!sectionBaseline){bestBase=section.baseCorrect;bestFinal=section.finalCorrect;sectionBaseline=true;}
        else if(section.baseCorrect>bestBase||section.finalCorrect>bestFinal){
            bestBase=Math.max(bestBase,section.baseCorrect);bestFinal=Math.max(bestFinal,section.finalCorrect);buildIdle=0;
            environment.verified("schematic="+plan.sha256+" layer="+y+" base="+section.baseCorrect+" final="+section.finalCorrect);
        }
        if(section.complete()){
            environment.pauseBuilder();layerIndex++;bestBase=bestFinal=buildIdle=settling=0;sectionBaseline=false;
            environment.checkpoint("BUILDING",layerIndex);event("SCHEMATIC_SECTION_DONE","layer="+y+" all "+section.total+" cells verified");return null;
        }
        if(++buildIdle>1200){environment.pauseBuilder();return block("PLACEMENT_STALLED","No new verified world placement; "+section.mismatch);}
        if(section.baseCorrect==section.total){
            environment.pauseBuilder();
            if(section.post.isEmpty()){if(++settling<=100)return null;return block("BLOCK_STATE","Properties did not settle: "+section.mismatch);}
            return finalAction(section.post.getFirst());
        }
        Map<String,Integer> held=environment.inventory();
        for(var need:section.materials.entrySet())if(held.getOrDefault(need.getKey(),0)<Math.min(need.getValue(),environment.maxStack(need.getKey()))){
            environment.pauseBuilder();
            if(!environment.atDepot())return travel();
            List<Depot> depots=inspectDepots();Map<String,Integer> stored=verifiedStock;
            int missing=Math.min(need.getValue(),environment.maxStack(need.getKey()))-held.getOrDefault(need.getKey(),0);
            if(stored.getOrDefault(need.getKey(),0)>0){
                if(environment.freeSlots()<2)return block("INVENTORY_CAPACITY","Construction supplies need two actual free carrying slots");
                return transfer(depots,need.getKey(),Math.min(missing,stored.get(need.getKey())),false,held);
            }
            if(++preparedRepairs>64)return block("SUPPLY_CHANGED","Repeatedly lost prepared construction stock");
            return acquire(need.getKey(),Math.addExact(held.getOrDefault(need.getKey(),0),Math.min(missing,64)));
        }
        if(environment.builderActive())return null;
        environment.startSection(y);event("SCHEMATIC_SECTION_START","layer="+y+" remaining="+section.materials+" target="+section.mismatch);return null;
    }

    private Task finalAction(Cell cell)throws Exception {
        String needed=cell.state().source()||cell.state().waterlogged()?"minecraft:water_bucket":cell.state().doubleSlab()?cell.state().id():null;
        Map<String,Integer> held=environment.inventory();
        if(needed!=null&&held.getOrDefault(needed,0)<1){
            if(environment.freeSlots()<2)return block("INVENTORY_CAPACITY","No verified room for final placement supplies");
            if(!environment.atDepot())return travel();List<Depot> stock=inspectDepots();
            if(total(stock).getOrDefault(needed,0)>0)return transfer(stock,needed,1,false,held);
            return acquire(needed,1);
        }
        Action next=new Action(Kind.POST,environment.postAction(cell));next.cell=cell;return begin(next);
    }

    private Task acquire(String item,int target)throws Exception {environment.pauseBuilder();Action next=new Action(Kind.ACQUIRE,environment.acquire(item,target));next.item=item;next.target=target;return begin(next);}
    private Task travel()throws Exception {environment.pauseBuilder();return begin(new Action(Kind.TRAVEL,environment.travelToDepot()));}
    private Task transfer(List<Depot> depots,String item,int count,boolean deposit,Map<String,Integer> held)throws Exception {
        int max=environment.maxStack(item);
        for(int i=0;i<depots.size();i++){
            int available=deposit?depots.get(i).room(item,max):depots.get(i).stock.getOrDefault(item,0);
            int amount=Math.min(count,Math.min(available,64));if(amount<=0)continue;
            environment.pauseBuilder();Action next=new Action(deposit?Kind.DEPOSIT:Kind.WITHDRAW,environment.transfer(i,item,amount,deposit));
            next.item=item;next.index=i;next.target=held.getOrDefault(item,0)+(deposit?-amount:amount);
            next.stockTarget=depots.get(i).stock.getOrDefault(item,0)+(deposit?amount:-amount);return begin(next);
        }
        return block("STORAGE_CAPACITY","No live slot/stack room for "+item+" x"+count);
    }
    private List<Depot> inspectDepots()throws Exception {
        List<Depot> depots=environment.depots();verifiedStock=Map.copyOf(total(depots));return depots;
    }
    private void reportMaterial(Action a,Map<String,Integer> inventory){
        if(prepared||a.item==null||!required.containsKey(a.item))return;
        int need=required.get(a.item),stored=verifiedStock.getOrDefault(a.item,0),carried=inventory.getOrDefault(a.item,0);
        materialStatus="item="+a.item+" required="+need+" stored="+stored+" carried="+carried
                +" remainingToGather="+Math.max(0,need-stored-carried)+(a.kind==Kind.ACQUIRE?" batchTarget=":" inventoryTarget=")+a.target
                +" action="+a.kind+" stock=lastVerifiedChestScan";
        event("SCHEMATIC_MATERIAL",materialStatus);
    }
    private Task begin(Action next)throws Exception {
        action=next;materialStatus="";next.task.controller=controller;Map<String,Integer> inventory=environment.inventory();next.highWater.putAll(inventory);
        event("SCHEMATIC_ACTION","phase="+phase+" action="+next.kind+" item="+next.item+" inventoryTarget="+next.target+" depot="+next.index);
        reportMaterial(next,inventory);return next.task;
    }
    private Task pollAction()throws Exception {
        Action a=action;environment.checkWorld();
        Map<String,Integer> inventory=environment.inventory();int held=a.item==null?0:inventory.getOrDefault(a.item,0);
        boolean gain=false;for(var e:inventory.entrySet())if(e.getValue()>a.highWater.getOrDefault(e.getKey(),0)){a.highWater.put(e.getKey(),e.getValue());gain=true;}
        a.idle=gain?0:a.idle+1;
        List<Depot> depots=(a.kind==Kind.WITHDRAW||a.kind==Kind.DEPOSIT)?inspectDepots():List.of();
        boolean met=switch(a.kind){
            case ACQUIRE->held>=a.target;
            case WITHDRAW->held>=a.target&&depots.get(a.index).stock.getOrDefault(a.item,0)<=a.stockTarget;
            case DEPOSIT->held<=a.target&&depots.get(a.index).stock.getOrDefault(a.item,0)>=a.stockTarget;
            case PLACE->environment.depotPresent(a.index);
            case TRAVEL->environment.atDepot();
            case POST->environment.cellComplete(a.cell);
        };
        if(met){
            action=null;environment.checkpoint(prepared?"BUILDING":"PREPARING",layerIndex);
            reportMaterial(a,inventory);
            event("SCHEMATIC_ACTION_VERIFIED",a.kind+" item="+a.item+" inventory="+held);return null;
        }
        boolean ended=a.task.stopped()||a.task.isFinished();
        if(ended&&(a.kind==Kind.POST||a.kind==Kind.PLACE)&&++a.endedTicks<=20)return null;
        if(++a.ticks>16000||a.idle>2400||ended){
            String nativeReason="";
            try{Object detail=call(a.task,"getAggregatedFailureReason");nativeReason=detail instanceof Optional<?> optional?optional.map(Object::toString).orElse(""):String.valueOf(detail);}catch(Exception ignored){}
            if(a.kind==Kind.PLACE&&placementAlternatives++<2){environment.allocateDepot(a.index);action=null;return null;}
            reportMaterial(a,inventory);
            return block(a.kind.name(),"Native action ended or timed out without its postcondition; "+(!materialStatus.isEmpty()?materialStatus+"; ":"")+"item="+a.item+" carried="+held+" batchTarget="+a.target+" child="+a.task+" "+nativeReason);
        }
        return a.task;
    }
    private static Map<String,Integer> total(List<Depot> depots){Map<String,Integer> all=new TreeMap<>();for(Depot d:depots)d.stock.forEach((k,v)->all.merge(k,v,Math::addExact));return all;}
    private static int ceil(int n,int d){if(d<1)throw new IllegalArgumentException("Unknown stack size");return (n+d-1)/d;}
    private Task success(String detail){terminal=true;successful=true;reason=detail;environment.close();event("SCHEMATIC_COMPLETED",detail);return null;}
    private Task block(String code,String detail){terminal=true;successful=false;reason="BLOCKED_SCHEMATIC_"+code+": "+detail;if(importing!=null&&!importing.isDone())importing.cancel(true);if(environment!=null)environment.close();event("SCHEMATIC_BLOCKED",reason);fail(reason);return null;}
    private static String rootMessage(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
    private void event(String event,String message){if(environment!=null)environment.report(event,message);}
    public boolean isFinished(){return terminal;}
    public boolean isTerminal(){return terminal;}
    public boolean isSuccessful(){return terminal&&successful;}
    public String controlledReason(){return reason;}
    protected void onStop(Task next){if(environment!=null)environment.close();if(!terminal){terminal=true;reason="BLOCKED_SCHEMATIC_INTERRUPTED: project stopped; deposited stock and placed blocks remain";event("SCHEMATIC_INTERRUPTED",reason);}}
    protected boolean isEqual(Task other){return other instanceof SchematicProjectTask t&&filename.equals(t.filename)&&origin.equals(t.origin)&&build==t.build;}
    protected String toDebugString(){return terminal?reason:"Schematic "+(build?"build ":"prepare ")+filename+" "+phase+" layer="+layerIndex+(materialStatus.isEmpty()?"":" "+materialStatus);}
    public boolean prepareForTransientResume(TaskSuspensionCause cause){return !terminal&&(cause==TaskSuspensionCause.GESTURE_OVERLAY||cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);}
    public void afterChildrenStopped(){try{if(environment!=null)environment.pauseBuilder();action=null;}catch(Exception e){block("SUSPENSION",e.getMessage());}}
    public void onTransientResumeAbandoned(){block("INTERRUPTION","Transient resume abandoned");}
    private static SchematicProjectTask activeProject(Object controller){
        try{Object root=get(call(controller,"getUserTaskChain"),"mainTask");return root instanceof SchematicProjectTask t&&!t.terminal?t:null;}catch(Exception|LinkageError e){return null;}
    }
    public static boolean insideProject(Object controller,Object position){
        try{SchematicProjectTask t=activeProject(controller);if(t==null||t.plan==null)return false;Pos p=SchematicWorld.point(position);
            return p.x()>=t.origin.x()&&p.x()<t.origin.x()+t.plan.width&&p.z()>=t.origin.z()&&p.z()<t.origin.z()+t.plan.length;
        }catch(Exception|LinkageError e){return false;}
    }
    public static boolean rejectWaterSource(Object controller,Object position){SchematicProjectTask t=activeProject(controller);return t!=null&&t.action!=null&&t.action.kind==Kind.ACQUIRE&&"minecraft:water_bucket".equals(t.action.item)&&insideProject(controller,position);}
    public static boolean reservedSource(Object controller,Object position){
        try{Object root=get(call(controller,"getUserTaskChain"),"mainTask");
            if(!(root instanceof SchematicProjectTask task)||task.terminal||task.environment==null)return false;
            Pos p=new Pos(((Number)call(position,"method_10263")).intValue(),((Number)call(position,"method_10264")).intValue(),((Number)call(position,"method_10260")).intValue());
            return task.environment.depotPositions().contains(p);
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
