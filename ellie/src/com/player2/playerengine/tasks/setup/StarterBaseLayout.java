package com.player2.playerengine.tasks.setup;

import java.util.*;

/** A workshop footprint with explicit native clearing/foundation jobs; no direct world edits. */
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
        List<Placement> result = new ArrayList<>();
        result.add(new Placement(anchor, "crafting_table"));
        result.add(new Placement(anchor.offset(2,0,0), "furnace"));
        for (int z : new int[]{2,4}) for (int x : new int[]{0,2,4,6})
            result.add(new Placement(anchor.offset(x,0,z), "chest"));
        return List.copyOf(result);
    }
    public static boolean safe(List<Placement> plan, Probe probe) throws Exception {
        if (plan.size()!=10) return false;
        Pos anchor = plan.getFirst().pos();
        Map<Pos,Placement> expected = new HashMap<>();
        for (Placement p : plan) expected.put(p.pos(),p);
        if (expected.size()!=10) return false;
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
    public static Optional<List<Placement>> select(Pos origin, Probe probe) throws Exception {
        return select(origin,probe,false);
    }
    public static Optional<List<Placement>> selectBuildable(Pos origin,Probe probe)throws Exception {
        return select(origin,probe,true);
    }
    public static List<Placement> terrain(List<Placement> plan,Pos origin) {
        Pos anchor=plan.getFirst().pos();List<Placement> jobs=new ArrayList<>();
        for(int dy:new int[]{1,0})for(int x=-1;x<=7;x++)for(int z=-1;z<=5;z++) {
            Pos p=anchor.offset(x,dy,z);
            // Existing requested stations are retained by the world adapter.
            jobs.add(new Placement(p,"air"));
        }
        List<Placement> floor=new ArrayList<>();
        for(int x=-1;x<=7;x++)for(int z=-1;z<=5;z++)floor.add(new Placement(anchor.offset(x,-1,z),"cobblestone"));
        floor.sort(Comparator.comparingLong(p->Math.abs((long)p.pos().x()-origin.x())+Math.abs((long)p.pos().z()-origin.z())));
        jobs.addAll(floor);return List.copyOf(jobs);
    }
    private static boolean buildable(List<Placement> plan,Probe probe)throws Exception {
        Map<Pos,Placement> expected=new HashMap<>();for(var p:plan)expected.put(p.pos(),p);
        for(var job:terrain(plan,plan.getFirst().pos())) {
            Pos p=job.pos();
            if(job.resource().equals("cobblestone")){if(!probe.canFound(p))return false;}
            else if(!(expected.containsKey(p)&&probe.matches(expected.get(p)))&&!probe.canClear(p))return false;
        }
        return true;
    }
    private static Optional<List<Placement>> select(Pos origin,Probe probe,boolean prepare)throws Exception {
        // Include the adjacent surface heights (e.g. standing on a path block),
        // without a deep vertical search or terrain excavation.
        for (int radius=0;radius<=12;radius++) {
          for(int dy:new int[]{0,1,-1}) {
            for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                List<Placement> candidate=at(origin.offset(dx,dy,dz));
                // Feet-cell ownership alone is insufficient near an edge: keep the
                // initial body/neighboring cells clear as well as the feet block.
                if (candidate.stream().anyMatch(p -> Math.abs(p.pos().x()-origin.x())<=1
                        && Math.abs(p.pos().z()-origin.z())<=1)) continue;
                if (prepare?buildable(candidate,probe):safe(candidate,probe)) return Optional.of(candidate);
            }
          }
        }
        return Optional.empty();
    }
}
