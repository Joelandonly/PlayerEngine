package com.player2.playerengine.nativecommands;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Deterministic, finite village plan for requests that explicitly specify compact scale.
 * Larger/fortified villages continue to use the established BlueprintLibrary generator.
 */
public final class CompactVillageBlueprint {
    private CompactVillageBlueprint() {}
    private record Cell(int x, int y, int z) {}
    private static void put(Map<Cell,String> blocks, int x, int y, int z, String block) {
        blocks.put(new Cell(x,y,z),block);
    }
    public static boolean accepts(String query, String description) {
        String text = ((query == null ? "" : query) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        if (!text.contains("village") && !text.contains("settlement")) return false;
        if (text.matches(".*\\b(huge|massive|large|giant|fortified|47x47|47 by 47)\\b.*")) return false;
        if(text.matches(".*\\b(four|five|six|seven|eight|nine|ten|[4-9]|10)\\s+(?:small\\s+)?houses\\b.*"))return false;
        return true; // Unqualified village requests must not silently become a fortress.
    }
    private static void house(Map<Cell,String> blocks, int x, int y, int z, int entranceX, int entranceZ) {
        for (int dz=0;dz<5;dz++) for (int dx=0;dx<5;dx++) {
            put(blocks,x+dx,y,z+dz,"cobblestone");
            put(blocks,x+dx,y+4,z+dz,"cobblestone");
            boolean wall = dx==0 || dx==4 || dz==0 || dz==4;
            for (int h=1;h<=3;h++) {
                boolean entrance=(x+dx)==entranceX && (z+dz)==entranceZ && h<=2;
                put(blocks,x+dx,y+h,z+dz,wall&&!entrance?"cobblestone":"air");
            }
        }
    }
    public static String generate(int x,int y,int z) {
        // Each house has a real enclosed interior, a two-high doorway and a roof.
        // Roads meet at the central plaza without crossing any wall footprint.
        Map<Cell,String> blocks = new LinkedHashMap<>();
        house(blocks,x+1,y,z+1,x+5,z+3);
        house(blocks,x+11,y,z+1,x+11,z+3);
        house(blocks,x+6,y,z+12,x+8,z+12);
        for(int px=6;px<=10;px++)for(int pz=7;pz<=11;pz++)road(blocks,x+px,y,z+pz);
        for(int px=5;px<=11;px++)road(blocks,x+px,y,z+3);
        for(int pz=4;pz<=12;pz++)road(blocks,x+8,y,z+pz);
        // A compact footprint avoids thousands of mining attempts against its own walls.
        StringBuilder code = new StringBuilder(blocks.size()*37);
        for(var entry:blocks.entrySet()) {
            Cell p=entry.getKey();
            code.append("setBlock(").append(p.x).append(',').append(p.y).append(',').append(p.z)
                .append(",\"").append(entry.getValue()).append("\");\n");
        }
        return code.toString();
    }
    private static void road(Map<Cell,String> blocks,int x,int y,int z) {
        put(blocks,x,y,z,"dirt");
        put(blocks,x,y+1,z,"air");
        put(blocks,x,y+2,z,"air");
    }
    public static String maybeCode(Object root) throws Exception {
        String query=(String)NativeAccess.get(root,"schematicQuery");
        String description=(String)NativeAccess.get(root,"description");
        if(!accepts(query,description))return null;
        Object p=NativeAccess.get(root,"buildPosition");
        if(p==null)throw new IllegalArgumentException("Missing village anchor");
        int x=((Number)NativeAccess.call(p,"method_10263")).intValue();
        int y=((Number)NativeAccess.call(p,"method_10264")).intValue();
        int z=((Number)NativeAccess.call(p,"method_10260")).intValue();
        String code=generate(x,y,z);
        com.player2.playerengine.observatory.ObservatoryTelemetry.event("VILLAGE_COMPACT_BLUEPRINT",
               "17x17 village, three 5x5 houses with two-high entrances and connected plaza/roads at ("+x+","+y+","+z+")");
        return code;
    }
}
