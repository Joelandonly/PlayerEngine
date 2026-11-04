package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;

/** Reuse the village builder's resource-source exclusion without blocking intentional excavation. */
public final class SetupBuildZone implements AutoCloseable {
    private boolean finished;
    private final Shape schematic;
    private final Object controller;
    private record Shape(Origin origin,int sx,int sz) {}
    private record Origin(int x,int z) {
        public int getX(){return x;}
        public int getZ(){return z;}
    }
    public SetupBuildZone(Object controller,int x,int z,int width,int depth) {
        if(controller==null||width<1||depth<1||width>256||depth>256)throw new IllegalArgumentException("Invalid setup build zone");
        this.controller=controller;this.schematic=new Shape(new Origin(x,z),width,depth);ensure();
    }
    public void ensure(){if(!finished)VillageSiteGuard.update(this,controller);}
    public void close(){finished=true;VillageSiteGuard.clearForJob(this);}
}
