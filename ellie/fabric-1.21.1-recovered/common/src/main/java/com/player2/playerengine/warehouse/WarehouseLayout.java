package com.player2.playerengine.warehouse;

import java.util.*;

/** Pure deterministic modules: 8 x 7 solid floor, four separated doubles, three-wide central aisle. */
public final class WarehouseLayout {
    public static final int MAX_CHESTS = 32;
    public static final int CHESTS_PER_MODULE = 4;
    public record Cell(int x, int y, int z) {}
    public record Pair(Cell first, Cell second, boolean north) {}
    private WarehouseLayout() {}
    public static int expandedCount(int current,int additional){
        if(additional<CHESTS_PER_MODULE||additional%CHESTS_PER_MODULE!=0||additional>MAX_CHESTS-current){
            throw new IllegalArgumentException("Expansion adds double chests in multiples of four, up to "+MAX_CHESTS+" total; current capacity is "+current);
        }
        return current+additional;
    }
    public static List<Pair> chests(int count) {
        if (count < 4 || count > MAX_CHESTS || count % 4 != 0)
            throw new IllegalArgumentException("Capacity must be 4..32 double chests in modules of four");
        List<Pair> result = new ArrayList<>();
        for (int m=0;m<count/4;m++) for(int z : new int[]{1,5}) for(int x : new int[]{1,5})
            result.add(new Pair(new Cell(x,1,m*7+z),new Cell(x+1,1,m*7+z),z==5));
        return List.copyOf(result);
    }
    public static List<Cell> floor(int count) {
        chests(count);
        List<Cell> result = new ArrayList<>();
        for(int z=0;z<count/4*7;z++) for(int x=0;x<8;x++) result.add(new Cell(x,0,z));
        return List.copyOf(result);
    }
    public static List<Cell> floor(int count,boolean bunker) {
        if(!bunker)return floor(count);
        chests(count);
        List<Cell> result=new ArrayList<>();
        for(int z=-1;z<=count/4*7;z++){
            for(int x=z<=3?-4:-1;x<=8;x++)result.add(new Cell(x,0,z));
        }
        result.add(new Cell(-5,0,-1));
        result.add(new Cell(-5,0,0));
        return List.copyOf(result);
    }
    public static Set<Cell> shell(int count,boolean bunker) {
        if(!bunker)return Set.of();
        int depth=count/4*7;
        Set<Cell> result=new LinkedHashSet<>();
        for(int y=1;y<=2;y++){
            for(int z=-1;z<=depth;z++){
                for(int x=-1;x<=8;x++){
                    if(x==-1&&z==0)continue;
                    if(x==-1||x==8||z==-1||z==depth)result.add(new Cell(x,y,z));
                }
            }
            for(int z=-1;z<=3;z++){
                for(int x=-4;x<=-2;x++){
                    if(x==-4||z==-1||z==3&&x!=-3||x==-2&&(z==1||z==2))result.add(new Cell(x,y,z));
                }
            }
        }
        result.add(new Cell(-5,1,-1));
        result.add(new Cell(-5,1,0));
        result.add(new Cell(-5,2,0));
        for(Cell cell:floor(count,true))if(cell.x()!=-5)result.add(new Cell(cell.x(),3,cell.z()));
        return Collections.unmodifiableSet(result);
    }

    public static Set<Cell> aisle(int count) {
        Set<Cell> result=new LinkedHashSet<>();
        for(Cell floor:floor(count)) result.add(new Cell(floor.x,1,floor.z));
        for(Pair pair:chests(count)){result.remove(pair.first);result.remove(pair.second);}
        return Collections.unmodifiableSet(result);
    }
}
