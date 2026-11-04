package com.player2.playerengine.tasks.setup;

import java.util.*;

/** Three-station workshop; existing ground first, minimum supported-platform fallback. */
public final class StarterBaseLayout {
    public record Pos(int x, int y, int z) { public Pos offset(int dx,int dy,int dz) { return new Pos(x+dx,y+dy,z+dz); } }
    public record Placement(Pos pos, String resource) {}
    public interface Probe {
        boolean air(Pos pos) throws Exception;
        boolean safeFloor(Pos pos) throws Exception;
        boolean matches(Placement placement) throws Exception;
        default boolean canClear(Pos pos) throws Exception { return air(pos); }
        default boolean canFound(Pos pos) throws Exception { return safeFloor(pos); }
    }
    private StarterBaseLayout() {}
    public static List<Placement> at(Pos anchor) {
        // No village, platform or 8-chest depot: the initial setup needs three blocks.
        // One empty block between stations leaves room for interaction/pathing.
        return List.of(
            new Placement(anchor, "crafting_table"),
            new Placement(anchor.offset(2,0,0), "furnace"),
            new Placement(anchor.offset(4,0,0), "chest")
        );
    }
    public static boolean safe(List<Placement> plan, Probe probe) throws Exception {
        if (plan.size()!=3) return false;
        Pos anchor = plan.getFirst().pos();
        Map<Pos,Placement> expected = new HashMap<>();
        for (Placement p : plan) expected.put(p.pos(),p);
        if (expected.size()!=3) return false;
        for (Placement placement:plan) {
            Pos p=placement.pos();
            if (!probe.safeFloor(p.offset(0,-1,0)) || !probe.air(p.offset(0,1,0))) return false;
            if (!probe.air(p) && !probe.matches(placement)) return false;
            if(!hasAccess(p,probe))return false;
        }
        return true;
    }
    public static boolean hasAccess(Pos p,Probe probe)throws Exception {
        for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            Pos side=p.offset(offset[0],0,offset[1]);
            if(probe.safeFloor(side.offset(0,-1,0))&&probe.air(side)&&probe.air(side.offset(0,1,0)))return true;
        }
        return false;
    }
    /** Reuse a real nearby bench as the three-station anchor, if its full site is safe/buildable. */
    public static Optional<List<Placement>> reuse(Pos origin, Probe probe) throws Exception {
        for(int radius=0;radius<=12;radius++)for(int dy:new int[]{0,1,-1,2,3})
          for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            List<Placement> candidate=at(origin.offset(dx,dy,dz));
            if(!probe.matches(candidate.getFirst()))continue;
            if(candidate.stream().anyMatch(p->Math.abs(p.pos().x()-origin.x())<=1&&Math.abs(p.pos().z()-origin.z())<=1))continue;
            if(safe(candidate,probe)||buildable(candidate,probe))return Optional.of(candidate);
          }
        return Optional.empty();
    }
    /** A saved project with any verified workstation must keep its recorded layout. */
    public static List<Placement> reuseUnstarted(List<Placement> saved,Pos origin,Probe probe)throws Exception {
        for(Placement p:saved)if(probe.matches(p))return saved;
        return reuse(origin,probe).orElse(saved);
    }
    public static Optional<List<Placement>> select(Pos origin, Probe probe) throws Exception {
        return select(origin,probe,false);
    }
    public static Optional<List<Placement>> selectBuildable(Pos origin,Probe probe)throws Exception {
        return select(origin,probe,true);
    }
    /** Only considered after a site on naturally supported ground cannot be found. */
    public static Optional<List<Placement>> selectPlatform(Pos origin,Probe probe)throws Exception {
        return select(origin,probe,2);
    }
    /** Floor cells required for a dry 3-station workshop and a one-wide service aisle. */
    public static List<Pos> supportCells(List<Placement> plan) {
        if(plan.size()!=3)throw new IllegalArgumentException("Expected three workstation blocks");
        Pos anchor=plan.getFirst().pos();
        LinkedHashSet<Pos> cells=new LinkedHashSet<>();
        for (Placement p:plan)cells.add(p.pos().offset(0,-1,0));
        for (int x=0;x<=4;x++)cells.add(anchor.offset(x,-1,1));
        return List.copyOf(cells);
    }
    /** Never put a clearance and floor job at the same position. */
    public static List<Placement> terrain(List<Placement> plan,Pos origin,boolean platform) {
        LinkedHashSet<Pos> clearance=new LinkedHashSet<>();
        for(Placement placement:plan) {
            clearance.add(placement.pos().offset(0,1,0));
            clearance.add(placement.pos());
        }
        if(platform){
            Pos anchor=plan.getFirst().pos();
            for(int x=0;x<=4;x++){
                clearance.add(anchor.offset(x,1,1));
                clearance.add(anchor.offset(x,0,1));
            }
        }
        List<Placement> jobs=new ArrayList<>();
        for(Pos pos:clearance)jobs.add(new Placement(pos,"air"));
        if(platform){
            List<Pos> floor=new ArrayList<>(supportCells(plan));
            floor.sort(Comparator.comparingLong(pos -> Math.abs((long)pos.x()-origin.x())+
                Math.abs((long)pos.z()-origin.z())));
            for(Pos pos:floor)jobs.add(new Placement(pos,"cobblestone"));
        }
        return List.copyOf(jobs);
    }
    public static List<Placement> terrain(List<Placement> plan,Pos origin) {
        return terrain(plan,origin,false);
    }
    private static boolean buildable(List<Placement> plan,Probe probe)throws Exception {
        for(Placement placement:plan) {
            Pos p=placement.pos();
            if(!probe.safeFloor(p.offset(0,-1,0)) || !hasAccess(p,probe)) return false;
            if(!probe.air(p.offset(0,1,0)) && !probe.canClear(p.offset(0,1,0)))return false;
            if(!probe.air(p) && !probe.matches(placement) && !probe.canClear(p))return false;
        }
        return true;
    }
    private static boolean platformBuildable(List<Placement> plan,Probe probe)throws Exception {
        for(Placement placement:plan){
            Pos p=placement.pos();
            if(!probe.air(p)&&!probe.matches(placement)&&!probe.canClear(p))return false;
            if(!probe.air(p.offset(0,1,0))&&!probe.canClear(p.offset(0,1,0)))return false;
        }
        Pos anchor=plan.getFirst().pos();
        for(int x=0;x<=4;x++)for(int y=0;y<=1;y++){
            Pos aisle=anchor.offset(x,y,1);
            if(!probe.air(aisle)&&!probe.canClear(aisle))return false;
        }
        for(Pos floor:supportCells(plan))if(!probe.canFound(floor))return false;
        return true;
    }
    private static Optional<List<Placement>> select(Pos origin,Probe probe,boolean prepare)throws Exception {
        return select(origin,probe,prepare?1:0);
    }
    private static Optional<List<Placement>> select(Pos origin,Probe probe,int mode)throws Exception {
        // Prefer natural surface, only then allow a dry compact platform on void/water.
        for (int radius=0;radius<=12;radius++) {
          for(int dy:new int[]{0,1,-1,2,3}) {
            for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                List<Placement> candidate=at(origin.offset(dx,dy,dz));
                // Feet-cell ownership alone is insufficient near an edge: keep the
                // initial body/neighboring cells clear as well as the feet block.
                if (candidate.stream().anyMatch(p -> Math.abs(p.pos().x()-origin.x())<=1
                        && Math.abs(p.pos().z()-origin.z())<=1)) continue;
                // A platform aisle must not excavate / place blocks around Ellie herself.
                if(mode==2 && Math.abs(candidate.getFirst().pos().y()-origin.y())<=1) {
                    Pos a=candidate.getFirst().pos();
                    boolean occupiesPlayerLane=false;
                    for(int x=0;x<=4;x++)if(Math.abs(a.x()+x-origin.x())<=1 &&
                        Math.abs(a.z()+1-origin.z())<=1){occupiesPlayerLane=true;break;}
                    if(occupiesPlayerLane)continue;
                }
                if ((mode==0?safe(candidate,probe):mode==1?buildable(candidate,probe):platformBuildable(candidate,probe))) return Optional.of(candidate);
            }
          }
        }
        return Optional.empty();
    }
}
