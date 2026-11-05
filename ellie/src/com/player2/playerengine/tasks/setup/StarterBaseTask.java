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
    }
    private Environment environment;
    private final StarterBaseLayout.Pos origin;
    private final String dimension;
    private List<StarterBaseLayout.Placement> plan;
    private List<StarterBaseLayout.Placement> terrain;
    private int terrainIndex;
    private int layerRepairs;
    private Task active;
    private boolean acquiring;
    private int index, ticks, stageTicks;
    private final List<StarterBaseLayout.Placement> installedTorches = new ArrayList<>();
    private final Set<StarterBaseLayout.Pos> rejectedTorchPositions = new HashSet<>();
    private Task lightingTask;
    private StarterBaseLayout.Placement lightingTarget;
    private boolean lightingAcquiring;
    private int lightingTicks, lightingPlaced;
    private boolean done, failed;
    private String reason="Starter base not yet verified";
    public StarterBaseTask(StarterBaseLayout.Pos origin,String dimension) { this.origin=Objects.requireNonNull(origin); this.dimension=Objects.requireNonNull(dimension); }
    public StarterBaseTask(Environment environment) { this.environment=Objects.requireNonNull(environment); this.origin=null; this.dimension=null; }
    protected void onStart() {
        if (environment==null) environment=new StarterBaseWorld(controller,origin,dimension);
    }
    public void prepare() throws Exception {
        onStart();
        if (plan==null) plan=List.copyOf(environment.loadOrSelect());
    }
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
            while(index<plan.size() && environment.matches(plan.get(index))) { index++; active=null; stageTicks=0; }
            if (index==plan.size()) {
                for (int i=0;i<plan.size();i++) if (!environment.matches(plan.get(i))||!environment.safe(plan.get(i))) {
                    index=i;return repair(plan.get(i));
                }
                Task lighting = tickSurfaceLighting();
                if (lighting != null || !done && !failed) return lighting;
                return null;
            }
            var target=plan.get(index);
            if (!environment.safe(target)) {
                return repair(target);
            }
            if (active!=null) {
                if (++stageTicks > (acquiring ? 2400 : 600)) return blocked("No verified base output within the bounded native task window at "+target.pos());
                if (!active.isFinished() && !active.stopped()) return active;
                if (acquiring && environment.count(target.resource())==0) return blocked("Native acquisition ended without "+target.resource());
                if (!acquiring && !environment.matches(target)) return blocked("Native placement stopped without verifying "+target.resource()+" at "+target.pos());
                active=null;
                stageTicks=0;
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
    private Task tickSurfaceLighting() throws Exception {
        if (lightingTask != null) {
            if (++lightingTicks > (lightingAcquiring ? 2400 : 600)) {
                if (lightingAcquiring) return blocked("Unable to acquire a torch for outdoor base lighting");
                rejectTorch("native placement timed out");
            } else if (!lightingTask.isFinished() && !lightingTask.stopped()) {
                return lightingTask;
            } else if (lightingAcquiring) {
                if (environment.count("torch") < 1)
                    return blocked("Outdoor torch acquisition ended without a verified torch");
                lightingTask = null;
                lightingAcquiring = false;
                lightingTicks = 0;
            } else if (lightingTarget != null && environment.matches(lightingTarget)) {
                installedTorches.add(lightingTarget);
                environment.retainTorch(lightingTarget);
                lightingPlaced++;
                com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_OUTDOOR_TORCH",
                    "Verified torch #" + lightingPlaced + " at " + lightingTarget.pos());
                lightingTask = null;
                lightingTarget = null;
                lightingTicks = 0;
            } else {
                rejectTorch("native task stopped without placing torch");
            }
        }
        var survey = environment.surveyLighting(plan, rejectedTorchPositions);
        if (survey.eligible() <= 0)
            return blocked("No eligible outdoor ground found near workshop; refusing to light underground caves");
        if (survey.complete()) {
            done = true;
            reason = "Outdoor base verified: crafting table, furnace, chest and surface light coverage";
            com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_SURFACE_LIT",
                "Outdoor samples=" + survey.eligible() + ", torches placed=" + lightingPlaced + ", no underground targets");
            return null;
        }
        if (lightingPlaced >= SurfaceLightingPlan.MAX_NEW_TORCHES)
            return blocked("Outdoor lighting budget exhausted with " + survey.dark() + " dark surface samples");
        if (survey.next().isEmpty())
            return blocked("No safe surface torch position can cover " + survey.dark() + " dark samples");
        lightingTarget = survey.next().get();
        lightingAcquiring = environment.count("torch") <= 0;
        lightingTask = lightingAcquiring ? environment.acquire("torch") : environment.place(lightingTarget);
        if (lightingTask == null) return blocked("No native task for outdoor torch placement");
        lightingTicks = 0;
        lightingTask.controller = controller;
        com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_SURFACE_LIGHTING",
            "dark=" + survey.dark() + "/" + survey.eligible() + ", target=" + lightingTarget.pos()
                + ", action=" + (lightingAcquiring ? "acquire torch" : "place torch"));
        return lightingTask;
    }
    private void rejectTorch(String reason) {
        if (lightingTarget != null) rejectedTorchPositions.add(lightingTarget.pos());
        com.player2.playerengine.observatory.ObservatoryTelemetry.event("SETUP_SURFACE_LIGHT_REJECT",
            String.valueOf(lightingTarget) + ": " + reason);
        lightingTarget = null;
        lightingTask = null;
        lightingTicks = 0;
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
            if (resource.equals("torch")) for(var p:installedTorches) if(environment.matches(p)) count++;
            return count; }
        catch (Exception | LinkageError error) { return 0; }
    }
    public boolean isTerminal() { return done||failed; }
    public boolean isSuccessful() {
        if (!done||failed) return false;
        try {
            for(var p:plan) if(!environment.matches(p)||!environment.safe(p)) {
                reason="Base changed after verification: missing or obstructed "+p.resource()+" at "+p.pos();
                return false;
            }
            if (!environment.surveyLighting(plan, Set.of()).complete()) {
                reason="Outdoor base lighting changed after verification";
                return false;
            }
            return true;
        } catch(Exception|LinkageError error) { reason="Final base world/lighting verification unavailable"; return false; }
    }
    public String controlledReason() { return reason; }
    public boolean isFinished() { return isTerminal(); }
    protected boolean isEqual(Task other) { return this==other; }
    protected String toDebugString() {
        if(terrain!=null&&terrainIndex<terrain.size())return "Starter base site preparation "+terrainIndex+"/"+terrain.size();
        if (index == 3 && !done) return "Outdoor lighting " + lightingPlaced + "/" + SurfaceLightingPlan.MAX_NEW_TORCHES;
        return "Starter base "+index+"/3: "+reason;
    }
    protected void onStop(Task next) { if(!done&&!failed)blocked("Starter base interrupted; recorded positions will be reverified on setup rerun"); }
    public boolean prepareForTransientResume(TaskSuspensionCause cause) {
        return !isTerminal() && (cause==TaskSuspensionCause.GESTURE_OVERLAY||cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
    }
    public void afterChildrenStopped() { active=null; lightingTask=null; }
    public void onTransientResumeAbandoned() { blocked("Starter base resume abandoned"); }
}
