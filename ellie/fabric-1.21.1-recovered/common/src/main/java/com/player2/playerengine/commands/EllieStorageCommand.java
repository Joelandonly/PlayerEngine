package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import com.player2.playerengine.supervisor.*;
import com.player2.playerengine.warehouse.*;
import net.minecraft.core.BlockPos;

/** Exact owner native command; construction requires an idle task scheduler. */
public final class EllieStorageCommand extends Command {
    private static final String USAGE="ellie_storage init here | expand [additional double chests, multiples of 4] | resume | status | list | organize | off | on | reserve <free slots> | register x y z | approve_floor";
    private static final ThreadLocal<PlayerEngineController> OWNER=new ThreadLocal<>();
    public static void asOwner(PlayerEngineController mod,Runnable dispatch){
        OWNER.set(mod);try{dispatch.run();}finally{OWNER.remove();}
    }
    public EllieStorageCommand() throws CommandException {
        super("ellie_storage",USAGE+". "
            +"One warehouse per dimension. New Nether warehouses use blackstone or nether bricks. Optional signs use place_sign.",
            new Arg<>(String.class,"operation","status",0,false));
    }
    @Override protected void call(PlayerEngineController mod,ArgParser parser) throws CommandException {
        try {
            String[] args=parser.getArgUnits();String operation=args.length==0?"status":args[0].toLowerCase(java.util.Locale.ROOT);
            if(!operation.equals("status")&&!operation.equals("list")&&OWNER.get()!=mod)
                throw new IllegalArgumentException("Warehouse changes require an authenticated exact owner command");
            boolean resuming=operation.equals("resume")&&args.length==1;
            WarehouseLedger saved=resuming?null:WarehouseManager.ledger(mod);
            if(operation.equals("status")||operation.equals("list")){finishWithInfo(WarehouseManager.status(mod,operation.equals("list")));return;}
            if(operation.equals("off")||operation.equals("on")){
                if(!saved.exists())throw new IllegalArgumentException("No warehouse initialized");
                saved.set("enabled",operation.equals("on"));saved.save();finishWithInfo("Automatic warehouse use "+operation);return;
            }
            if(operation.equals("reserve")&&args.length==2){
                int total=com.player2.playerengine.agentic.InventorySpace.inspect(mod).total();
                int n=Integer.parseInt(args[1]);
                if(total<=0)throw new IllegalArgumentException("Cannot read the main inventory capacity");
                if(n<1||n>total)throw new IllegalArgumentException("Reserve must be 1.."+total+" main-inventory slots");
                saved.set("reserve",n);saved.save();finishWithInfo("Inventory reserve target="+n);return;
            }
            var active=CoreGoalSupervisor.snapshot(mod);
            if(active!=null && (active.status()==GoalLifecycle.Status.RUNNING || active.status()==GoalLifecycle.Status.SUSPENDED))throw new IllegalArgumentException("Another task owns Ellie; finish or explicitly stop it before warehouse construction/registration");
            if(resuming)saved=WarehouseManager.ledgerForResume(mod);
            if(operation.equals("approve_floor")&&args.length==1){
                String issue=WarehouseManager.unavailable(mod,saved);if(issue!=null)throw new IllegalArgumentException(issue);
                java.util.List<BlockPos> approved=new java.util.ArrayList<>();
                for(var cell:WarehouseLayout.floor(saved.number("requested",4),WarehouseManager.bunker(saved))){
                    BlockPos pos=WarehouseManager.at(saved,cell);
                    if(WarehouseManager.isStructure(saved,mod.getWorld().getBlockState(pos))){
                        if(com.player2.playerengine.safezone.SafeZoneService.protectedAt(mod.getWorld(),pos)
                            ||com.player2.playerengine.agentic.InventoryCleanupStorage.isProjectStorage(mod,pos))throw new IllegalArgumentException("Protected floor at "+pos);
                        approved.add(pos);
                    }
                }
                for(BlockPos pos:approved)saved.set("placed."+WarehouseManager.key(pos),true);saved.save();
                finishWithInfo("Explicitly approved existing floor cells="+approved.size()+"; no blocks changed");return;
            }
            if(operation.equals("organize")&&args.length==1){
                String issue=WarehouseManager.unavailable(mod,saved);if(issue!=null)throw new IllegalArgumentException(issue);
                ProjectTaskGateway.run(mod,new WarehouseOrganizeTask(),()->finishWithInfo("Warehouse organization ended; see task outcome"));return;
            }
            if(operation.equals("init")&&args.length==2&&args[1].equalsIgnoreCase("here")){
                if(!saved.canChooseSite())throw new IllegalArgumentException("Warehouse work already started in "+WarehouseManager.dimension(mod)+"; use resume or expand 4 here");
                var draft=saved.draft();
                draft.set("origin",WarehouseManager.key(mod.getEntity().blockPosition()));draft.set("dimension",WarehouseManager.dimension(mod));
                draft.set("style",WarehouseManager.dimension(mod).equals("minecraft:the_nether")?"nether_bunker":"open");
                draft.set("enabled",true);draft.set("requested",4);draft.set("reserve",saved.number("reserve",4));draft.set("state","REQUESTED");draft.set("reason","");
                WarehouseBuildTask.validate(mod,draft);
                saved.commit(draft);
                WarehouseManager.event("WAREHOUSE_INITIALIZED",WarehouseManager.dimension(mod)+" "+saved.get("origin","")+" requested=4");
            }else if(operation.equals("expand")&&args.length<=2){
                String issue=WarehouseManager.unavailable(mod,saved);if(issue!=null)throw new IllegalArgumentException(issue);
                if(!saved.exists() || !saved.get("state","").equals("READY"))throw new IllegalArgumentException("Finish current warehouse construction first");
                int additional=args.length==1?WarehouseLayout.CHESTS_PER_MODULE:Integer.parseInt(args[1]);
                int count=WarehouseLayout.expandedCount(saved.number("requested",4),additional);
                var draft=saved.draft();draft.set("requested",count);draft.set("state","REQUESTED");
                WarehouseBuildTask.validate(mod,draft);
                saved.commit(draft);
                WarehouseManager.event("WAREHOUSE_EXPANSION_STARTED","requested="+count+" "+saved.get("dimension",""));
            }else if(operation.equals("register")&&args.length==4){
                String issue=WarehouseManager.unavailable(mod,saved);if(issue!=null)throw new IllegalArgumentException(issue);
                BlockPos pos=new BlockPos(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
                for(var pair:WarehouseLayout.chests(saved.number("requested",4))){
                    BlockPos first=WarehouseManager.at(saved,pair.first()),second=WarehouseManager.at(saved,pair.second());
                    if(first.equals(pos)||second.equals(pos)){
                        WarehouseManager.registerPair(mod,saved,first,second);saved=WarehouseManager.ledger(mod);
                        saved.set("placed."+WarehouseManager.key(first),true);saved.set("placed."+WarehouseManager.key(second),true);saved.save();
                        finishWithInfo("Explicit warehouse chest approval saved; contents retained");return;
                    }
                }
                throw new IllegalArgumentException("Chest must occupy a planned warehouse pair; legacy cleanup registrations are never imported");
            }else if(!resuming)throw new IllegalArgumentException("Usage: "+USAGE);
            WarehouseBuildTask.validate(mod,saved);
            ProjectTaskGateway.run(mod,new WarehouseBuildTask(),()->finishWithInfo("Warehouse task ended; use ellie_storage status for verified state"));
        }catch(Exception failure){finishWithError("Warehouse: "+failure.getMessage());}
    }
}
