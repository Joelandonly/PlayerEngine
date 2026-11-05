package com.player2.playerengine.tasks.deferred;
import java.nio.file.Path;
/** No world storage in the substituted production-entry harness. */
public final class DeferredJobStore {
    public static DeferredJobStore get(){return new DeferredJobStore();}
    public Path worldRoot(){throw new IllegalStateException("No Minecraft world in this harness");}
}
