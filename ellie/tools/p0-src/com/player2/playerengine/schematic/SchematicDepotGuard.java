package com.player2.playerengine.schematic;

import com.player2.playerengine.schematic.SchematicPlan.Pos;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.player2.playerengine.nativecommands.NativeAccess.call;

/**
 * Project storage is a WORLD asset, not a disposable property of the currently
 * running SchematicProjectTask. The original per-task break-avoidance callback
 * is removed on task stop; this independent, per-controller guard survives that
 * transition and protects recorded depot chests and their supporting blocks.
 *
 * Registration is explicit, and will not mark missing/vanished containers as
 * verified inventory. A human can still break/move blocks manually.
 */
public final class SchematicDepotGuard {
    private SchematicDepotGuard() {}
    private static final Map<Object,Lease> LEASES=Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Lease implements Predicate<Object> {
        final WeakReference<Object> owner;
        final Object world;
        final AtomicReference<Set<Pos>> cells=new AtomicReference<>(Set.of());
        Lease(Object owner,Object world) {
            this.owner=new WeakReference<>(owner);
            this.world=world;
        }
        void add(Pos pos) {
            HashSet<Pos> next=new HashSet<>(cells.get());
            next.add(pos);
            next.add(pos.add(0,-1,0)); // protect the supporting block too
            cells.set(Set.copyOf(next));
        }
        @Override public boolean test(Object candidate) {
            try {
                Pos pos=SchematicWorld.point(candidate);
                if (!cells.get().contains(pos)) return false;
                Object controller=owner.get();
                return controller!=null && call(controller,"getWorld")==world;
            } catch(Exception | LinkageError inaccessible) {
                return false;
            }
        }
    }

    /** Registers restored project storage after the game-level break guard is ready. */
    public static void retainAll(Object controller,Object world,Object extra,List<Pos> depots) {
        if (depots==null || depots.isEmpty()) return;
        for (Pos p:depots) retain(controller,world,extra,p);
    }

    /** Called whenever the existing project coordinator creates a new depot. */
    @SuppressWarnings("unchecked")
    public static void retain(Object controller,Object world,Object extra,Pos depot) {
        if (extra==null) return; // initialize() registers all saved positions once extra is ready
        if (controller==null || world==null || depot==null)
            throw new IllegalArgumentException("Incomplete project storage guard");
        try {
            Object mutex=call(extra,"getBreakMutex");
            if (mutex==null) throw new IllegalStateException("Missing native break mutex");
            synchronized(mutex) {
                List<Object> avoiders=(List<Object>)call(extra,"getBreakAvoiders");
                synchronized(LEASES) {
                    Lease prior=LEASES.get(controller);
                    if (prior==null || prior.world!=world || !avoiders.contains(prior)) {
                        if (prior!=null) avoiders.remove(prior);
                        prior=new Lease(controller,world);
                        avoiders.add(prior);
                        LEASES.put(controller,prior);
                    }
                    prior.add(depot);
                }
            }
        } catch(Exception | LinkageError error) {
            throw new IllegalStateException("Cannot preserve schematic depot break guard",error);
        }
    }

    /** Also used by verified-resource selectors, even after a schematic is interrupted. */
    public static boolean protects(Object controller,Object position) {
        if (controller==null || position==null) return false;
        Lease lease;
        synchronized(LEASES){lease=LEASES.get(controller);}
        return lease!=null && lease.test(position);
    }

    public static int retainedCells(Object controller) {
        synchronized(LEASES){
            Lease lease=LEASES.get(controller);
            return lease==null?0:lease.cells.get().size();
        }
    }
}
