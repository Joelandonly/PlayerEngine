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
        for(var cell:cells(plan))if(!suitable(cell,probe))return false;
        return access(plan,probe).isPresent();
    }
    public static boolean buildable(Plan plan,List<Pos> access,Probe probe)throws Exception {
        for(var cell:cells(plan))if(!suitable(cell,probe))return false;
        return !access.isEmpty()&&accessBuildable(access,probe);
    }
    public static Optional<Placement> obstruction(Plan plan,Probe probe)throws Exception {for(var cell:cells(plan))if(!suitable(cell,probe))return Optional.of(cell);return Optional.empty();}
    private static boolean suitable(Placement cell,Probe probe)throws Exception {
        if(cell.resource().equals("air"))return probe.open(cell.pos())||probe.canBuild(cell);
        if(cell.resource().endsWith("_door"))return probe.door(cell.pos())||probe.canBuild(cell);
        return probe.solid(cell.pos())||probe.canBuild(cell);
    }
    /** A bounded stair/bridge connection to real walkable terrain. Full cubes may
     * float; they need a placement connection, not a pillar under every square. */
    public static Optional<List<Pos>> access(Plan plan,Probe probe)throws Exception {
        Pos start=plan.door().offset(0,-1,-1);
        Map<Pos,Pos> previous=new LinkedHashMap<>();Map<Pos,Integer> depth=new HashMap<>();
        ArrayDeque<Pos> queue=new ArrayDeque<>();previous.put(start,null);depth.put(start,0);queue.add(start);
        while(!queue.isEmpty()&&previous.size()<=4096){
            Pos p=queue.removeFirst();
            if(!accessBuildable(List.of(p),probe))continue;
            if(probe.solid(p)&&probe.open(p.offset(0,1,0))&&probe.open(p.offset(0,2,0))){
                List<Pos> path=new ArrayList<>();for(Pos n=p;n!=null;n=previous.get(n))path.add(n);Collections.reverse(path);if(accessBuildable(path,probe))return Optional.of(List.copyOf(path));
            }
            if(depth.get(p)>=6)continue;
            for(int[] side:new int[][]{{0,-1},{1,0},{-1,0},{0,1}})for(int dy:new int[]{0,-1,1}){
                Pos next=p.offset(side[0],dy,side[1]);
                if(next.x()>=plan.floor().x()&&next.x()<=plan.floor().x()+4&&next.z()>=plan.floor().z()&&next.z()<=plan.floor().z()+4)continue;
                if(previous.containsKey(next))continue;
                boolean sameColumn=false;for(Pos ancestor=p;ancestor!=null;ancestor=previous.get(ancestor))if(ancestor.x()==next.x()&&ancestor.z()==next.z()){sameColumn=true;break;}
                if(sameColumn)continue;
                previous.put(next,p);depth.put(next,depth.get(p)+1);queue.addLast(next);
            }
        }
        return Optional.empty();
    }
    public static List<Pos> accessBlocks(List<Pos> path){
        Set<Pos> blocks=new LinkedHashSet<>(path);
        for(int i=1;i<path.size();i++)if(path.get(i).y()!=path.get(i-1).y()){
            Pos higher=path.get(i).y()>path.get(i-1).y()?path.get(i):path.get(i-1);blocks.add(higher.offset(0,-1,0));
        }
        return List.copyOf(blocks);
    }
    public static boolean accessBuildable(List<Pos> path,Probe probe)throws Exception {
        for(Pos p:accessBlocks(path))if(!probe.solid(p)&&!probe.canBuild(new Placement(p,"dirt")))return false;
        for(Pos p:path){
            if(!probe.solid(p)&&!probe.canBuild(new Placement(p,"dirt")))return false;
            for(int y=1;y<=2;y++)if(!probe.open(p.offset(0,y,0))&&!probe.canBuild(new Placement(p.offset(0,y,0),"air")))return false;
        }
        return true;
    }
    public static boolean accessVerified(List<Pos> path,Probe probe)throws Exception {
        for(Pos p:accessBlocks(path))if(!probe.solid(p))return false;
        for(Pos p:path)if(!probe.solid(p)||!probe.open(p.offset(0,1,0))||!probe.open(p.offset(0,2,0)))return false;
        return !path.isEmpty();
    }
    public static List<Pos> structuralPositions(Plan plan,List<Pos> access){
        List<Pos> out=new ArrayList<>();for(var cell:cells(plan))if(!cell.resource().equals("air")&&!cell.resource().endsWith("_door"))out.add(cell.pos());out.addAll(accessBlocks(access));return List.copyOf(out);
    }
    /** Reuse any existing suitable solid block; only missing cells demand materials. */
    public static String code(Plan plan,List<String> materials,List<Pos> access,Probe probe)throws Exception {
        if(materials.size()!=96+accessBlocks(access).size())throw new IllegalArgumentException("Structural material roles do not match the recorded plan");
        Map<Pos,String> assigned=new LinkedHashMap<>();int index=0;
        for(Pos p:structuralPositions(plan,access))assigned.put(p,materials.get(index++));
        StringBuilder code=new StringBuilder();Set<Pos> written=new HashSet<>();
        for(var cell:cells(plan)){
            String block;
            if(cell.resource().equals("air")){block="air";}
            else if(cell.resource().endsWith("_door")){if(probe.door(cell.pos()))continue;block=cell.resource();}
            else{if(probe.solid(cell.pos()))continue;block=assigned.get(cell.pos());}
            emit(code,cell.pos(),block);written.add(cell.pos());
        }
        for(Pos p:accessBlocks(access))if(!probe.solid(p)&&written.add(p))emit(code,p,assigned.get(p));
        for(Pos p:access){
            for(int y=1;y<=2;y++)if(written.add(p.offset(0,y,0)))emit(code,p.offset(0,y,0),"air");
        }
        return code.toString();
    }
    private static void emit(StringBuilder code,Pos p,String block){code.append("setBlock(").append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(",\"").append(block).append("\");\n");}
    public static String code(Plan plan){
        StringBuilder code=new StringBuilder();
        for(var p:cells(plan))code.append("setBlock(").append(p.pos().x()).append(',').append(p.pos().y()).append(',').append(p.pos().z()).append(",\"").append(p.resource()).append("\");\n");
        return code.toString();
    }
}
