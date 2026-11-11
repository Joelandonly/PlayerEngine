package com.player2.playerengine.safezone;

import com.player2.playerengine.PlayerEngineController;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

/** Runs only in SetupDirectRoute after authenticated owner UUID verification. */
public final class SafeZoneNativeCommand {
    private static final Map<MinecraftServer,Map<UUID,Selection>> CORNERS=new WeakHashMap<>();
    private record Selection(String dimension,BlockPos first,BlockPos second){}
    private SafeZoneNativeCommand(){}
    public static String execute(PlayerEngineController controller,UUID authenticatedOwner,String input)throws IOException{
        if(controller==null||authenticatedOwner==null||!(controller.getOwner() instanceof ServerPlayer owner)
             ||!owner.getUUID().equals(authenticatedOwner))
            throw new IllegalArgumentException("Authenticated owner required");
        ServerLevel world=controller.getWorld();
        if(world==null||owner.level()!=world)throw new IllegalArgumentException("Owner and Ellie must be in the same dimension");
        String dim=world.dimension().location().toString();
        SafeZoneData store=SafeZoneService.data(world.getServer());
        if(store.fault()!=null)return store.fault()+". NPC block modifications locked until the zone file is restored.";
        String[] tokens=input.trim().split("\\s+");
        if(tokens.length<2||!tokens[0].equals("safezone"))return usage();
        String verb=tokens[1].toLowerCase(Locale.ROOT);
        synchronized(CORNERS){
            Map<UUID,Selection> pending=CORNERS.computeIfAbsent(world.getServer(),key->new HashMap<>());
            Selection select=pending.get(authenticatedOwner);
            switch(verb){
                case "pos1", "pos2" -> {
                    if(tokens.length!=2)throw new IllegalArgumentException("Use: safezone "+verb);
                    BlockPos here=owner.blockPosition().immutable();
                    if(select==null||!select.dimension().equals(dim))select=new Selection(dim,null,null);
                    select=verb.equals("pos1")?new Selection(dim,here,select.second()):new Selection(dim,select.first(),here);
                    pending.put(authenticatedOwner,select);
                    return verb+" set to "+here.toShortString()+" in "+dim+". Selection is not yet saved.";
                }
                case "save" -> {
                    if(tokens.length!=3)throw new IllegalArgumentException("Use: safezone save <name>");
                    if(select==null||!select.dimension().equals(dim)||select.first()==null||select.second()==null)
                        throw new IllegalArgumentException("Set safezone pos1 and pos2 in this dimension first");
                    SafeZoneRegion zone=new SafeZoneRegion(authenticatedOwner,dim,name(tokens[2]),
                        select.first().getX(),select.first().getY(),select.first().getZ(),
                        select.second().getX(),select.second().getY(),select.second().getZ());
                    store.add(zone);pending.remove(authenticatedOwner);
                    return "Saved protected region "+zone.describe()+". Ellie cannot place or break blocks inside.";
                }
                case "here" -> {
                    if(tokens.length!=5)throw new IllegalArgumentException("Use: safezone here <name> <horizontal_radius> <vertical_radius>");
                    int radius=number(tokens[3]),height=number(tokens[4]);
                    if(radius<0||radius>48||height<0||height>32)throw new IllegalArgumentException("Radius 0..48, vertical radius 0..32");
                    BlockPos p=owner.blockPosition();
                    SafeZoneRegion zone=new SafeZoneRegion(authenticatedOwner,dim,name(tokens[2]),
                        p.getX()-radius,p.getY()-height,p.getZ()-radius,p.getX()+radius,p.getY()+height,p.getZ()+radius);
                    store.add(zone);return "Saved protected region "+zone.describe();
                }
                case "add" -> {
                    if(tokens.length!=9)throw new IllegalArgumentException("Use: safezone add <name> x1 y1 z1 x2 y2 z2");
                    SafeZoneRegion zone=new SafeZoneRegion(authenticatedOwner,dim,name(tokens[2]),
                        number(tokens[3]),number(tokens[4]),number(tokens[5]),number(tokens[6]),number(tokens[7]),number(tokens[8]));
                    store.add(zone);return "Saved protected region "+zone.describe();
                }
                case "remove", "delete" -> {
                    if(tokens.length!=3)throw new IllegalArgumentException("Use: safezone remove <name>");
                    return store.remove(authenticatedOwner,dim,name(tokens[2]))?"Safe zone deleted in "+dim+": "+tokens[2]:"No matching zone owned by you in "+dim;
                }
                case "list" -> {
                    if(tokens.length!=2)throw new IllegalArgumentException("Use: safezone list");
                    var owned=store.owned(authenticatedOwner);
                    return owned.isEmpty()?"No saved safe zones.":"Saved safe zones: "+String.join("; ",owned.stream().map(SafeZoneRegion::describe).toList());
                }
                case "check" -> {
                    if(tokens.length!=2)throw new IllegalArgumentException("Use: safezone check");
                    BlockPos p=owner.blockPosition();
                    return SafeZoneService.protectedAt(world,p)?"Protected here: Ellie cannot edit blocks.":"Not protected here.";
                }
                default -> {return usage();}
            }
        }
    }
    private static String name(String value){return value.toLowerCase(Locale.ROOT);}
    private static int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid coordinate or radius: "+value);}}
    private static String usage(){return "safezone pos1 | pos2 | save <name> | here <name> <radius> <height> | add <name> x1 y1 z1 x2 y2 z2 | list | check | remove <name>";}
}
