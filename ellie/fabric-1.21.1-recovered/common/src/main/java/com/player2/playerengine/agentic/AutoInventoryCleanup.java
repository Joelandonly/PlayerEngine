package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.warehouse.WarehousePolicy;
import com.player2.playerengine.warehouse.WarehouseManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.EntityBlock;

/** Optional, non-destructive pressure relief *inside* a live resource task.
 * Uses only an explicitly registered chest. Never drops items or changes a parent goal.
 */
public final class AutoInventoryCleanup {
    private AutoInventoryCleanup() {}
    private static final Map<Task, Session> SESSIONS = new WeakHashMap<>();
    /** Common loose drops supplement the block and equipment checks. */
    private static final Set<String> SURPLUS = Set.of(
        "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:granite",
        "minecraft:diorite", "minecraft:andesite", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
        "minecraft:deepslate", "minecraft:tuff", "minecraft:stone", "minecraft:netherrack",
        "minecraft:basalt", "minecraft:blackstone", "minecraft:end_stone", "minecraft:sand",
        "minecraft:red_sand", "minecraft:rotten_flesh", "minecraft:spider_eye",
        "minecraft:bone", "minecraft:arrow"
    );
    private static final class Session {
        final String dimension;
        BoundedContainerTransferTask transfer;
        BlockPos destination;
        int freeBefore, countBefore;
        int targetFree, deferredAtFree=-1;
        Item depositing;
        long nextAttempt;
        String lastPressureReason = "";
        final Set<BlockPos> excluded = new java.util.HashSet<>();
        final Task waiting = new PressurePause();
        boolean recovering,legacy;
        Session(String dimension){this.dimension=dimension;}
    }
    private static final class PressurePause extends Task {
        protected void onStart() {}
        protected Task onTick() { setDebugState("Inventory recovery paused; see INVENTORY_RECOVERY_BLOCKED"); return null; }
        protected void onStop(Task next) {}
        protected boolean isEqual(Task other) { return other instanceof PressurePause; }
        protected String toDebugString() { return "Inventory pressure safety pause (goal retained)"; }
    }
    public static Task continuePending(Task root) {
        Session session = SESSIONS.get(root);
        return session == null || session.transfer == null ? null : choose(root, null);
    }
    public static void cancel(Task root) {
        Session session=SESSIONS.remove(root);
        if(session!=null && session.transfer!=null)session.transfer.stop();
    }
    /** Runs BEFORE ResourceTask.onResourceTick, not after a producer has already acted. */
    public static Task beforeResourceTick(Task root) { return choose(root,null); }
    public static boolean transferringWithin(Task root) {
        return root.containsTask(task -> {
            Session session=SESSIONS.get(task);
            return session!=null&&session.transfer!=null&&!session.transfer.isFinished();
        });
    }
    public static Task choose(Task root, Task normalAction) {
        PlayerEngineController mod=root.controller;
        if(mod==null || mod.getWorld()==null || mod.getEntity()==null)return normalAction;
        String dimension=WarehouseManager.dimension(mod);
        Session session=SESSIONS.get(root);
        if(session==null||!session.dimension.equals(dimension)){
            cancel(root);
            session=new Session(dimension);
            SESSIONS.put(root,session);
        }
        long now=mod.getWorld().getGameTime();
        InventorySpace.Snapshot snapshot=InventorySpace.inspect(mod);
        int reserve=4;
        try { reserve=com.player2.playerengine.warehouse.WarehouseManager.ledger(mod).number("reserve",4); }
        catch(Exception ignored) {}
        if(session.transfer!=null){
            boolean allowed;
            try {
                if(session.legacy)allowed=InventoryCleanupStorage.destination(mod).filter(session.destination::equals).isPresent();
                else {
                    var saved=com.player2.playerengine.warehouse.WarehouseManager.ledger(mod);
                    allowed=com.player2.playerengine.warehouse.WarehouseManager.authorized(mod,saved,session.destination);
                }
            }catch(Exception unavailable){allowed=false;}
            if(!allowed || session.transfer.stopped()&&!session.transfer.isFinished()) {
                session.transfer.stop(); session.transfer=null;session.excluded.add(session.destination);
                return blocked(session, "Storage authorization/access changed or transfer interrupted; original goal retained");
            }
            if(!session.transfer.isFinished())return session.transfer;
            int countAfter=mod.getItemStorage().getItemCountInventoryOnly(session.depositing);
            boolean progress=countAfter<session.countBefore;
            ObservatoryTelemetry.event("INVENTORY_RECOVERY_TRANSFER", "dimension="+com.player2.playerengine.warehouse.WarehouseManager.dimension(mod)
                +" destination="+session.destination+" item="+BuiltInRegistries.ITEM.getKey(session.depositing)
                +" outcome="+session.transfer.outcome()+" countBefore="+session.countBefore+" countAfter="+countAfter
                +" freeBefore="+session.freeBefore+" freeAfter="+snapshot.free());
            try { com.player2.playerengine.warehouse.WarehouseManager.remember(mod,session.destination); }catch(Exception ignored) {}
            if(!progress)session.excluded.add(session.destination);
            session.transfer=null;
        }
        if(!snapshot.known())return blocked(session,"Cannot verify actual main-inventory capacity; producer paused");
        if(!session.recovering&&transferringWithin(root))return normalAction;
        reserve=Math.max(1,Math.min(reserve,snapshot.total()));
        int needed=session.recovering?session.targetFree
            :snapshot.free()>=reserve?reserve:Math.min(reserve,remainingOutputSlots(root,snapshot.total()));
        if(!WarehousePolicy.needsRecovery(snapshot.free(),needed) || root.isFinished()) {
            if(session.recovering)ObservatoryTelemetry.event("INVENTORY_RECOVERY_COMPLETED","task="+root.getClass().getSimpleName()+" free="+snapshot.free()+" original goal resuming");
            session.recovering=false;session.deferredAtFree=-1;session.excluded.clear();session.lastPressureReason="";return normalAction;
        }
        if(session.nextAttempt>now)return snapshot.free()==0?session.waiting:normalAction;
        if(!session.recovering){
            if(snapshot.free()>0&&session.deferredAtFree>=0&&snapshot.free()>=session.deferredAtFree)return normalAction;
            session.recovering=true;
            session.targetFree=Math.min(snapshot.total(),needed+reserve);
            ObservatoryTelemetry.event("INVENTORY_RECOVERY_STARTED","task="+root.getClass().getSimpleName()
                +" "+snapshot.summary()+" taskSlots="+needed+" targetFree="+session.targetFree);
        }
        List<Candidate> candidates=selectSurplus(root);
        if(candidates.isEmpty())return defer(session,now,snapshot,normalAction,"No unreserved surplus; equipment, active requirements and project supplies retained; "+snapshot.summary());
        BlockPos target=null;int amount=0;Candidate candidate=null;
        boolean warehouseExists=true;
        String reason="No verified storage destination in "+dimension+"; use ellie_storage init here or check local warehouse capacity";
        for(Candidate possible:candidates){
        candidate=possible;amount=possible.amount;
        try {
            var saved=com.player2.playerengine.warehouse.WarehouseManager.ledger(mod);warehouseExists=saved.exists();
            if(warehouseExists){
                String issue=com.player2.playerengine.warehouse.WarehouseManager.unavailable(mod,saved);
                if(issue!=null)reason=issue;
                else {
                    var choice=com.player2.playerengine.warehouse.WarehouseManager.allocate(mod,candidate.stack,session.excluded);
                    if(choice!=null){target=choice.pos();amount=Math.min(amount,choice.capacity());}
                    else reason=com.player2.playerengine.warehouse.WarehouseManager.allocationProblem(mod,candidate.stack,session.excluded);
                }
            }
        } catch(Exception failure){reason="Warehouse metadata unavailable: "+failure.getMessage();}
        if(!warehouseExists){
            var legacy=InventoryCleanupStorage.destination(mod);
            if(legacy.isPresent()&&!session.excluded.contains(legacy.get())) {
                var resolved=com.player2.playerengine.containeraccess.ContainerResolver.resolve(mod.getWorld(),legacy.get());
                if(resolved.ok()){
                    int capacity=com.player2.playerengine.warehouse.WarehouseManager.capacity(resolved.resolved().container(),candidate.stack);
                    if(capacity>0){target=legacy.get();amount=Math.min(amount,capacity);}
                }
            }
        }
        if(target!=null)break;
        }
        if(target==null || mod.getEntity().distanceToSqr(target.getX()+.5,target.getY()+.5,target.getZ()+.5)>115*115){
            session.excluded.clear();
            return defer(session,now,snapshot,normalAction,reason);
        }
        session.legacy=!warehouseExists;session.lastPressureReason="";session.freeBefore=snapshot.free();session.destination=target;
        session.depositing=candidate.stack.getItem();session.countBefore=mod.getItemStorage().getItemCountInventoryOnly(session.depositing);
        session.transfer=BoundedContainerTransferTask.depositMatching(target,candidate.stack,amount);
        return session.transfer;
    }
    private static Task defer(Session session,long now,InventorySpace.Snapshot snapshot,Task normalAction,String reason){
        session.nextAttempt=now+100;
        if(snapshot.free()==0)return blocked(session,reason+"; producer paused, original goal retained");
        session.recovering=false;
        session.deferredAtFree=snapshot.free();
        session.excluded.clear();
        if(!reason.equals(session.lastPressureReason)){
            session.lastPressureReason=reason;
            ObservatoryTelemetry.event("INVENTORY_RECOVERY_DEFERRED",reason+"; continuing with "+snapshot.free()+" free slots");
        }
        return normalAction;
    }
    private static Task blocked(Session session,String reason){
        if(!reason.equals(session.lastPressureReason)){
            session.lastPressureReason=reason;
            ObservatoryTelemetry.event("INVENTORY_RECOVERY_BLOCKED",reason);
            if(reason.matches(".*full=[1-9][0-9]*.*"))ObservatoryTelemetry.event("WAREHOUSE_CAPACITY_FULL",reason);
        }
        return session.waiting;
    }

    private record Candidate(ItemStack stack, String id, int amount, boolean supersededTool) {}

    /** Parent and child can describe the same output, so reserve for the largest live demand. */
    private static int remainingOutputSlots(Task root,int limit){
        var inventory=root.controller.getInventory().main;
        int[] largest={-1};
        projectRoot(root).containsTask(task->{
            if(task instanceof ResourceTask resource){
                int slots=0;
                boolean hasTarget=false;
                for(ItemTarget target:resource.getItemTargets()){
                    if(target==null||target.isEmpty())continue;
                    hasTarget=true;
                    slots=Math.min(limit,slots+additionalSlots(target,inventory,limit));
                }
                if(hasTarget)largest[0]=Math.max(largest[0],slots);
            }
            return false;
        });
        // Producers without a declared output still need somewhere to put the next stack.
        return largest[0]<0?1:largest[0];
    }

    private static int additionalSlots(ItemTarget target,List<ItemStack> inventory,int limit){
        Item[] matches=target.getMatches();
        int stackSize=java.util.Arrays.stream(matches).filter(java.util.Objects::nonNull)
            .mapToInt(item->item.getDefaultInstance().getMaxStackSize()).min().orElse(1);
        long remaining=target.getTargetCount();
        for(ItemStack stack:inventory){
            if(stack.isEmpty()||!target.matches(stack.getItem()))continue;
            remaining-=stack.getCount();
            if(matches.length==1&&ItemStack.isSameItemSameComponents(stack,stack.getItem().getDefaultInstance())){
                remaining-=Math.max(0,stack.getMaxStackSize()-stack.getCount());
            }
        }
        return remaining<=0?0:(int)Math.min(limit,(remaining+stackSize-1)/stackSize);
    }

    private static Task projectRoot(Task root){
        var chain=root.controller.getUserTaskChain();
        Task active=chain==null?null:chain.getCurrentTask();
        return active!=null&&active.containsTask(task->task==root)?active:root;
    }

    private static List<Candidate> selectSurplus(Task root) {
        PlayerEngineController mod = root.controller;
        Map<Item, Integer> amounts = new HashMap<>();
        var candidates=new java.util.ArrayList<Candidate>();
        var inventory=mod.getInventory();
        Task policyRoot=projectRoot(root);
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (stack.has(DataComponents.FOOD) || SchematicProjectTask.isProjectMaterial(mod, id)
                || mod.getBehaviour().isProtected(item) || mod.getModSettings().isImportant(item)
                || MaterialReservationService.isReserved(mod,item)) continue;
            if (root instanceof SchematicProjectTask project && project.reservesMaterial(id)) continue;
            if(policyRoot.containsTask(t -> t instanceof ResourceTask resource
                && java.util.Arrays.stream(resource.getItemTargets()).anyMatch(target -> target.matches(item))
                || t instanceof CraftMacroResourceTask craft&&craft.keepsIngredient(item)))continue;
            if(item instanceof TieredItem tool){
                if(i!=inventory.selectedSlot&&superseded(stack,tool,inventory.main))candidates.add(new Candidate(stack.copyWithCount(1),id,1,true));
            }else if(ItemStack.isSameItemSameComponents(stack,item.getDefaultInstance())
                && (SURPLUS.contains(id)||item==Items.STICK||item instanceof BlockItem block&&!(block.getBlock() instanceof EntityBlock))){
                amounts.merge(item,stack.getCount(),Integer::sum);
            }
        }
        for(var entry:amounts.entrySet()){
            Item item=entry.getKey();ItemStack exemplar=item.getDefaultInstance();
            int excess=entry.getValue()-(item==Items.STICK?exemplar.getMaxStackSize():0);
            if(excess>0)candidates.add(new Candidate(exemplar,BuiltInRegistries.ITEM.getKey(item).toString(),Math.min(excess,StorageItemArgs.MAX_COUNT),false));
        }
        candidates.sort(java.util.Comparator.comparing(Candidate::supersededTool).reversed()
            .thenComparing(java.util.Comparator.comparingInt(Candidate::amount).reversed()).thenComparing(Candidate::id));
        return List.copyOf(candidates);
    }

    private static boolean superseded(ItemStack stack,TieredItem tool,List<ItemStack> inventory){
        ItemStack plain=stack.copyWithCount(1);
        plain.setDamageValue(0);
        if(!ItemStack.isSameItemSameComponents(plain,tool.getDefaultInstance()))return false;
        int remaining=stack.getMaxDamage()-stack.getDamageValue();
        return inventory.stream().anyMatch(other -> !other.isEmpty()&&other.getItem() instanceof TieredItem better
            && better.getClass()==tool.getClass()&&better.getTier().getUses()>tool.getTier().getUses()
            && other.getMaxDamage()-other.getDamageValue()>=remaining);
    }
}
