package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.tasks.base.*;
import java.util.*;

/** Runs native item/placement tasks and verifies each real block before accepting it. */
public final class StarterBaseTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
    public interface Environment {
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
    protected Task onTick() {
        if (done || failed) return null;
        try {
            if (++ticks>12000) return blocked("Starter base active-work budget exhausted; verify site and placement reach");
            if (plan==null) {
                plan=List.copyOf(environment.loadOrSelect());
                if (plan.size()!=10) return blocked("Starter base requires one table, one furnace and eight chest blocks");
            }
            while(index<plan.size() && environment.matches(plan.get(index))) { index++; active=null; stageTicks=0; }
            if (index==plan.size()) {
                for (var p:plan) if (!environment.matches(p)||!environment.safe(p)) return blocked("Placed base changed or became obstructed before final verification");
                done=true; reason="Starter base blocks verified: crafting table, furnace, eight chest blocks with aisles and lid clearance";
                return null;
            }
            var target=plan.get(index);
            if (!environment.safe(target)) return blocked("Base site is occupied, obstructed or unsupported; no blocks will be broken");
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
    protected String toDebugString() { return "Starter base "+index+"/10: "+reason; }
    protected void onStop(Task next) { if(!done&&!failed)blocked("Starter base interrupted; recorded positions will be reverified on setup rerun"); }
    public boolean prepareForTransientResume(TaskSuspensionCause cause) {
        return !isTerminal() && (cause==TaskSuspensionCause.GESTURE_OVERLAY||cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
    }
    public void afterChildrenStopped() { active=null; }
    public void onTransientResumeAbandoned() { blocked("Starter base resume abandoned"); }
}
