package com.player2.playerengine.warehouse;

import com.player2.playerengine.agentic.AutoInventoryCleanup;
import com.player2.playerengine.agentic.InventorySpace;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.containeraccess.*;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Finite consolidation into earlier matching chests; never balances back and forth or touches player storage. */
public final class WarehouseOrganizeTask extends Task implements TrackedTaskOutcomeProvider {
    private BoundedContainerTransferTask transfer;
    private BlockPos source,destination;
    private Item item;
    private int before,moves;
    private boolean depositing,terminal,success;
    private String reason="Not started";
    private String dimension;
    @Override protected void onStart(){if(dimension==null)dimension=WarehouseManager.dimension(controller);}
    @Override protected Task onTick(){
        if(terminal)return null;
        if(!dimension.equals(WarehouseManager.dimension(controller))){
            finish(false,"Warehouse dimension changed; withdrawn items retained in Ellie inventory");return null;
        }
        try{
            var saved=WarehouseManager.ledger(controller);String issue=WarehouseManager.unavailable(controller,saved);
            if(issue!=null){finish(false,issue);return null;}
            if(transfer!=null){
                if(!WarehouseManager.authorized(controller,saved,source)||!WarehouseManager.authorized(controller,saved,destination)){
                    finish(false,"Container authorization/access changed; withdrawn items retained in Ellie inventory");return null;
                }
                if(!transfer.isFinished())return transfer;
                int held=controller.getItemStorage().getItemCountInventoryOnly(item);
                WarehouseManager.remember(controller,source);WarehouseManager.remember(controller,destination);
                if(!depositing){
                    int actual=held-before;
                    if(actual<=0){finish(false,"Withdrawal made no verified progress");return null;}
                    transfer=new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.DEPOSIT,destination,
                        List.of(new StorageItemArgs.ItemQuery(item,BuiltInRegistries.ITEM.getKey(item).toString(),actual)));
                    depositing=true;return transfer;
                }
                if(held!=before){finish(false,"Deposit incomplete; items retained in Ellie inventory");return null;}
                transfer=null;moves++;
            }
            if(moves>=64){finish(true,"Organization pass limited to 64 stack transfers; run again if useful");return null;}
            Task pressure=AutoInventoryCleanup.beforeResourceTick(this);if(pressure!=null)return pressure;
            List<BlockPos> chests=new ArrayList<>();
            for(var pair:WarehouseLayout.chests(saved.number("requested",4))){
                var r=ContainerResolver.resolve(controller.getWorld(),WarehouseManager.at(saved,pair.first()));
                if(r.ok()&&WarehouseManager.authorized(controller,saved,r.resolved().canonicalPos()))chests.add(r.resolved().canonicalPos());
            }
            for(int s=chests.size()-1;s>=1;s--){
                var contents=ContainerResolver.resolve(controller.getWorld(),chests.get(s)).resolved().container();
                for(int slot=0;slot<contents.getContainerSize();slot++){
                    ItemStack stack=contents.getItem(slot);if(stack.isEmpty()||!ItemStack.isSameItemSameComponents(stack,stack.getItem().getDefaultInstance()))continue;
                    Item candidate=stack.getItem();String id=BuiltInRegistries.ITEM.getKey(candidate).toString();
                    if(controller.getBehaviour().isProtected(candidate)||MaterialReservationService.isReserved(controller,candidate)
                        ||SchematicProjectTask.isProjectMaterial(controller,id)||controller.getItemStorage().getItemCountInventoryOnly(candidate)!=0)continue;
                    boolean variants=false;
                    for(int k=0;k<contents.getContainerSize();k++){var other=contents.getItem(k);if(other.is(candidate)&&!ItemStack.isSameItemSameComponents(other,stack)){variants=true;break;}}
                    if(variants)continue;
                    for(int d=0;d<s;d++){
                        var target=ContainerResolver.resolve(controller.getWorld(),chests.get(d)).resolved().container();
                        if(target.countItem(candidate)==0)continue;
                        int room=WarehouseManager.capacity(target,stack);if(room<=0)continue;
                        source=chests.get(s);destination=chests.get(d);item=candidate;before=0;depositing=false;
                        transfer=BoundedContainerTransferTask.withdrawUpTo(source,item,Math.min(64,Math.min(room,contents.countItem(item))));
                        return transfer;
                    }
                }
            }
            finish(true,"Organization verified; completed stack transfers="+moves);
        }catch(Exception failure){finish(false,failure.getMessage());}
        return null;
    }
    private void finish(boolean ok,String message){terminal=true;success=ok;reason=message;if(transfer!=null)transfer.stop();WarehouseManager.event("WAREHOUSE_ORGANIZE_RESULT",message);}
    @Override protected void onStop(Task next){if(!terminal)finish(false,"INTERRUPTED; all withdrawn items retained");}
    @Override public boolean isFinished(){return terminal;}
    @Override public boolean isTerminal(){return terminal;}
    @Override public boolean isSuccessful(){return terminal&&success;}
    @Override public String controlledReason(){return reason;}
    @Override protected boolean isEqual(Task other){return this==other;}
    @Override protected String toDebugString(){return "Warehouse bounded organization";}
}
