package com.player2.playerengine.warehouse;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.InventoryCleanupStorage;
import com.player2.playerengine.containeraccess.*;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.tasks.deferred.DeferredJobStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Server-thread shared authorization and live allocation; never imports GPS or legacy registrations. */
public final class WarehouseManager {
    private WarehouseManager() {}
    private static final Map<Path,WarehouseLedger> LEDGERS=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<Path,WarehouseLedger> entry){return size()>64;}
    };
    public static String dimension(PlayerEngineController mod){return mod.getWorld().dimension().location().toString();}
    private static Path ledgerPath(PlayerEngineController mod) throws IOException {
        var store=DeferredJobStore.get();
        if(store==null || store.worldRoot()==null || mod.getAIPersistantData()==null)throw new IOException("Persistent world/companion identity unavailable");
        String identity=String.valueOf(mod.getAIPersistantData().getCharacterId());
        if(identity.isBlank()||identity.equals("null"))throw new IOException("Persistent companion identity unavailable");
        String name=UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
        String dimension=dimension(mod);
        String dimensionKey=UUID.nameUUIDFromBytes(dimension.getBytes(StandardCharsets.UTF_8)).toString();
        Path directory=store.worldRoot().resolve("ellie-warehouses");
        Path scoped=directory.resolve(name+"-"+dimensionKey+".properties");
        if(LEDGERS.containsKey(scoped)||Files.exists(scoped))return scoped;
        Path legacy=directory.resolve(name+".properties");
        if(LEDGERS.containsKey(legacy)||Files.exists(legacy)){
            var saved=read(legacy);
            if(dimension.equals(saved.get("dimension","")))return legacy;
        }
        return scoped;
    }
    private static WarehouseLedger read(Path file) throws IOException {
        WarehouseLedger ledger=LEDGERS.get(file);
        if(ledger==null){ledger=new WarehouseLedger(file);LEDGERS.put(file,ledger);}
        return ledger;
    }
    public static WarehouseLedger ledger(PlayerEngineController mod) throws IOException {
        WarehouseLedger ledger=read(ledgerPath(mod));
        if(ledger.fault()!=null)throw new IOException("Warehouse journal write fault; use ellie_storage resume after storage is writable: "+ledger.fault());
        return ledger;
    }
    /** Resume from committed receipts, discarding any mutations from the failed write. */
    public static WarehouseLedger ledgerForResume(PlayerEngineController mod) throws IOException {
        Path file=ledgerPath(mod);
        var cached=LEDGERS.get(file);
        if(cached==null||cached.fault()==null)return ledger(mod);
        var committed=new WarehouseLedger(file);
        if(!committed.exists())throw new IOException("No saved warehouse plan to resume after the write fault");
        LEDGERS.put(file,committed);
        event("WAREHOUSE_JOURNAL_RELOADED","Resumed from the last committed warehouse receipts");
        return committed;
    }
    public static BlockPos origin(WarehouseLedger saved){String[] p=saved.get("origin","").split(",");return new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    public static boolean bunker(WarehouseLedger saved){return "nether_bunker".equals(saved.get("style",""));}
    public static boolean isStructure(WarehouseLedger saved,BlockState state){
        return bunker(saved)?state.is(Blocks.BLACKSTONE)||state.is(Blocks.NETHER_BRICKS):state.is(Blocks.COBBLESTONE);
    }
    static BlockState structureState(Item item){
        return (item==Items.NETHER_BRICKS?Blocks.NETHER_BRICKS:item==Items.BLACKSTONE?Blocks.BLACKSTONE:Blocks.COBBLESTONE).defaultBlockState();
    }
    static Item[] supplies(WarehouseLedger saved){
        return bunker(saved)?new Item[]{Items.CHEST,Items.BLACKSTONE,Items.NETHER_BRICKS}:new Item[]{Items.CHEST,Items.COBBLESTONE};
    }
    static Item structureItem(PlayerEngineController mod,WarehouseLedger saved){
        if(!bunker(saved))return Items.COBBLESTONE;
        return mod.getItemStorage().getItemCountInventoryOnly(Items.BLACKSTONE)==0
            &&mod.getItemStorage().getItemCountInventoryOnly(Items.NETHER_BRICKS)>0?Items.NETHER_BRICKS:Items.BLACKSTONE;
    }
    public static BlockPos at(WarehouseLedger saved,WarehouseLayout.Cell cell){return origin(saved).offset(cell.x(),cell.y(),cell.z());}
    public static String key(BlockPos pos){return pos.getX()+","+pos.getY()+","+pos.getZ();}
    public static String unavailable(PlayerEngineController mod,WarehouseLedger saved){
        if(!saved.exists())return "No authorized warehouse in "+dimension(mod)+"; use ellie_storage init here";
        if(!"true".equals(saved.get("enabled","false")))return "Automatic warehouse use is disabled";
        if(!dimension(mod).equals(saved.get("dimension","")))return "Dimension mismatch: warehouse="+saved.get("dimension","")+" Ellie="+dimension(mod);
        return null;
    }
    public static boolean authorized(PlayerEngineController mod,WarehouseLedger saved,BlockPos pos){
        if(unavailable(mod,saved)!=null || !"true".equals(saved.get("container."+key(pos),"false")) || !verified(mod,pos))return false;
        var live=ContainerResolver.resolve(mod.getWorld(),pos);
        return live.ok()&&live.resolved().secondaryPos()!=null&&key(live.resolved().secondaryPos()).equals(saved.get("secondary."+key(pos),""));
    }
    public static boolean verified(PlayerEngineController mod,BlockPos pos){
        if(!mod.getWorld().hasChunkAt(pos)||InventoryCleanupStorage.isProjectStorage(mod,pos)||SafeZoneService.protectedAt(mod.getWorld(),pos))return false;
        var r=ContainerResolver.resolve(mod.getWorld(),pos);
        if(!r.ok()||!r.resolved().canonicalPos().equals(pos))return false;
        BlockPos second=r.resolved().secondaryPos();
        if(second==null || !mod.getWorld().getBlockState(pos).is(Blocks.CHEST)
            || InventoryCleanupStorage.isProjectStorage(mod,second)||SafeZoneService.protectedAt(mod.getWorld(),second))return false;
        for(BlockPos p:List.of(pos,second)) {
            if(!mod.getWorld().getBlockState(p.above()).isAir())return false;
            boolean access=false;
            for(Direction dir:Direction.Plane.HORIZONTAL){BlockPos feet=p.relative(dir);
                if(mod.getWorld().getBlockState(feet).isAir()&&mod.getWorld().getBlockState(feet.above()).isAir()
                    && mod.getWorld().getBlockState(feet.below()).isSolidRender(mod.getWorld(),feet.below())
                    && mod.getWorld().getFluidState(feet).isEmpty())access=true;
            }
            if(!access)return false;
        }
        return true;
    }
    public static int capacity(Container container,ItemStack probe){
        int capacity=0;
        for(int i=0;i<container.getContainerSize();i++){
            if(!container.canPlaceItem(i,probe))continue;
            ItemStack stack=container.getItem(i);
            capacity+=WarehousePolicy.slotCapacity(stack.isEmpty(),ItemStack.isSameItemSameComponents(stack,probe),
                stack.getCount(),probe.getMaxStackSize(),container.getMaxStackSize(),true);
        }
        return capacity;
    }
    public record Destination(BlockPos pos,int capacity) {}
    public static Destination allocate(PlayerEngineController mod,ItemStack probe,Set<BlockPos> excluded) throws IOException {
        WarehouseLedger saved=ledger(mod);String failure=unavailable(mod,saved);if(failure!=null)throw new IOException(failure);
        Destination best=null;boolean preferred=false;double nearest=Double.MAX_VALUE;
        for(var pair:WarehouseLayout.chests(saved.number("requested",4))){
            BlockPos planned=at(saved,pair.first());
            if(!mod.getWorld().hasChunkAt(planned))continue;
            var r=ContainerResolver.resolve(mod.getWorld(),planned);if(!r.ok())continue;
            BlockPos pos=r.resolved().canonicalPos();
            if(excluded.contains(pos)||!authorized(mod,saved,pos))continue;
            double distance=mod.getEntity().distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            if(distance>115*115)continue;
            int room=capacity(r.resolved().container(),probe);if(room<=0)continue;
            boolean matches=r.resolved().container().countItem(probe.getItem())>0;
            if(best==null || WarehousePolicy.prefer(matches,distance,preferred,nearest)){best=new Destination(pos,room);preferred=matches;nearest=distance;}
        }
        return best;
    }
    public static String allocationProblem(PlayerEngineController mod,ItemStack probe,Set<BlockPos> excluded) throws IOException {
        WarehouseLedger saved=ledger(mod);int full=0,unloaded=0,unverified=0,far=0,tried=0;
        for(var pair:WarehouseLayout.chests(saved.number("requested",4))){
            BlockPos planned=at(saved,pair.first());
            if(!mod.getWorld().hasChunkAt(planned)){unloaded++;continue;}
            var r=ContainerResolver.resolve(mod.getWorld(),planned);
            if(!r.ok()||!authorized(mod,saved,r.resolved().canonicalPos())){unverified++;continue;}
            BlockPos pos=r.resolved().canonicalPos();
            if(excluded.contains(pos)){tried++;continue;}
            if(mod.getEntity().distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>115*115){far++;continue;}
            if(capacity(r.resolved().container(),probe)==0)full++;
        }
        return "No warehouse destination for "+BuiltInRegistries.ITEM.getKey(probe.getItem())+": full="+full+" unloaded="+unloaded
            +" unverified/protected/obstructed="+unverified+" beyond115blocks="+far+" failedThisPass="+tried
            +(full>0?"; request expansion when capacity is full":"");
    }
    public static void remember(PlayerEngineController mod,BlockPos pos) throws IOException {
        WarehouseLedger saved=ledger(mod);if(!authorized(mod,saved,pos))throw new IOException("Warehouse chest no longer authorized/accessible");
        Container container=ContainerResolver.resolve(mod.getWorld(),pos).resolved().container();
        Map<String,Integer> stock=new TreeMap<>();int used=0;
        for(int i=0;i<container.getContainerSize();i++){ItemStack s=container.getItem(i);if(!s.isEmpty()){used++;stock.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),Integer::sum);}}
        Map<String,Integer> categories=new TreeMap<>();stock.forEach((id,count)->categories.merge(WarehousePolicy.category(id),count,Integer::sum));
        String category=categories.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Miscellaneous");
        saved.set("category."+key(pos),category);saved.set("snapshot."+key(pos),stock.toString());saved.set("used."+key(pos),used);saved.set("sample."+key(pos),mod.getWorld().getGameTime());saved.save();
    }
    public static void registerPair(PlayerEngineController mod,WarehouseLedger saved,BlockPos first,BlockPos second) throws IOException {
        var r=ContainerResolver.resolve(mod.getWorld(),first);
        if(!r.ok() || r.resolved().secondaryPos()==null || !Set.of(first,second).equals(Set.of(r.resolved().canonicalPos(),r.resolved().secondaryPos())))throw new IOException("Expected double-chest pair not joined correctly");
        BlockPos canonical=r.resolved().canonicalPos();
        if(!verified(mod,canonical))throw new IOException("Chest access/protection validation failed");
        saved.set("container."+key(canonical),true);saved.set("secondary."+key(canonical),key(r.resolved().secondaryPos()));saved.save();remember(mod,canonical);
        event("WAREHOUSE_CHEST_ADDED",dimension(mod)+" "+key(canonical)+" slots=54");
    }
    public static String status(PlayerEngineController mod,boolean list) throws IOException {
        WarehouseLedger saved=ledger(mod);if(!saved.exists())return "No warehouse initialized in "+dimension(mod);
        String issue=unavailable(mod,saved);int verified=0,used=0,unknown=0;
        StringBuilder out=new StringBuilder();
        if(issue==null)for(var pair:WarehouseLayout.chests(saved.number("requested",4))){
            BlockPos planned=at(saved,pair.first());
            var r=ContainerResolver.resolve(mod.getWorld(),planned);
            if(r.ok()&&authorized(mod,saved,r.resolved().canonicalPos())){
                BlockPos pos=r.resolved().canonicalPos();Container c=r.resolved().container();int occupied=0;
                for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty())occupied++;
                verified++;used+=occupied;
                if(list){remember(mod,pos);out.append("; ").append(key(pos)).append(" ").append(occupied).append("/54 category=").append(ledger(mod).get("category."+key(pos),"Miscellaneous")).append(" ").append(ledger(mod).get("snapshot."+key(pos),"unknown"));}
            }else unknown++;
        }
        return (bunker(saved)?"Warehouse bunker ":"Warehouse ")+saved.get("dimension","")+" origin="+saved.get("origin","")+" state="+saved.get("state","unknown")+" requested="+saved.get("requested","4")
            +" verified="+verified+" used="+used+" free="+(verified*54-used)+" unverified="+unknown+" reserveSlots="+saved.number("reserve",4)
            +(issue==null?"":"; "+issue)+out;
    }
    public static void event(String kind,String detail){ObservatoryTelemetry.event(kind,detail);}
}
