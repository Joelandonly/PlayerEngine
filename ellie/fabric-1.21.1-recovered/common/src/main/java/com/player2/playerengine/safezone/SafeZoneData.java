package com.player2.playerengine.safezone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Per-world, crash-safe store. Corrupt/unreadable existing stores deny ALL bot edits. */
public final class SafeZoneData {
    private static final String HEADER="ELLIE_SAFE_ZONES_V1";
    private static final int LIMIT=64;
    private final Path file;
    private volatile List<SafeZoneRegion> zones=List.of();
    private volatile String fault=null;
    private SafeZoneData(Path file){this.file=file;}
    public static SafeZoneData open(Path file){
        SafeZoneData result=new SafeZoneData(file);
        if(Files.exists(file)){
            try{
                List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
                if(lines.isEmpty()||!HEADER.equals(lines.get(0)))throw new IOException("Unsupported safe-zone file header");
                List<SafeZoneRegion> records=new ArrayList<>();
                Set<String> unique=new HashSet<>();
                for(int i=1;i<lines.size();i++){
                    if(lines.get(i).isBlank())continue;
                    SafeZoneRegion zone=SafeZoneRegion.parse(lines.get(i));
                    String key=zone.owner()+"|"+zone.dimension()+"|"+zone.name();
                    if(!unique.add(key))throw new IOException("Duplicate zone at line "+(i+1));
                    records.add(zone);
                    if(records.size()>LIMIT)throw new IOException("Zone limit exceeded");
                }
                result.zones=List.copyOf(records);
            }catch(Exception e){result.fault="SAFEZONE_STORE_UNREADABLE: "+e.getClass().getSimpleName()+": "+e.getMessage();}
        }
        return result;
    }
    public String fault(){return fault;}
    public boolean contains(String dimension,int x,int y,int z){
        if(fault!=null)return true; // fail closed; never silently discard existing claims
        for(SafeZoneRegion zone:zones)if(zone.contains(dimension,x,y,z))return true;
        return false;
    }
    public List<SafeZoneRegion> owned(UUID owner){
        return zones.stream().filter(z->z.owner().equals(owner)).toList();
    }
    public synchronized void add(SafeZoneRegion zone)throws IOException{
        checkWritable();
        if(zones.size()>=LIMIT)throw new IllegalArgumentException("World has reached the 64-zone limit");
        for(SafeZoneRegion old:zones)if(old.owner().equals(zone.owner())&&old.dimension().equals(zone.dimension())&&old.name().equals(zone.name()))
            throw new IllegalArgumentException("Zone already exists in this dimension. Remove it before recreating.");
        List<SafeZoneRegion> candidate=new ArrayList<>(zones);candidate.add(zone);
        commit(candidate);
    }
    public synchronized boolean remove(UUID owner,String dimension,String name)throws IOException{
        checkWritable();
        List<SafeZoneRegion> candidate=new ArrayList<>(zones);
        boolean removed=candidate.removeIf(z->z.owner().equals(owner)&&z.dimension().equals(dimension)&&z.name().equals(name));
        if(removed)commit(candidate);
        return removed;
    }
    private void checkWritable()throws IOException{if(fault!=null)throw new IOException(fault+". Restore the saved zone file before editing.");}
    private void commit(List<SafeZoneRegion> candidate)throws IOException{
        Files.createDirectories(file.getParent());
        StringBuilder body=new StringBuilder(HEADER).append('\n');
        for(SafeZoneRegion zone:candidate)body.append(zone.serialize()).append('\n');
        Path tmp=Files.createTempFile(file.getParent(),"safezones-",".tmp");
        try{
            Files.writeString(tmp,body.toString(),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            zones=List.copyOf(candidate); // publish only after successful disk commit
        }finally{Files.deleteIfExists(tmp);}
    }
}
