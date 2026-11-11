package com.player2.playerengine.safezone;

import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** Global policy for NPC-driven block edits. Never controls player interactions. */
public final class SafeZoneService {
    private static final Map<MinecraftServer,SafeZoneData> WORLDS=new WeakHashMap<>();
    private static final Map<String,Long> WARNED=new ConcurrentHashMap<>();
    private SafeZoneService(){}
    public static SafeZoneData data(MinecraftServer server){
        if(server==null)throw new IllegalStateException("Minecraft server unavailable");
        synchronized(WORLDS){
            return WORLDS.computeIfAbsent(server,s->{
                Path world=s.getWorldPath(LevelResource.ROOT);
                SafeZoneData store=SafeZoneData.open(Player2NpcPersistencePaths.persistentDataRoot(world).resolve("ellie-safe-zones.tsv"));
                if(store.fault()!=null)System.err.println("[EllieSafeZone] "+store.fault()+"; blocking NPC modifications");
                return store;
            });
        }
    }
    public static boolean protectedAt(Level level,BlockPos pos){
        if(level==null||pos==null)return false;
        if(level.isClientSide())return false;
        MinecraftServer server=level.getServer();
        if(server==null)return true; // unknown server: never risk a destructive edit
        return data(server).contains(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
    }
    public static boolean deny(Level level,BlockPos pos,String action){
        if(!protectedAt(level,pos))return false;
        // Rate-limited telemetry; avoid a wall of lines during Baritone path retries.
        String key=(level==null?"unknown":level.dimension().location().toString())+":"+action+":"+pos;
        long now=System.currentTimeMillis(),old=WARNED.getOrDefault(key,0L);
        if(now-old>15_000L){
            WARNED.put(key,now);
            String detail=action+" blocked at "+pos+" (owner safe zone)";
            System.out.println("[EllieSafeZone] "+detail);
            try{com.player2.playerengine.observatory.ObservatoryTelemetry.event("SAFEZONE_DENY",detail);}catch(Exception|LinkageError ignored){}
            if(WARNED.size()>2048)WARNED.clear();
        }
        return true;
    }
}
