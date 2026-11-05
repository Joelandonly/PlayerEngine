package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.automaton.api.cache.IWaypoint;
import com.player2.playerengine.automaton.api.cache.IWaypointCollection;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.*;

/**
 * The owner-facing `setup` command. One material bill, one home bench, and bounded
 * native tasks. Shelter, iron equipment, farms and placing torches are NOT setup.
 * The old survival workflow is retained in the repository but not dispatched.
 */
public final class BasicSetupTask extends Task
        implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
    public record Stage(String name, String resource, int amount, int stations) {
        public boolean workshop() { return stations > 0; }
    }

    // Wood bill: 2 wooden tools (6 planks), table (4), chest (8), 4 stone
    // tools' + wooden tools' sticks (at most 11 sticks = 6 planks), and
    // 64 torches (16 sticks = 8 planks). Total <= 32 planks = 8 logs;
    // collect 12 logs for a small margin. Cobble: 9 stone tools + 8 furnace
    // + 3 spare = 20. Coal: 16 units for 64 torches.
    private static final Stage[] PLAN = {
        new Stage("Gather all wood", "log", 12, 0),
        new Stage("Place home crafting bench", null, 0, 1),
        new Stage("Craft wooden axe", "wooden_axe", 1, 0),
        new Stage("Craft wooden pickaxe", "wooden_pickaxe", 1, 0),
        new Stage("Gather all stone", "cobblestone", 20, 0),
        new Stage("Craft stone pickaxe", "stone_pickaxe", 1, 0),
        new Stage("Craft stone axe", "stone_axe", 1, 0),
        new Stage("Craft stone shovel", "stone_shovel", 1, 0),
        new Stage("Craft stone sword", "stone_sword", 1, 0),
        new Stage("Gather all coal", "coal", 16, 0),
        new Stage("Craft 64 torches", "torch", 64, 0),
        new Stage("Craft chest", "chest", 1, 0),
        new Stage("Craft furnace", "furnace", 1, 0),
        new Stage("Place chest and furnace at home", null, 0, 3)
    };

    public static Stage[] plannedStages() { return PLAN.clone(); }

    /** Small game boundary permits real state-machine tests without a fake successful game. */
    public interface Environment {
        int inventoryCount(String resource) throws Exception;
        Task acquire(String resource, int missing) throws Exception;
        Task placeWorkshop(int stationCount) throws Exception;
        boolean workshopVerified(int stationCount) throws Exception;
        boolean homeVerified() throws Exception;
        void release();
    }

    static final class NativeEnvironment implements Environment {
        private final PlayerEngineController controller;
        private final StarterBaseLayout.Pos origin;
        private final String dimension;
        private StarterBaseTask workshop;

        NativeEnvironment(PlayerEngineController controller) throws Exception {
            this.controller=Objects.requireNonNull(controller);
            origin=StarterBaseWorld.origin(controller);
            dimension=StarterBaseWorld.dimension(controller);
        }
        public int inventoryCount(String name) {
            var matches=TaskCatalogue.getItemMatches(name);
            if(matches==null||matches.length==0)
                throw new IllegalStateException("Unsupported setup material "+name);
            return controller.getItemStorage().getItemCountInventoryOnly(matches);
        }
        public Task acquire(String name,int missing) throws Exception {
            return EllieSetupTask.nativeAcquisition(name,missing);
        }
        public Task placeWorkshop(int count) {
            release();
            workshop=new StarterBaseTask(origin,dimension,count);
            workshop.controller=controller;
            return workshop;
        }
        public boolean homeVerified() throws Exception {
            if(workshop==null) return false;
            var plan=workshop.layout();
            if(plan.isEmpty()) return false;
            var p=plan.getFirst().pos();
            IWaypointCollection waypoints=controller.getBaritone().getWorldProvider()
                    .getCurrentWorld().getWaypoints();
            for(IWaypoint waypoint:waypoints.getByTag(IWaypoint.Tag.HOME)) {
                if(waypoint.getName().equals("Ellie Setup Home") &&
                        waypoint.getLocation().equals(new BetterBlockPos(p.x(),p.y(),p.z())))
                    return true;
            }
            return false;
        }
        public boolean workshopVerified(int count) throws Exception {
            return workshop!=null && workshop.isSuccessful() && homeVerified()
                    && workshop.verifiedPlacedCount("crafting_table")==1
                    && (count==1 || workshop.verifiedPlacedCount("furnace")==1
                            && workshop.verifiedPlacedCount("chest")==1);
        }
        public void release() {
            if(workshop!=null)workshop.releaseProtection();
        }
    }

    private Environment environment;
    private int index, ticks, stagnant, lastCount, settleTicks, attempts, allTicks;
    private Task active;
    private boolean completed, blocked;
    private String reason="Not started";
    private final Map<String,Integer> repairs=new HashMap<>();
    private int totalRepairs;
    private String repairResource;
    private int repairAmount;

    public BasicSetupTask() {}
    public BasicSetupTask(Environment environment) { this.environment=Objects.requireNonNull(environment); }

    protected void onStart() {
        if(environment==null) try { environment=new NativeEnvironment(controller); }
        catch(Exception | LinkageError e) { block("Unable to establish setup world: "+e);return; }
        event("SETUP_START","build="+EllieSetupTask.ELLIE_BUILD_REVISION+
              " basic batched setup, home=first verified crafting bench, "+PLAN.length+" stages");
    }

    protected Task onTick() {
        if(completed||blocked)return null;
        try {
            if(++allTicks>100000)return block("Total setup work budget exhausted");
            if(index==PLAN.length && repairResource==null)return verifyFinal();
            Stage stage=repairResource==null ? PLAN[index] :
                    new Stage("Repair missing supply",repairResource,repairAmount,0);
            if(stage.workshop())return workshopTick(stage);
            return acquireTick(stage);
        }catch(Exception | LinkageError e) {
            return block("Stage "+(index+1)+": "+e.getClass().getSimpleName()+
                         " "+String.valueOf(e.getMessage()));
        }
    }
    private Task workshopTick(Stage stage) throws Exception {
        if(active==null) {
            active=Objects.requireNonNull(environment.placeWorkshop(stage.stations()),
                    "No native workshop task");
            active.controller=controller;
            resetWatch();
            event("SETUP_WORKSHOP",stage.name()+" stationCount="+stage.stations());
            return active;
        }
        if(environment.workshopVerified(stage.stations())) {
            return advance(stage);
        }
        if(active.isFinished()||active.stopped())return block("Workshop failed: "+active);
        if(++ticks>12000)return block("Workshop placement never verified (stage="+stage.name()+")");
        return active;
    }
    private Task acquireTick(Stage stage) throws Exception {
        int owned=environment.inventoryCount(stage.resource());
        if(owned>=stage.amount())return advance(stage);
        if(active==null) {
            int missing=stage.amount()-owned;
            active=Objects.requireNonNull(environment.acquire(stage.resource(),missing),
                    "No native task for "+stage.resource());
            active.controller=controller;
            lastCount=owned;settleTicks=0;stagnant=0;
            event("SETUP_BATCH","stage="+(index+1)+"/"+PLAN.length+" "+stage.resource()+
                  " have="+owned+" target="+stage.amount()+" missing="+missing+
                  " attempt="+(attempts+1)+"/2");
            return active;
        }
        if(owned>lastCount) {
            lastCount=owned;
            stagnant=0;
            event("SETUP_MATERIAL_PROGRESS",stage.resource()+" "+owned+"/"+stage.amount());
        }
        if(active.stopped())return block(stage.resource()+" acquisition stopped");
        if(active.isFinished()) {
            if(++settleTicks<=40)return null; // real output may arrive after native callback
            if(++attempts>=2)return block(stage.resource()+
                     " native task ended twice without verified output; have="+owned+
                     " need="+stage.amount());
            event("SETUP_ACQUISITION_RETRY",stage.resource()+
                  " native output missing; retry remaining="+(stage.amount()-owned));
            active=null;settleTicks=0;
            return null;
        }
        if(++stagnant>3600)return block(stage.resource()+
                " no inventory gain for 3600 ticks; target may be unreachable or oscillating");
        if(++ticks>16000)return block(stage.resource()+" cumulative acquisition budget exhausted");
        return active;
    }
    private Task advance(Stage stage) {
        event("SETUP_STEP_DONE",(index+1)+"/"+PLAN.length+" "+stage.name());
        active=null;attempts=0;resetWatch();
        if(repairResource!=null)repairResource=null;
        else index++;
        return null;
    }
    private Task verifyFinal() throws Exception {
        if(!environment.workshopVerified(3)||!environment.homeVerified())
            return block("Final workshop/home waypoint verification failed");
        for(String resource:List.of("stone_pickaxe","stone_axe","stone_shovel","stone_sword","torch")) {
            int amount=resource.equals("torch")?64:1;
            if(environment.inventoryCount(resource)>=amount)continue;
            if(repairs.merge(resource,1,Integer::sum)>1 || ++totalRepairs>4)
                return block("Final verification repeatedly lost "+resource);
            repairResource=resource;repairAmount=amount;
            event("SETUP_FINAL_RECHECK","Reacquire "+resource+" x"+amount+
                  ", then verify entire kit again");
            return null;
        }
        completed=true;
        reason="Wood and stone tools, 64 torches, chest/furnace and persistent home bench verified";
        event("SETUP_COMPLETED",reason);
        return null;
    }
    private void resetWatch(){ticks=0;stagnant=0;lastCount=0;settleTicks=0;}
    private static void event(String type,String detail) {
        com.player2.playerengine.observatory.ObservatoryTelemetry.event(type,detail);
    }
    private Task block(String detail){
        blocked=true;reason="Basic setup BLOCKED: "+detail;
        if(environment!=null)environment.release();
        event("SETUP_BLOCKED",reason);
        fail(reason);
        return null;
    }
    public boolean isFinished(){return completed||blocked;}
    public boolean isTerminal(){return completed||blocked;}
    public boolean isSuccessful(){return completed&&!blocked;}
    public String controlledReason(){return reason;}
    protected void onStop(Task next) {
        if(environment!=null)environment.release();
        if(!completed&&!blocked){blocked=true;reason="Basic setup interrupted";event("SETUP_INTERRUPTED",reason);}
    }
    protected boolean isEqual(Task other){return other instanceof BasicSetupTask;}
    protected String toDebugString(){
        if(blocked||completed)return reason;
        if(index>=PLAN.length)return "Basic setup final verification";
        return "Basic setup "+(index+1)+"/"+PLAN.length+": "+PLAN[index].name()+
               (active==null?"":" -> "+active);
    }
    public boolean prepareForTransientResume(TaskSuspensionCause cause){
        return !isTerminal()&&(cause==TaskSuspensionCause.GESTURE_OVERLAY||
               cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
    }
    public void afterChildrenStopped(){active=null;resetWatch();}
    public void onTransientResumeAbandoned(){block("Interruption resume abandoned");}
}
