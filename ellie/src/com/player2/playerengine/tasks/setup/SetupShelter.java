package com.player2.playerengine.tasks.setup;

import java.util.*;
import com.player2.playerengine.tasks.setup.StarterBaseLayout.*;

/** A small enclosure for the existing native physical builder, never a world editor. */
public final class SetupShelter {
    public record Plan(Pos floor,String wood) {
        public Plan {if(!Set.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","crimson","warped").contains(wood))throw new IllegalArgumentException("Unknown wood palette");}
        public Pos center(){return floor.offset(2,1,2);}
        public Pos door(){return floor.offset(2,1,0);}
    }
    public interface Probe {
        boolean solid(Pos p)throws Exception;
        boolean open(Pos p)throws Exception;
        boolean door(Pos p)throws Exception;
        boolean canBuild(Placement p)throws Exception;
    }
    private SetupShelter(){}
    public static List<Placement> cells(Plan plan) {
        List<Placement> out=new ArrayList<>(125);
        for(int y=0;y<=4;y++)for(int x=0;x<5;x++)for(int z=0;z<5;z++){
            String material;
            if(y==0||y==4)material=plan.wood()+"_planks";
            else if(x==2&&z==0&&y<=2)material=plan.wood()+"_door";
            else material=x==0||x==4||z==0||z==4?plan.wood()+"_planks":"air";
            out.add(new Placement(plan.floor().offset(x,y,z),material));
        }
        return List.copyOf(out);
    }
    public static boolean verified(Plan plan,Probe probe)throws Exception {
        for(var cell:cells(plan)){
            if(cell.resource().equals("air")){if(!probe.open(cell.pos()))return false;}
            else if(cell.resource().endsWith("_door")){if(!probe.door(cell.pos()))return false;}
            else if(!probe.solid(cell.pos()))return false;
        }
        Pos outside=plan.door().offset(0,0,-1);
        return probe.solid(outside.offset(0,-1,0))&&probe.open(outside)&&probe.open(outside.offset(0,1,0));
    }
    public static boolean buildable(Plan plan,Probe probe)throws Exception {
        for(var cell:cells(plan))if(!probe.canBuild(cell))return false;
        Pos outside=plan.door().offset(0,0,-1);
        return probe.solid(outside.offset(0,-1,0))&&probe.open(outside)&&probe.open(outside.offset(0,1,0));
    }
    public static String code(Plan plan){
        StringBuilder code=new StringBuilder();
        for(var p:cells(plan))code.append("setBlock(").append(p.pos().x()).append(',').append(p.pos().y()).append(',').append(p.pos().z()).append(",\"").append(p.resource()).append("\");\n");
        return code.toString();
    }
}
