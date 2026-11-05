package com.player2.playerengine.reliability;

import com.player2.playerengine.agentic.StorageIntelligence;
import java.util.*;
import java.lang.reflect.Array;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Shared setup-platform material policy: actual full cubes, not a cobblestone-only recipe. */
public final class StructuralMaterials {
    private StructuralMaterials(){}
    /** TaskCatalogue keys are bare vanilla names, while block/item identifiers may be namespaced. */
    public static String catalogueName(String registryId){
        Objects.requireNonNull(registryId,"registryId");
        return registryId.startsWith("minecraft:")?registryId.substring("minecraft:".length()):registryId;
    }
    public static boolean safe(String id,boolean full,boolean falling,boolean blockEntity){
        return full&&!falling&&!blockEntity&&!List.of("tnt","magma","campfire","cactus","fire","powder_snow","_ore","diamond","emerald","netherite","gold_block","iron_block","raw_iron_block","raw_gold_block","ancient_debris","respawn_anchor","note_block","coal_block","redstone_block","obsidian").stream().anyMatch(id::contains);
    }
    public static Map<String,Integer> carried(Object controller)throws Exception {
        List<?> main=(List<?>)get(call(controller,"getInventory"),"main");
        if(main.size()!=36)throw new IllegalStateException("Expected actual 36 main slots for platform materials");
        Map<String,Integer> stock=new LinkedHashMap<>();
        for(Object stack:main){
            if(Boolean.TRUE.equals(call(stack,"method_7960")))continue;
            Object item=call(stack,"method_7909");String id=String.valueOf(item);if(!id.contains(":"))id="minecraft:"+id;
            if(usable(controller,item,id))stock.merge(id,((Number)call(stack,"method_7947")).intValue(),Integer::sum);
        }
        return stock;
    }
    public static Map<String,Integer> available(Object controller)throws Exception {
        Map<String,Integer> stock=new LinkedHashMap<>(carried(controller));
        Map<String,Integer> stored=StorageIntelligence.setupStock(controller);
        for(var item:stored.entrySet())try{
            Object matches=callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",catalogueName(item.getKey()));
            if(Array.getLength(matches)==1&&usable(controller,Array.get(matches,0),item.getKey()))stock.merge(item.getKey(),item.getValue(),Integer::sum);
        }catch(Exception ignored){}
        return stock;
    }
    private static boolean usable(Object controller,Object item,String id)throws Exception {
        try{
            Object block=call(item,"method_7711"),state=call(block,"method_9564");
            Object player=call(controller,"getPlayer");Object pos=Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(
                (int)Math.floor(((Number)call(player,"method_23317")).doubleValue()),(int)Math.floor(((Number)call(player,"method_23318")).doubleValue()),(int)Math.floor(((Number)call(player,"method_23321")).doubleValue()));
            return safe(id,Boolean.TRUE.equals(call(state,"method_26234",call(controller,"getWorld"),pos)),Class.forName("net.minecraft.class_2346").isInstance(block),Boolean.TRUE.equals(call(state,"method_31709")));
        }catch(NoSuchMethodException notBlock){return false;}
    }
    private static final Set<String> COMMON_ORES=Set.of("coal_ore","deepslate_coal_ore","iron_ore","deepslate_iron_ore","copper_ore","deepslate_copper_ore");
    private static final Set<String> NATURAL=Set.of("stone","deepslate","dirt","grass_block","coarse_dirt","rooted_dirt","podzol","mycelium","andesite","diorite","granite","tuff","calcite","dripstone_block","clay","mud","moss_block","snow","snow_block","sand","red_sand","gravel","sandstone","red_sandstone","netherrack","blackstone","basalt","end_stone","short_grass","tall_grass","fern","large_fern","dead_bush","vine","dandelion","poppy","blue_orchid","allium","azure_bluet","oxeye_daisy","cornflower","lily_of_the_valley","rose_bush","peony","lilac","sunflower","pink_petals","brown_mushroom","red_mushroom","mushroom_stem","brown_mushroom_block","red_mushroom_block","bamboo","sugar_cane");
    private static String name(String id){String value=id.replace("Block{","").replace("}","");return value.contains(":")?value.substring(value.indexOf(':')+1):value;}
    public static boolean commonOre(String id){return COMMON_ORES.contains(name(id));}
    public static boolean naturalToClear(String id){
        String value=name(id);if(COMMON_ORES.contains(value))return true;
        if(!safe(id,true,false,false)||value.startsWith("potted_"))return false;
        return NATURAL.contains(value)||value.endsWith("_sapling")||value.endsWith("_log")||value.endsWith("_leaves")||value.endsWith("_wood")||value.endsWith("_stem")||value.endsWith("_hyphae");
    }
    public static String fallback(Object controller){
        try{
            Object player=call(controller,"getPlayer"),world=call(controller,"getWorld");int x=(int)Math.floor(((Number)call(player,"method_23317")).doubleValue()),y=(int)Math.floor(((Number)call(player,"method_23318")).doubleValue()),z=(int)Math.floor(((Number)call(player,"method_23321")).doubleValue()),soil=0,rock=0;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-3;dy<=0;dy++){
                Object p=Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(x+dx,y+dy,z+dz);
                if(!Boolean.TRUE.equals(call(world,"method_22340",p)))continue;
                String id=String.valueOf(call(call(world,"method_8320",p),"method_26204"));
                if(id.contains(":dirt}")||id.contains(":grass_block}"))soil++;if(id.contains(":stone}")||id.contains(":deepslate}"))rock++;
            }
            return rock>soil?"minecraft:cobblestone":"minecraft:dirt";
        }catch(Exception|LinkageError unavailable){return "minecraft:cobblestone";}
    }
    public static String preferred(Map<String,Integer> stock,String fallback){return stock.entrySet().stream().filter(e->e.getValue()>0).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(fallback);}
    /** Existing solid cells consume no inventory reservation. Mix carried/stored stacks first. */
    public static List<String> allocate(List<Boolean> missing,Map<String,Integer> stock,String fallback){
        Map<String,Integer> remaining=new LinkedHashMap<>(stock);List<String> out=new ArrayList<>();
        for(boolean needed:missing){String id=needed?preferred(remaining,fallback):fallback;out.add(id);if(needed&&remaining.getOrDefault(id,0)>0)remaining.put(id,remaining.get(id)-1);}
        return List.copyOf(out);
    }
}
