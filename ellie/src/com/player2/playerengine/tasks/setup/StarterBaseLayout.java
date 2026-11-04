package com.player2.playerengine.tasks.setup;

import java.util.*;

/** A conservative workshop footprint: no house generation, terraforming or item deletion. */
public final class StarterBaseLayout {
    public record Pos(int x, int y, int z) { public Pos offset(int dx,int dy,int dz) { return new Pos(x+dx,y+dy,z+dz); } }
    public record Placement(Pos pos, String resource) {}
    public interface Probe {
        boolean air(Pos pos) throws Exception;
        boolean safeFloor(Pos pos) throws Exception;
        boolean matches(Placement placement) throws Exception;
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
        for (int x=-1;x<=7;x++) for (int z=-1;z<=5;z++) {
            Pos p=anchor.offset(x,0,z);
            if (!probe.safeFloor(p.offset(0,-1,0)) || !probe.air(p.offset(0,1,0))) return false;
            Placement placement=expected.get(p);
            if (!probe.air(p) && (placement==null || !probe.matches(placement))) return false;
        }
        return true;
    }
    public static Optional<List<Placement>> select(Pos origin, Probe probe) throws Exception {
        // Search at the original feet height. Never descend into a cave or excavate a pad.
        for (int radius=0;radius<=12;radius++) {
            for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                List<Placement> candidate=at(origin.offset(dx,0,dz));
                // Feet-cell ownership alone is insufficient near an edge: keep the
                // initial body/neighboring cells clear as well as the feet block.
                if (candidate.stream().anyMatch(p -> Math.abs(p.pos().x()-origin.x())<=1
                        && Math.abs(p.pos().z()-origin.z())<=1)) continue;
                if (safe(candidate,probe)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
