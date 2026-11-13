package com.player2.playerengine.warehouse;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Atomic, versioned metadata. Observed contents are informational, never allocation authority. */
public final class WarehouseLedger {
    private static final long[] REPLACE_RETRY_MS = {20, 40, 80};
    private final Path file;
    private final Properties values=new Properties();
    private String fault;
    public String fault(){return fault;}
    public WarehouseLedger(Path file) throws IOException {
        this.file=Objects.requireNonNull(file);
        if(Files.exists(file)) {
            try(InputStream in=Files.newInputStream(file)){values.load(in);}
            if(!"1".equals(values.getProperty("schema")))throw new IOException("Unsupported warehouse schema");
        }
    }
    private WarehouseLedger(WarehouseLedger original) {
        file=original.file;
        values.putAll(original.values);
    }
    public WarehouseLedger draft(){return new WarehouseLedger(this);}
    public void commit(WarehouseLedger draft) throws IOException {
        draft.save();
        values.clear();
        values.putAll(draft.values);
    }
    public boolean canChooseSite(){
        return !get("state","").equals("READY") && !values.containsKey("terrainStarted")
            && values.stringPropertyNames().stream().noneMatch(key->key.startsWith("placed.")||key.startsWith("container."));
    }
    public String get(String key,String fallback){return values.getProperty(key,fallback);}
    public int number(String key,int fallback){return Integer.parseInt(get(key,Integer.toString(fallback)));}
    public boolean exists(){return values.containsKey("origin");}
    public void set(String key,Object value){values.setProperty(key,String.valueOf(value));}
    public void save() throws IOException {
        try{saveAtomic();fault=null;}catch(IOException failure){
            fault=failure.getClass().getSimpleName()+": "+failure.getMessage();
            throw new IOException("Warehouse journal write failed: "+fault,failure);
        }
    }
    private void saveAtomic() throws IOException {
        values.setProperty("schema","1");
        Files.createDirectories(file.getParent());
        Path tmp=Files.createTempFile(file.getParent(),"warehouse-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(tmp)){values.store(out,"Ellie authorized warehouse; live validation required");}
            replaceWithRetry(tmp);
        }finally{Files.deleteIfExists(tmp);}
    }
    private void replaceWithRetry(Path tmp) throws IOException {
        for(int attempt=0;;attempt++){
            try{
                try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
                return;
            }catch(FileSystemException failure){
                if(attempt==REPLACE_RETRY_MS.length||failure instanceof NoSuchFileException)throw failure;
                try{Thread.sleep(REPLACE_RETRY_MS[attempt]);}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
    }
}
