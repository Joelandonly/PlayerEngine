package com.player2.playerengine.warehouse;

import com.player2.playerengine.acquisition.NativeAcquisitionPlanner;
import com.player2.playerengine.agentic.AutoInventoryCleanup;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.automaton.api.schematic.AbstractSchematic;
import com.player2.playerengine.reliability.WorksiteAnchor;
import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.schematic.SchematicBuildSupport;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.construction.ConstructionTools;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.tasks.setup.SetupBuildZone;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Bounded terrain preparation and physical construction through the native task chain. */
public final class WarehouseBuildTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
    private enum CellWork { CLEAR, STRUCTURE, CHEST }
    private boolean terminal,success,policy,supplies;
    private String reason="Not started";
    private Task action;
    private long actionStart;
    private int lastHeld=-1;
    private long lastGain;
    private int lastCraftStage;
    private BlockPos target;
    private CellWork cellWork;
    private Item material;
    private ConstructionTools tool;
    private int amount;
    private SetupBuildZone siteProtection;
    private final MaterialReservationService reservations=new MaterialReservationService();
    private WarehouseLedger saved;
    private String dimension;
    public static void validate(com.player2.playerengine.PlayerEngineController mod,WarehouseLedger saved) throws IOException {
        WarehouseSite.inspect(mod,saved);
    }
    @Override protected void onStart(){
        if(terminal)return;
        try{
            String current=WarehouseManager.dimension(controller);
            if(dimension!=null&&!dimension.equals(current)){finish(false,"Warehouse dimension changed; resume at the original site");return;}
            dimension=current;
            saved=WarehouseManager.ledger(controller);validate(controller,saved);
            BlockPos origin=WarehouseManager.origin(saved);
            int depth=saved.number("requested",4)/4*7;
            boolean bunker=WarehouseManager.bunker(saved);
            if(siteProtection==null){
                siteProtection=bunker
                    ?new SetupBuildZone(controller,origin.getX()-5,origin.getZ()-1,14,depth+2)
                    :new SetupBuildZone(controller,origin.getX(),origin.getZ(),8,depth);
                WorksiteAnchor.open(this,controller,origin,"warehouse");
            }
            controller.getBehaviour().push();supplies=true;
            controller.getBehaviour().addProtectedItems(WarehouseManager.supplies(saved));
            controller.getBehaviour().avoidBlockBreaking(pos->insideSite(origin,depth,bunker,pos)&&!isClearTarget(pos));
            controller.getBehaviour().avoidBlockPlacing(pos->insideSite(origin,depth,bunker,pos)&&!pos.equals(target));
        }
        catch(Exception failure){finish(false,failure.getMessage());}
    }
    private static boolean insideSite(BlockPos origin,int depth,boolean bunker,BlockPos pos){
        int x=pos.getX()-origin.getX(),z=pos.getZ()-origin.getZ();
        if(bunker)return x>=-5&&x<=8&&z>=-1&&z<=depth;
        return (x>=0&&x<8&&z>=0&&z<depth)||(x==-1&&z==0);
    }
    private boolean isClearTarget(BlockPos pos){return cellWork==CellWork.CLEAR&&pos.equals(target);}
    private void enterPlacementPolicy(){
        if(policy)return;
        controller.getBehaviour().push();policy=true;
        controller.getBehaviour().avoidBlockBreaking(pos->!isClearTarget(pos));
        controller.getBehaviour().avoidBlockPlacing(pos->cellWork==CellWork.CLEAR||!pos.equals(target));
        controller.getBehaviour().addProtectedItems(WarehouseManager.supplies(saved));
    }
    private void leavePlacementPolicy(){if(policy){controller.getBehaviour().pop();policy=false;}}
    @Override protected Task onTick(){
        if(terminal)return null;
        try {
            if(!dimension.equals(WarehouseManager.dimension(controller))){finish(false,"Warehouse dimension changed; resume at the original site");return null;}
            saved=WarehouseManager.ledger(controller);
            String issue=WarehouseManager.unavailable(controller,saved);if(issue!=null){finish(false,issue);return null;}
            if(action!=null){
                if(target!=null){
                    var site=WarehouseSite.inspect(controller,saved);
                    BlockState state=controller.getWorld().getBlockState(target);
                    boolean placed=switch(cellWork){
                        case CLEAR -> state.isAir();
                        case STRUCTURE -> WarehouseManager.isStructure(saved,state);
                        case CHEST -> ((PlaceCell)action).chestPlaced(state);
                    };
                    if(placed){action.stop();action=null;target=null;leavePlacementPolicy();}
                    else if(cellWork==CellWork.CHEST&&state.is(Blocks.CHEST))throw new IOException("Chest placed with incompatible facing at "+target);
                    else if(cellWork==CellWork.CLEAR&&!site.clearance().contains(target))throw new IOException("Clearance target changed at "+target);
                    else if(controller.getWorld().getGameTime()-actionStart>400){finish(false,"WAREHOUSE_PATH_UNREACHABLE: placement exceeded 400 ticks at "+target);return null;}
                    else return action;
                }else{
                    int held=held();
                    if(material==Items.BLACKSTONE&&WarehouseManager.bunker(saved)
                        &&controller.getItemStorage().getItemCountInventoryOnly(Items.NETHER_BRICKS)>0)held=amount;
                    long now=controller.getWorld().getGameTime();
                    int stage=action instanceof CraftMacroResourceTask craft?craft.getPhase().ordinal():-1;
                    if(held>lastHeld||stage>lastCraftStage||AutoInventoryCleanup.transferringWithin(action))lastGain=now;
                    lastHeld=held;
                    lastCraftStage=Math.max(lastCraftStage,stage);
                    if(held<amount){
                        if(now-lastGain>1200 || now-actionStart>12000){finish(false,"Acquisition made no verified progress for "+material+"; resume after supplies/access are fixed");return null;}
                        return action;
                    }
                    action.stop();action=null;reservations.clear();
                }
            }
            var site=WarehouseSite.inspect(controller,saved);
            int count=saved.number("requested",4);
            boolean bunker=WarehouseManager.bunker(saved);
            var floor=WarehouseLayout.floor(count,bunker);
            var shell=WarehouseLayout.shell(count,bunker);
            Item stoneItem=WarehouseManager.structureItem(controller,saved);
            int missingStone=site.supports().size(),missingChests=0;
            for(var cells:List.of(floor,shell))for(var cell:cells)
                if(!WarehouseManager.isStructure(saved,controller.getWorld().getBlockState(WarehouseManager.at(saved,cell))))missingStone++;
            for(var pair:WarehouseLayout.chests(count))for(var cell:List.of(pair.first(),pair.second()))if(!controller.getWorld().getBlockState(WarehouseManager.at(saved,cell)).is(Blocks.CHEST))missingChests++;
            int stone=Math.min(missingStone,56),chests=Math.min(missingChests,8);
            boolean gatherStone=stone>0&&controller.getItemStorage().getItemCountInventoryOnly(stoneItem)==0;
            boolean gatherChests=chests>0&&controller.getItemStorage().getItemCountInventoryOnly(Items.CHEST)==0;
            var tools=site.tools();
            if(gatherChests||!bunker&&gatherStone)tools.add(ConstructionTools.AXE);
            if(gatherStone)tools.add(ConstructionTools.PICKAXE);
            for(var required:tools)if(!required.available(controller))return acquire(required.starter(),1,required);
            if(!site.clearance().isEmpty())return work(site.clearance().getFirst(),CellWork.CLEAR,Direction.NORTH);
            if(gatherStone)return acquire(stoneItem,stone,null);
            if(gatherChests)return acquire(Items.CHEST,chests,null);
            if(!site.supports().isEmpty())return work(site.supports().getFirst(),CellWork.STRUCTURE,Direction.NORTH);
            for(var cell:floor){
                BlockPos pos=WarehouseManager.at(saved,cell);
                if(controller.getWorld().getBlockState(pos).isAir())return work(pos,CellWork.STRUCTURE,Direction.NORTH);
            }
            for(var pair:WarehouseLayout.chests(count)){
                BlockPos first=WarehouseManager.at(saved,pair.first()),second=WarehouseManager.at(saved,pair.second());
                BlockState firstState=controller.getWorld().getBlockState(first),secondState=controller.getWorld().getBlockState(second);
                if(firstState.isAir()&&secondState.isAir())return work(first,CellWork.CHEST,pair.north()?Direction.NORTH:Direction.SOUTH);
                if(firstState.isAir())return work(first,CellWork.CHEST,existingFacing(second,secondState),second);
                if(secondState.isAir())return work(second,CellWork.CHEST,existingFacing(first,firstState),first);
                var resolution=com.player2.playerengine.containeraccess.ContainerResolver.resolve(controller.getWorld(),first);
                if(!resolution.ok() || resolution.resolved().secondaryPos()==null
                    || !Set.of(first,second).equals(Set.of(resolution.resolved().canonicalPos(),resolution.resolved().secondaryPos()))
                    || !WarehouseManager.verified(controller,resolution.resolved().canonicalPos()))throw new IOException("Double-chest join/access changed at "+first);
                if(!WarehouseManager.authorized(controller,saved,resolution.resolved().canonicalPos())){
                    WarehouseManager.registerPair(controller,saved,first,second);saved=WarehouseManager.ledger(controller);
                }
            }
            for(var cell:shell){
                BlockPos pos=WarehouseManager.at(saved,cell);
                if(controller.getWorld().getBlockState(pos).isAir())return work(pos,CellWork.STRUCTURE,Direction.NORTH);
            }
            saved.set("state","READY");saved.save();
            finish(true,(bunker?"Nether warehouse bunker ready; ":"Warehouse ready; ")+count+" double chests and connected aisles verified");
        }catch(Exception failure){finish(false,failure.getMessage());}
        return null;
    }
    private static boolean canPair(Direction direction){return direction==Direction.NORTH||direction==Direction.SOUTH;}
    private static Direction existingFacing(BlockPos pos,BlockState state) throws IOException {
        if(state.is(Blocks.CHEST)&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE)throw new IOException("Chest at "+pos+" is already joined to another chest");
        if(state.is(Blocks.CHEST)&&canPair(state.getValue(ChestBlock.FACING)))return state.getValue(ChestBlock.FACING);
        throw new IOException("Chest at "+pos+" must face north or south to join its warehouse partner");
    }
    private int held(){return tool==null?controller.getItemStorage().getItemCountInventoryOnly(material):tool.available(controller)?1:0;}
    private Task acquire(Item item,int count,ConstructionTools required){
        material=item;amount=count;tool=required;
        for(Item supply:WarehouseManager.supplies(saved)){
            if(supply!=item)reservations.reserve(controller,supply,controller.getItemStorage().getItemCountInventoryOnly(supply));
        }
        String id=BuiltInRegistries.ITEM.getKey(item).toString();
        var plan=NativeAcquisitionPlanner.choose(controller,id,count,reservations);
        action=plan.task();
        if(action==null)throw new IllegalStateException("Cannot acquire "+id+": "+plan.reason());
        WarehouseManager.event("WAREHOUSE_ACQUIRE",id+" x "+count+" via "+plan.route());
        return beginAction();
    }
    private Task beginAction(){if(action==null)throw new IllegalStateException("Resource/crafting catalogue has no task for required warehouse material");actionStart=lastGain=controller.getWorld().getGameTime();lastHeld=-1;lastCraftStage=-1;return action;}
    private Task work(BlockPos pos,CellWork kind,Direction direction) throws IOException {
        return work(pos,kind,direction,null);
    }
    private Task work(BlockPos pos,CellWork kind,Direction direction,BlockPos partner) throws IOException {
        target=pos;cellWork=kind;
        if(kind==CellWork.CLEAR)saved.set("terrainStarted",true);
        else saved.set("placed."+WarehouseManager.key(pos),true);
        saved.set("state","BUILDING");saved.save();
        Item block=kind==CellWork.STRUCTURE?WarehouseManager.structureItem(controller,saved):null;
        enterPlacementPolicy();action=new PlaceCell(pos,kind,direction,partner,block);return beginAction();
    }
    private void finish(boolean ok,String message){
        terminal=true;success=ok;reason=message;
        if(action!=null)action.stop();afterChildrenStopped();
        if(siteProtection!=null){siteProtection.close();siteProtection=null;}
        WorksiteAnchor.close(this);
        if(saved!=null&&saved.exists()&&saved.fault()==null)try{saved.set("state",ok?"READY":"BLOCKED");saved.set("reason",message);saved.save();}catch(IOException failure){
            success=false;
            reason=failure.getMessage();
        }
        WarehouseManager.event(success?"WAREHOUSE_BUILD_COMPLETED":"WAREHOUSE_BUILD_BLOCKED",String.valueOf(reason));
    }
    @Override public boolean prepareForTransientResume(TaskSuspensionCause cause){return !terminal;}
    @Override public void afterChildrenStopped(){
        action=null;
        reservations.clear();
        leavePlacementPolicy();
        if(supplies){controller.getBehaviour().pop();supplies=false;}
        target=null;
    }
    @Override public void onTransientResumeAbandoned(){onStop(null);}
    @Override protected void onStop(Task next){if(!terminal)finish(false,"INTERRUPTED; construction receipts retained; use ellie_storage resume");}
    @Override public boolean isFinished(){return terminal;}
    @Override public boolean isTerminal(){return terminal;}
    @Override public boolean isSuccessful(){return terminal&&success;}
    public String progressionProject(){
        return saved == null ? null : "warehouse/" + saved.get("dimension","") + "/"
            + WarehouseManager.origin(saved).asLong() + "/" + saved.number("requested",4);
    }
    @Override public String controlledReason(){return reason;}
    @Override protected boolean isEqual(Task other){return this==other;}
    @Override protected String toDebugString(){return "Warehouse bounded construction";}
    private final class PlaceCell extends Task {
        private final BlockPos target;private final CellWork kind;private final Direction facing;
        private final BlockPos partner;
        private final Item block;
        private AbstractSchematic owned;
        private boolean ownsBuilder(){
            if(owned==null)return false;
            try{
                Object builder=controller.getBaritone().getBuilderProcess();
                return com.player2.playerengine.nativecommands.NativeAccess.get(builder,"schematic")==owned
                    ||com.player2.playerengine.nativecommands.NativeAccess.get(builder,"realSchematic")==owned;
            }catch(Exception unavailable){return false;}
        }
        PlaceCell(BlockPos target,CellWork kind,Direction facing,BlockPos partner,Item block){this.target=target;this.kind=kind;this.facing=facing;this.partner=partner;this.block=block;}
        boolean chestPlaced(BlockState state){
            return state.is(Blocks.CHEST)&&(partner==null?canPair(state.getValue(ChestBlock.FACING)):state.getValue(ChestBlock.FACING)==facing);
        }
        protected void onStart(){}
        protected Task onTick(){
            if(SafeZoneService.deny(controller.getWorld(),target,"warehouse placement"))return null;
            var builder=controller.getBaritone().getBuilderProcess();
            if(!builder.isActive()){
                if(kind==CellWork.CHEST){
                    owned=new ChestCellSchematic();
                    SchematicBuildSupport.bindEntity(controller.getEntity(),builder);
                }else{
                    owned=new AbstractSchematic(1,1,1){
                        @Override public boolean placeFromBelow(){return kind==CellWork.STRUCTURE&&WarehouseManager.bunker(saved);}
                        public BlockState desiredState(int x,int y,int z,BlockState current,List<BlockState> available){
                            return kind==CellWork.CLEAR?Blocks.AIR.defaultBlockState():WarehouseManager.structureState(block);
                        }
                    };
                }
                builder.buildSingleCell("warehouse authorized cell",owned,target);
            }
            return null;
        }
        private final class ChestCellSchematic extends AbstractSchematic implements SchematicBuildSupport.OrientedChest {
            ChestCellSchematic(){super(1,1,1);}
            @Override public BlockPos partner(){return partner;}
            @Override public BlockState desiredState(int x,int y,int z,BlockState current,List<BlockState> available){
                if(current.is(Blocks.CHEST))return current;
                var desired=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing);
                if(partner==null)return desired;
                var left=desired.setValue(ChestBlock.TYPE,ChestType.LEFT);
                return target.relative(ChestBlock.getConnectedDirection(left)).equals(partner)
                    ?left:desired.setValue(ChestBlock.TYPE,ChestType.RIGHT);
            }
        }
        protected void onStop(Task next){if(ownsBuilder())controller.getBaritone().getBuilderProcess().onLostControl();}
        protected boolean isEqual(Task other){return this==other;}
        protected String toDebugString(){return "Warehouse cell "+target;}
    }
}
