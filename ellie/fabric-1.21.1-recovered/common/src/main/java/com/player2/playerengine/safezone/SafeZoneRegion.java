package com.player2.playerengine.safezone;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable inclusive 3-D owner-managed protection cuboid. No Minecraft dependency. */
public record SafeZoneRegion(UUID owner, String dimension, String name,
                             int x1, int y1, int z1, int x2, int y2, int z2) {
    private static final Pattern NAMES=Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Pattern DIMENSIONS=Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public SafeZoneRegion {
        if(owner==null)throw new IllegalArgumentException("Owner required");
        if(dimension==null||!DIMENSIONS.matcher(dimension).matches()||dimension.length()>128)
            throw new IllegalArgumentException("Invalid dimension");
        if(name==null||!NAMES.matcher(name).matches())
            throw new IllegalArgumentException("Zone name: letters, digits, _ or -; max 32 characters");
        int lx=Math.min(x1,x2),hx=Math.max(x1,x2);
        int ly=Math.min(y1,y2),hy=Math.max(y1,y2);
        int lz=Math.min(z1,z2),hz=Math.max(z1,z2);
        x1=lx;x2=hx;y1=ly;y2=hy;z1=lz;z2=hz;
        if(x1< -29999984||x2>29999984||z1< -29999984||z2>29999984||y1< -64||y2>319)
            throw new IllegalArgumentException("Zone outside Minecraft 1.21.1 build limits");
        long volume=(long)(x2-x1+1)*(y2-y1+1)*(z2-z1+1);
        if(x2-x1>160||z2-z1>160||volume>4_000_000L)
            throw new IllegalArgumentException("Zone too large (max 161x161 horizontal and 4 million blocks)");
    }
    public boolean contains(String dim,int x,int y,int z){
        return dimension.equals(dim)&&x>=x1&&x<=x2&&y>=y1&&y<=y2&&z>=z1&&z<=z2;
    }
    public String describe(){return name+" "+dimension+" ["+x1+","+y1+","+z1+"]..["+x2+","+y2+","+z2+"]";}
    public String serialize(){return owner+"\t"+dimension+"\t"+name+"\t"+x1+"\t"+y1+"\t"+z1+"\t"+x2+"\t"+y2+"\t"+z2;}
    public static SafeZoneRegion parse(String line){
        String[] p=line.split("\\t",-1);
        if(p.length!=9)throw new IllegalArgumentException("Invalid zone record field count");
        return new SafeZoneRegion(UUID.fromString(p[0]),p[1],p[2],
            Integer.parseInt(p[3]),Integer.parseInt(p[4]),Integer.parseInt(p[5]),
            Integer.parseInt(p[6]),Integer.parseInt(p[7]),Integer.parseInt(p[8]));
    }
}
