package com.player2.playerengine.schematic;

import java.util.*;

/** Immutable imported data. No live world, movement or inventory objects are retained here. */
public final class SchematicPlan {
    public static final int MAX_CELLS=1_000_000,MAX_AXIS_XZ=64,MAX_HEIGHT=384;
    public record Pos(int x,int y,int z) {
        public Pos add(int dx,int dy,int dz){return new Pos(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz));}
        public String key(){return x+","+y+","+z;}
        public static Pos parse(String value){String[] p=value.split(",");if(p.length!=3)throw new IllegalArgumentException("Invalid saved position");return new Pos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    }
    public record State(String id,Map<String,String> properties) {
        public State {
            if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid block ID");
            if(id.equals("minecraft:cave_air")||id.equals("minecraft:void_air")){id="minecraft:air";properties=Map.of();}
            properties=Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public boolean air(){return Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(id);}
        public boolean water(){return id.equals("minecraft:water");}
        public boolean source(){return water()&&properties.getOrDefault("level","0").equals("0");}
        public boolean waterlogged(){return properties.getOrDefault("waterlogged","false").equals("true");}
        public boolean interactiveOpen(){return properties.getOrDefault("open","false").equals("true")
                &&(id.endsWith("_trapdoor")||id.endsWith("_door")||id.endsWith("_fence_gate"));}
        public boolean doubleSlab(){return id.endsWith("_slab")&&properties.getOrDefault("type","").equals("double");}
        public int placementItems(){
            if(air()||water())return 0;
            if(id.endsWith("_door")&&properties.getOrDefault("half","").equals("upper"))return 0;
            if(id.endsWith("_bed")&&properties.getOrDefault("part","").equals("head"))return 0;
            return doubleSlab()?2:1;
        }
        public State placementState(){
            Map<String,String> p=new TreeMap<>(properties);
            if(interactiveOpen())p.put("open","false");
            if(doubleSlab())p.put("type","bottom");
            if(waterlogged())p.put("waterlogged","false");
            return new State(id,p);
        }
    }
    public record Cell(Pos pos,State state) {}
    public final String filename,sha256;
    public final int width,height,length;
    public final List<State> palette;
    private final int[] blocks;
    public final List<Cell> targets;
    public final Map<String,Integer> materialCounts;
    public final List<Integer> layers;

    public SchematicPlan(String filename,String sha256,int width,int height,int length,List<State> palette,int[] blocks) {
        long volume=(long)width*height*length;
        if(width<1||height<1||length<1||width>MAX_AXIS_XZ||length>MAX_AXIS_XZ||height>MAX_HEIGHT||volume>MAX_CELLS||volume!=blocks.length)
            throw new IllegalArgumentException("Schematic limit: 64x384x64, at most 1000000 cells");
        this.filename=Objects.requireNonNull(filename);this.sha256=Objects.requireNonNull(sha256);
        this.width=width;this.height=height;this.length=length;this.palette=List.copyOf(palette);this.blocks=blocks.clone();
        List<Cell> cells=new ArrayList<>();Map<String,Integer> bill=new TreeMap<>();Set<Integer> ys=new TreeSet<>();
        boolean water=false;
        for(int i=0;i<blocks.length;i++) {
            if(blocks[i]<0||blocks[i]>=palette.size())throw new IllegalArgumentException("Invalid block palette index");
            State state=palette.get(blocks[i]);if(state.air())continue;
            int x=i%width,z=(i/width)%length,y=i/(width*length);
            cells.add(new Cell(new Pos(x,y,z),state));ys.add(y);
            if(state.placementItems()>0)bill.merge(state.id(),state.placementItems(),Math::addExact);
            water|=state.source()||state.waterlogged();
        }
        if(cells.isEmpty())throw new IllegalArgumentException("Schematic contains no construction targets");
        if(cells.size()>100_000)throw new IllegalArgumentException("Schematic contains more than 100000 targets");
        if(water)bill.put("minecraft:water_bucket",1);
        // A modest temporary access reserve, separate from blocks consumed by the blueprint.
        if(ys.size()>2)bill.merge("minecraft:cobblestone",64,Math::addExact);
        targets=List.copyOf(cells);materialCounts=Collections.unmodifiableMap(bill);layers=List.copyOf(ys);
    }
    public State at(int x,int y,int z){return palette.get(blocks[(y*length+z)*width+x]);}
    public List<Cell> layer(int y){return targets.stream().filter(c->c.pos.y==y).toList();}
    public int totalItemCount(){return materialCounts.values().stream().mapToInt(Integer::intValue).sum();}
}
