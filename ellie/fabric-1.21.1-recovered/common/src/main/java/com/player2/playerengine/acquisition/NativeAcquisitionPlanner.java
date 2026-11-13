package com.player2.playerengine.acquisition;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.blockknowledge.BlockKnowledge;
import com.player2.playerengine.tasks.agentic.MineBlockParams;
import com.player2.playerengine.tasks.agentic.MineBlockTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.CraftMacroTasks;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.warehouse.WarehouseWithdrawal;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;

/** One authoritative native strategy selector. Every caller retains task ownership
 * and verifies its own output. No LLM, world edits, or speculative success receipts.
 */
public final class NativeAcquisitionPlanner {
    private NativeAcquisitionPlanner() {}
    public enum Route { ALREADY_HELD, WAREHOUSE_FIRST, CRAFT_RECIPE, NATIVE_CATALOGUE, VERIFIED_BLOCK_DROP, UNSUPPORTED }
    public record Decision(Route route, Task task, String reason) {
        public Optional<Task> optionalTask() {return Optional.ofNullable(task);}
        public boolean failClosed() {return route == Route.UNSUPPORTED;}
    }
    public static Decision choose(PlayerEngineController mod, String raw, int absoluteTarget, MaterialReservationService reservations) {
        if(mod==null||raw==null||absoluteTarget<1||absoluteTarget>4096)
            return new Decision(Route.UNSUPPORTED,null,"Invalid resource request");
        String id=raw.trim().toLowerCase(Locale.ROOT);
        if(!id.contains(":")) id="minecraft:"+id;
        ResourceLocation key=ResourceLocation.tryParse(id);
        Item item=key==null?null:BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        // Catalogue aliases such as "log" represent multiple alternative items. A
        // single-item recipe is not appropriate; keep the existing catalogue policy.
        if(item==null) {
            String alias=id.startsWith("minecraft:")?id.substring(10):id;
            Task existing=TaskCatalogue.taskExists(alias)?TaskCatalogue.getItemTask(alias,absoluteTarget):null;
            return existing==null?new Decision(Route.UNSUPPORTED,null,"No native route for "+raw)
                    :new Decision(Route.NATIVE_CATALOGUE,existing,"Native group/alias resource task");
        }
        int held=mod.getItemStorage().getItemCountInventoryOnly(item);
        if(held>=absoluteTarget) return new Decision(Route.ALREADY_HELD,null,"Already carried: "+held);
        ItemTarget target=new ItemTarget(item,absoluteTarget);
        String exactId=id;
        if(WarehouseWithdrawal.hasStock(mod,target)) {
            return new Decision(Route.WAREHOUSE_FIRST,
                new WarehouseFirstAcquisition(target,()->chooseNative(mod,exactId,target,reservations)),
                "Authorized warehouse stock before crafting or gathering the remainder");
        }
        return chooseNative(mod,id,target,reservations);
    }

    private static Decision chooseNative(PlayerEngineController mod,String id,ItemTarget target,MaterialReservationService reservations) {
        Item item=target.getMatches()[0];
        int absoluteTarget=target.getTargetCount();
        if(mod.getItemStorage().getItemCountInventoryOnly(item)>=absoluteTarget)
            return new Decision(Route.ALREADY_HELD,null,"Inventory target already met");
        BlockKnowledge.Entry known=BlockKnowledge.byItem(id);
        boolean recipeBacked=ProjectMaterialPlanner.recipeBacked(id);
        // Authoritative live Minecraft recipe resolver comes before TaskCatalogue.
        // This is what prevents "get ladder" from scavenging built ladders.
        try {
            CraftMacroTasks.MacroProbe craft=CraftMacroTasks.macroOutcome(mod,target,reservations);
            if(craft.outcome()==CraftMacroTasks.MacroOutcome.MACRO_CREATED && craft.task()!=null)
                return new Decision(Route.CRAFT_RECIPE,craft.task(),"Live recursive crafting recipe");
        } catch(Exception | LinkageError e) {
            if(recipeBacked) return new Decision(Route.UNSUPPORTED,null,"Crafting resolver failed for "+id+": "+e.getClass().getSimpleName());
        }
        // A recipe-backed output may use a native smelter/stonecutter/catalogue
        // task if available, but must never activate the optional mineIfPresent path.
        // TaskCatalogue.CataloguedResource.getResource enforces that globally.
        Task existing=null;
        String alias=id.startsWith("minecraft:")?id.substring(10):id;
        if(TaskCatalogue.taskExists(alias)) existing=TaskCatalogue.getItemTask(alias,absoluteTarget);
        else if(TaskCatalogue.taskExists(item)) existing=TaskCatalogue.getItemTask(item,absoluteTarget);
        if(existing!=null) return new Decision(Route.NATIVE_CATALOGUE,existing,
            recipeBacked?"Native crafting/smelting catalogue, scavenging disabled":"Native resource catalogue");
        if(recipeBacked) return new Decision(Route.UNSUPPORTED,null,
            "Recipe exists for "+id+" but no executable native crafting route; refusing to mine placed blocks");
        // Only direct, deterministic loot drops. No Silk Touch assumptions, chests,
        // block entities, random drops or structure looting through this fallback.
        if(known!=null && known.obtainable() && known.itemForm()!=null && absoluteTarget<=256) {
            for(BlockKnowledge.Route r:known.routes()) {
                if(!r.method().equals("mine") || !safeDirectDrop(r.requirement())) continue;
                ResourceLocation sourceKey=ResourceLocation.tryParse(r.source());
                Block block=sourceKey==null?null:BuiltInRegistries.BLOCK.getOptional(sourceKey).orElse(null);
                if(block==null || block instanceof EntityBlock) continue;
                if(!known.itemForm().equals(id)) continue;
                return new Decision(Route.VERIFIED_BLOCK_DROP,
                   new MineBlockTask(new MineBlockParams(r.source(),absoluteTarget,32.0,120.0,8,6),null,null),
                   "Deterministic block drop; existing protected-block guards apply");
            }
        }
        return new Decision(Route.UNSUPPORTED,null,"No safe executable acquisition for "+id);
    }
    public static Optional<Task> task(PlayerEngineController mod,String name,int absoluteTarget,
                                       MaterialReservationService ledger) {
        return choose(mod,name,absoluteTarget,ledger).optionalTask();
    }
    private static boolean safeDirectDrop(String conditions) {
        String c=conditions.toLowerCase(Locale.ROOT);
        return !c.contains("silk_touch")&&!c.contains("chance:")&&!c.contains("fortune")
            &&!c.contains("tool:")&&!c.contains("killed_by_player")&&!c.contains("not(")
            &&!c.contains("state:")&&!c.contains("bonus:");
    }
}
