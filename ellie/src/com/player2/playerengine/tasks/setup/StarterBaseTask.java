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
        List<StarterBaseLayout.Placement> loadOrSelect() throws Exception;
        boolean matches(StarterBaseLayout.Placement placement) throws Exception;
        boolean safe(StarterBaseLayout.Placement placement) throws Exception;
        int count(String resource) throws Exception;
        Task acquire(String resource) throws Exception;
        Task place(StarterBaseLayout.Placement placement) throws Exception;
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
                    if(++layerRepairs>3)return blocked("Placed base changed repeatedly before final verification");
                    index=i;terrainIndex=0;active=null;stageTicks=0;
                    reason="Repairing base before final verification";return null;
                }
                done=true; reason="Starter base verified: crafting table, furnace, one chest and usable support";
                return null;
            }
            var target=plan.get(index);
            if (!environment.safe(target)) {
                if(++layerRepairs>3)return blocked("Cannot retain safe base layer at "+target.pos()+" after bounded repairs");
                terrainIndex=0;active=null;stageTicks=0;
                reason="Rechecking support/clearance before placing "+target.resource()+" at "+target.pos();
                return null;
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
    private Task blocked(String detail) { failed=true; reason=detail; return null; }
    public void releaseProtection(){if(environment!=null)environment.releaseProtection();}
    public int verifiedPlacedCount(String resource) {
        if (environment==null || plan==null) return 0;
        try { int count=0;for(var p:plan) if(p.resource().equals(resource)&&environment.matches(p))count++;return count; }
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
            return true;
        } catch(Exception|LinkageError error) { reason="Final base world verification unavailable"; return false; }
    }
    public String controlledReason() { return reason; }
    public boolean isFinished() { return isTerminal(); }
    protected boolean isEqual(Task other) { return this==other; }
    protected String toDebugString() {
        if(terrain!=null&&terrainIndex<terrain.size())return "Starter base site preparation "+terrainIndex+"/"+terrain.size();
        return "Starter base "+index+"/3: "+reason;
    }
    protected void onStop(Task next) { if(!done&&!failed)blocked("Starter base interrupted; recorded positions will be reverified on setup rerun"); }
    public boolean prepareForTransientResume(TaskSuspensionCause cause) {
        return !isTerminal() && (cause==TaskSuspensionCause.GESTURE_OVERLAY||cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
    }
    public void afterChildrenStopped() { active=null; }
    public void onTransientResumeAbandoned() { blocked("Starter base resume abandoned"); }
}
