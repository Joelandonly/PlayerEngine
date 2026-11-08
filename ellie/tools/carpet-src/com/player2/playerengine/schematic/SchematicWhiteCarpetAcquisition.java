package com.player2.playerengine.schematic;

import com.player2.playerengine.agentic.ResourceAgenticIntegration;
import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.nativecommands.NativeAccess;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Native-only material route for white carpet. The generic dynamic recipe
 * expands 48 carpets -> 32 white wool -> 128 string -> spider farming.
 * Under the schematic owner, prefer shears -> white sheep -> direct carpet.
 *
 * This Task is a child of SchematicProjectTask, NOT another supervisor.
 * Every stage uses a real native task and the output is verified from the
 * actual inventory. No items or task completions are fabricated.
 */
public final class SchematicWhiteCarpetAcquisition extends Task {
    private static final String PREFIX="com.player2.playerengine.";
    public enum Stage { DONE, ACQUIRE_SHEARS, COLLECT_WHITE_WOOL, CRAFT_CARPETS }

    private final SchematicWorld environment;
    private final int target;
    private Task child;
    private String stage="";
    private boolean finished;
    private String error="";
    private int startCarpets,startWool,startShears;

    public SchematicWhiteCarpetAcquisition(SchematicWorld environment,int target){
        this.environment=java.util.Objects.requireNonNull(environment);
        if(target<1||target>64)throw new IllegalArgumentException("Invalid white carpet target "+target);
        this.target=target;
    }

    /** Two wool yield three carpets. Only missing carpets affect the next bill. */
    public static int woolNeeded(int alreadyCarpets,int carpetTarget) {
        if(carpetTarget<1||carpetTarget>64||alreadyCarpets<0)
            throw new IllegalArgumentException("Invalid carpet target/count");
        int missing=Math.max(0,carpetTarget-alreadyCarpets);
        return 2*((missing+2)/3);
    }

    /** Deterministic material preference, even when string appears nearby. */
    public static Stage choose(int carpets,int wool,int shears,int target) {
        if(carpets>=target)return Stage.DONE;
        if(wool>=woolNeeded(carpets,target))return Stage.CRAFT_CARPETS;
        return shears>0?Stage.COLLECT_WHITE_WOOL:Stage.ACQUIRE_SHEARS;
    }

    @Override protected void onStart(){}

    @Override protected Task onTick(){
        try{
            environment.checkWorld();
            Map<String,Integer> held=environment.inventory();
            int carpets=held.getOrDefault("minecraft:white_carpet",0);
            int wool=held.getOrDefault("minecraft:white_wool",0);
            int shears=held.getOrDefault("minecraft:shears",0);
            Stage wanted=choose(carpets,wool,shears,target);
            if(wanted==Stage.DONE){
                finished=true;
                environment.report("SCHEMATIC_CARPET_VERIFIED",
                    "white_carpet="+carpets+" target="+target+" actual_inventory=true");
                return null;
            }

            if(child!=null){
                if(!child.stopped()&&!child.isFinished())return child;
                if(child.stopped()&&!child.isFinished()){
                    return blocked("Native "+stage+" task stopped without verified material; "+child);
                }
                if(carpetProgress(stage,carpets,wool,shears)<=0){
                    return blocked("Native "+stage+" task ended without increasing the required actual item count");
                }
                child=null;
            }

            stage=wanted.name();
            startCarpets=carpets;startWool=wool;startShears=shears;
            environment.report("SCHEMATIC_CARPET_ROUTE","stage="+stage
                +" carpet="+carpets+"/"+target+" white_wool="+wool
                +" required_wool="+woolNeeded(carpets,target)
                +" shears="+shears+" no_string_hunting=true");

            switch(wanted){
                case ACQUIRE_SHEARS -> child=nativeItem("shears",1);
                case COLLECT_WHITE_WOOL -> child=nativeItem("white_wool",woolNeeded(carpets,target));
                case CRAFT_CARPETS -> child=carpetRecipe(target);
                case DONE -> throw new IllegalStateException("Unexpected completed stage");
            }
            child.controller=controller;
            return child;
        }catch(Exception | LinkageError exception){
            return blocked("White carpet acquisition: "+exception.getClass().getSimpleName()
                +": "+String.valueOf(exception.getMessage()));
        }
    }

    private int carpetProgress(String old,int carpets,int wool,int shears){
        return switch(old){
            case "ACQUIRE_SHEARS" -> shears-startShears;
            case "COLLECT_WHITE_WOOL" -> wool-startWool;
            case "CRAFT_CARPETS" -> carpets-startCarpets;
            default -> -1;
        };
    }

    private Task nativeItem(String id,int amount) {
        return ResourceAgenticIntegration.nativeAcquisition(id,amount).orElseThrow(
            ()->new IllegalStateException("Native catalogue does not support "+id+" x"+amount));
    }

    /** Explicit 2x2 vanilla recipe, never resolve white wool into 128 string. */
    private Task carpetRecipe(int amount)throws Exception{
        Method item=SchematicWorld.class.getDeclaredMethod("item",String.class);
        item.setAccessible(true);
        Object wool=item.invoke(environment,"minecraft:white_wool");
        Object carpet=item.invoke(environment,"minecraft:white_carpet");
        String type=PREFIX+"util.ItemTarget";
        Object required=FoodAcquisition.construct(type,wool,1);
        Object slots=Array.newInstance(Class.forName(type),4);
        Array.set(slots,0,required);
        Array.set(slots,1,required);
        Object recipe=NativeAccess.callStatic(PREFIX+"util.CraftingRecipe",
            "newShapedRecipe",slots,3);
        if(recipe==null)throw new IllegalStateException("White carpet recipe unavailable");
        Object targetRecipe=FoodAcquisition.construct(PREFIX+"util.RecipeTarget",carpet,amount,recipe);
        // Never invoke recursive resource collection inside this crafting stage.
        // If a wool shortage appears, return to the separate wool stage.
        return (Task)FoodAcquisition.construct(PREFIX+"tasks.CraftInInventoryTask",
            targetRecipe,false,false);
    }

    private Task blocked(String reason){
        error=reason;
        environment.report("SCHEMATIC_CARPET_BLOCKED",reason);
        fail(reason);
        return null;
    }

    @Override public boolean isFinished(){return finished;}
    @Override protected void onStop(Task next){}
    @Override protected boolean isEqual(Task other){
        return other instanceof SchematicWhiteCarpetAcquisition a
            && a.environment==environment && a.target==target;
    }
    @Override protected String toDebugString(){
        return "White carpet wool-first "+stage+" target="+target
            +(error.isEmpty()?"":" error="+error);
    }
}
