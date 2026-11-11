package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.tasks.base.*;
import java.util.*;

/** Runs native item/placement tasks and verifies each real block before accepting it. */
public final class StarterBaseTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
    public interface Environment {
        default void protect(List<StarterBaseLayout.Placement> plan)throws Exception {}
        default void releaseProtection() {}
        default List<StarterBaseLayout.Placement> loadExisting() throws Exception { return List.of(); }
        default List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> plan)throws Exception { return List.of(); }
        default boolean prepared(StarterBaseLayout.Placement job)throws Exception { throw new UnsupportedOperationException(); }
        default boolean canPrepare(StarterBaseLayout.Placement job)throws Exception { return false; }
        default Task prepareTerrain(StarterBaseLayout.Placement job)throws Exception { throw new UnsupportedOperationException(); }
        default List<StarterBaseLayout.Placement> repairPlan(StarterBaseLayout.Placement station)throws Exception {return List.of();}
        default String unsafeReason(StarterBaseLayout.Placement station)throws Exception {return "support, headroom or standing access is unsafe";}
        default Task approach(List<StarterBaseLayout.Placement> plan)throws Exception {return null;}
        List<StarterBaseLayout.Placement> loadOrSelect() throws Exception;
        boolean matches(StarterBaseLayout.Placement placement) throws Exception;
        boolean safe(StarterBaseLayout.Placement placement) throws Exception;
        int count(String resource) throws Exception;
        Task acquire(String resource) throws Exception;
        Task place(StarterBaseLayout.Placement placement) throws Exception;
        // Test environments without a game surface preserve the legacy station-only fixture.
        default SurfaceLightingPlan.Survey surveyLighting(List<StarterBaseLayout.Placement> plan,
                Set<StarterBaseLayout.Pos> excluded) throws Exception {
            return new SurfaceLightingPlan.Survey(1, 0, Optional.empty());
        }
        default void retainTorch(StarterBaseLayout.Placement placement) {}
        /** Persist HOME only after the table exists in the world. */
        default void rememberHome(StarterBaseLayout.Placement bench) throws Exception {}
    }
    private Environment environment;
    private final int stationLimit;
    private final StarterBaseLayout.Pos origin;
    private final String dimension;
    private List<StarterBaseLayout.Placement> plan;
    private List<StarterBaseLayout.Placement> terrain;
    private int terrainIndex;
    private int layerRepairs;
    private Task active;
    private boolean acquiring;
    private Task access;
    private int accessTicks;
    private int index, ticks, stageTicks, outputWaitTicks;
    private boolean done, failed;
    private String reason="Starter base not yet verified";
    public StarterBaseTask(StarterBaseLayout.Pos origin,String dimension) { this(origin,dimension,3); }
    public StarterBaseTask(StarterBaseLayout.Pos origin,String dimension,int stations) {
        if(stations!=1&&stations!=3)throw new IllegalArgumentException("Workshop stage must be 1 or 3 stations");
        this.stationLimit=stations;
        this.origin=Objects.requireNonNull(origin);this.dimension=Objects.requireNonNull(dimension);
    }
    public StarterBaseTask(Environment environment) { this.environment=Objects.requireNonNull(environment); this.origin=null; this.dimension=null;this.stationLimit=3; }
    protected void onStart() {
        if (environment==null) environment=new StarterBaseWorld(controller,origin,dimension);
    }
    public void prepare() throws Exception {
        onStart();
        if (plan==null) plan=List.copyOf(environment.loadOrSelect());
    }
    public List<StarterBaseLayout.Placement> layout(){if(plan==null)throw new IllegalStateException("Workshop site not selected");return plan;}
    public void prepareExisting() throws Exception {
        onStart();
        List<StarterBaseLayout.Placement> existing=environment.loadExisting();
        if (!existing.isEmpty()) plan=List.copyOf(existing);
    }
    protected Task onTick() {
        if (done || failed) return null;
        try {
            if (++ticks>72000) return blocked("Starter base active-work budget exhausted; verify preparation and placement reach");
            if (plan==null) {
                plan=List.copyOf(environment.loadOrSelect());
                if (plan.size()!=3) return blocked("Starter base requires one table, one furnace and one chest");
            }
            Task needed=environment.approach(plan);
            if(needed!=null){
                if(++accessTicks>2400)return blocked("Cannot reach the saved workshop chest within the bounded return window");
                if(access==null||access.stopped()||access.isFinished()){access=needed;access.controller=controller;}
                return access;
            }
            access=null;accessTicks=0;
            if(terrain==null)terrain=List.copyOf(environment.terrainPlan(plan));
            environment.protect(plan);
            while(terrainIndex<terrain.size()) {
                var job=terrain.get(terrainIndex);
                if(environment.prepared(job)){terrainIndex++;active=null;stageTicks=0;continue;}
                if(!environment.canPrepare(job))return blocked("Cannot safely prepare "+job.resource()+" at "+job.pos()+" (bounds, fluid, protected or unbreakable block)");
                if(active!=null) {
                    if(++stageTicks>(acquiring?2400:1200))return blocked("Native site preparation made no verified output at "+job.pos());
                    if(!active.isFinished()&&!active.stopped())return active;
                    if(acquiring&&environment.count(job.resource())>0){active=null;stageTicks=0;}
                    else return blocked("Native site preparation stopped without verified output at "+job.pos());
                }
                acquiring=!job.resource().equals("air")&&environment.count(job.resource())==0;
                active=Objects.requireNonNull(acquiring?environment.acquire(job.resource()):environment.prepareTerrain(job));
                active.controller=controller;return active;
            }
            // Preparation is a verified layer, not a cursor that can remain valid
            // after resource gathering removed an earlier foundation block.
            for(int i=0;i<terrain.size();i++)if(!environment.prepared(terrain.get(i))) {
                if(++layerRepairs>3)return blocked("Prepared base layer changed repeatedly; repair budget exhausted at "+terrain.get(i).pos());
                terrainIndex=i;active=null;stageTicks=0;
                reason="Repairing changed prepared layer at "+terrain.get(i).pos();
                return null;
            }
            while(index<stationLimit && environment.matches(plan.get(index))) { index++; active=null; stageTicks=0; }
            // The first verified table becomes the permanent setup HOME, before tools.
            if(index>0 && environment.matches(plan.getFirst())) environment.rememberHome(plan.getFirst());
            if (index==stationLimit) {
                for (int i=0;i<stationLimit;i++) if (!environment.matches(plan.get(i))||!environment.safe(plan.get(i))) {
                    index=i;return repair(plan.get(i));
                }
                done=true;
                reason=stationLimit==1?"Home crafting bench physically verified":"Home bench, furnace and chest physically verified";
                return null;
            }
            var target=plan.get(index);
            if (!environment.safe(target)) {
                return repair(target);
            }
            if (active!=null) {
                if (++stageTicks > (acquiring ? 2400 : 600)) return blocked("No verified base output within the bounded native task window at "+target.pos());
                if (!active.isFinished() && !active.stopped()) return active;
                if (acquiring && environment.count(target.resource())==0) {
                    // Native crafting can finish before the inventory output arrives.
                    // Wait for observed output; never turn a callback into ownership.
                    if(++outputWaitTicks<=40){
                        reason="Awaiting actual "+target.resource()+" output at "+target.pos()+" ("+outputWaitTicks+"/40 ticks)";
                        if(outputWaitTicks==1)com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_OUTPUT_SETTLE",reason);
                        return null;
                    }
                    return blocked("Native acquisition ended without "+target.resource()+" after 40 ticks awaiting actual inventory output at "+target.pos());
                }
                if (!acquiring && !environment.matches(target)) return blocked("Native placement stopped without verifying "+target.resource()+" at "+target.pos());
                active=null;
                stageTicks=0;outputWaitTicks=0;
            }
            acquiring=environment.count(target.resource())==0;
            active=Objects.requireNonNull(acquiring?environment.acquire(target.resource()):environment.place(target),"Missing native base task");
            active.controller=controller;
            return active;
        } catch (Exception | LinkageError error) {
            String detail = error instanceof IllegalStateException && error.getMessage()!=null ? ": "+error.getMessage() : "";
            return blocked("Starter base verification/task unavailable: "+error.getClass().getSimpleName()+detail);
        }
    }
    private Task repair(StarterBaseLayout.Placement target)throws Exception {
        var jobs=environment.repairPlan(target);var pending=new ArrayList<StarterBaseLayout.Placement>();
        for(var job:jobs)if(!environment.prepared(job))pending.add(job);
        String detail=target.resource()+" at "+target.pos()+": "+environment.unsafeReason(target);
        if(pending.isEmpty())return blocked("Unsafe workstation; no safe native repair available: "+detail);
        if(++layerRepairs>3)return blocked("Workstation repair budget exhausted: "+detail);
        var combined=new LinkedHashSet<StarterBaseLayout.Placement>(pending);combined.addAll(terrain);
        terrain=List.copyOf(combined);terrainIndex=0;active=null;stageTicks=0;
        reason="Repairing workstation: "+detail;
        com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_BASE_REPAIR",reason+"; native jobs="+pending);
        return null;
    }
    private Task blocked(String detail) { failed=true; reason=detail; return null; }
    public void releaseProtection(){if(environment!=null)environment.releaseProtection();}
    public int verifiedPlacedCount(String resource) {
        if (environment==null || plan==null) return 0;
        try { int count=0;for(var p:plan) if(p.resource().equals(resource)&&environment.matches(p))count++;
            return count; }
        catch (Exception | LinkageError error) { return 0; }
    }
    public boolean isTerminal() { return done||failed; }
    public boolean isSuccessful() {
        if (!done||failed) return false;
        try {
            for(int i=0;i<stationLimit;i++) {var p=plan.get(i);if(!environment.matches(p)||!environment.safe(p)) {
                reason="Base changed after verification: missing or obstructed "+p.resource()+" at "+p.pos();
                return false;
            }}
            if(!environment.matches(plan.getFirst()))return false;
            return true;
        } catch(Exception|LinkageError error) { reason="Final base world verification unavailable"; return false; }
    }
    public String controlledReason() { return reason; }
    public boolean isFinished() { return isTerminal(); }
    protected boolean isEqual(Task other) { return this==other; }
    protected String toDebugString() {
        if(terrain!=null&&terrainIndex<terrain.size())return "Starter base site preparation "+terrainIndex+"/"+terrain.size();
        return "Starter base "+index+"/"+stationLimit+": "+reason;
    }
    protected void onStop(Task next) { if(!done&&!failed)blocked("Starter base interrupted; recorded positions will be reverified on setup rerun"); }
    public boolean prepareForTransientResume(TaskSuspensionCause cause) {
        return !isTerminal() && (cause==TaskSuspensionCause.GESTURE_OVERLAY||cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
    }
    public void afterChildrenStopped() { active=null; }
    public void onTransientResumeAbandoned() { blocked("Starter base resume abandoned"); }
}
