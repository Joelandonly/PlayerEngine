#!/usr/bin/env python3
"""Compile real warehouse/recovery/transfer classes against small in-memory fixtures.
These are server-thread logic tests, not Fabric compilation or gameplay validation.
"""
from pathlib import Path
import tempfile, subprocess, textwrap, os
PROJECT=Path(__file__).resolve().parents[2]
SOURCE=PROJECT/'common/src/main/java'
STUBS={
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
public record BlockPos(int x,int y,int z) {
 public static final BlockPos ZERO=new BlockPos(0,0,0);
 public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}
 public BlockPos immutable(){return this;}public BlockPos offset(int x,int y,int z){return new BlockPos(this.x+x,this.y+y,this.z+z);}
 public BlockPos above(){return above(1);}public BlockPos above(int n){return offset(0,n,0);}public BlockPos below(){return above(-1);}
 public BlockPos relative(Direction d){return offset(d.x,0,d.z);}public String toShortString(){return x+","+y+","+z;}
 public boolean closerThan(BlockPos p,double n){return Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2)<n*n;}
}''',
'net/minecraft/core/Direction.java': '''package net.minecraft.core;public enum Direction{NORTH(0,-1),SOUTH(0,1),EAST(1,0),WEST(-1,0);final int x,z;Direction(int x,int z){this.x=x;this.z=z;}public static class Plane{public static final Direction[] HORIZONTAL=values();}}''',
'net/minecraft/world/item/Item.java': '''package net.minecraft.world.item;public class Item{private final String id;public Item(String id){this.id=id;}public String id(){return id;}public ItemStack getDefaultInstance(){return new ItemStack(this,1);}public boolean equals(Object other){return other instanceof Item i&&id.equals(i.id);}public int hashCode(){return id.hashCode();}}''',
'net/minecraft/world/item/TieredItem.java': '''package net.minecraft.world.item;public class TieredItem extends Item{private final Tier tier;public TieredItem(String id,int uses){super(id);tier=new Tier(uses);}public Tier getTier(){return tier;}public record Tier(int getUses){}}''',
'net/minecraft/world/item/BlockItem.java': '''package net.minecraft.world.item;public class BlockItem extends Item{private final Object block;public BlockItem(String id,Object block){super(id);this.block=block;}public Object getBlock(){return block;}}''',
'net/minecraft/world/level/block/EntityBlock.java': '''package net.minecraft.world.level.block;public interface EntityBlock{}''',
'net/minecraft/core/component/DataComponents.java': '''package net.minecraft.core.component;public class DataComponents{public static final Object FOOD=new Object();}''',
'net/minecraft/world/item/ItemStack.java': '''package net.minecraft.world.item;
public class ItemStack{public static final ItemStack EMPTY=new ItemStack(new Item("empty"),0);private Item item;private int n,damage;public String components="";
 public ItemStack(Item item,int n){this.item=item;this.n=n;}public boolean isEmpty(){return n<=0;}public boolean is(Item i){return item.equals(i);}
 public Item getItem(){return item;}public int getCount(){return n;}public int getMaxStackSize(){return item instanceof TieredItem?1:64;}public void grow(int n){this.n+=n;}public void shrink(int n){this.n-=n;}
 public boolean has(Object component){return false;}public int getMaxDamage(){return item instanceof TieredItem tool?tool.getTier().getUses():0;}public int getDamageValue(){return damage;}public void setDamageValue(int value){damage=value;}
 public ItemStack split(int n){int take=Math.min(n,this.n);this.n-=take;return copyWithCount(take);}
 public ItemStack copyWithCount(int n){var s=new ItemStack(item,n);s.components=components;s.damage=damage;return s;}
 public static boolean matches(ItemStack a,ItemStack b){return a.n==b.n&&isSameItemSameComponents(a,b);}
 public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return a.item.equals(b.item)&&a.components.equals(b.components)&&a.damage==b.damage;}}
''',
'net/minecraft/core/registries/BuiltInRegistries.java': '''package net.minecraft.core.registries;import net.minecraft.world.item.Item;public class BuiltInRegistries{public static final BuiltInRegistries ITEM=new BuiltInRegistries();public String getKey(Item i){return i.id();}}''',
'net/minecraft/world/Container.java': '''package net.minecraft.world;import net.minecraft.world.item.*;public interface Container{
 int getContainerSize();ItemStack getItem(int i);void setItem(int i,ItemStack s);default int getMaxStackSize(){return 64;}
 default boolean canPlaceItem(int i,ItemStack s){return true;}default void setChanged(){}default int countItem(Item item){int n=0;for(int i=0;i<getContainerSize();i++)if(getItem(i).is(item))n+=getItem(i).getCount();return n;}
 default ItemStack removeItem(int i,int n){return getItem(i).split(n);}}
''',
'net/minecraft/world/WorldlyContainer.java': '''package net.minecraft.world;import net.minecraft.world.item.*;public interface WorldlyContainer extends Container{boolean canPlaceItemThroughFace(int slot,ItemStack item,Object face);}''',
'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys;public record Vec3(double x,double y,double z){public double distanceToSqr(double x,double y,double z){return Math.pow(this.x-x,2)+Math.pow(this.y-y,2)+Math.pow(this.z-z,2);}}''',
'net/minecraft/world/level/block/Blocks.java': '''package net.minecraft.world.level.block;public class Blocks{public static final String CHEST="chest";}''',
'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
import java.util.*;import net.minecraft.core.*;import net.minecraft.world.*;
public class ServerLevel{
 public long time;public String dimensionId="minecraft:overworld";public Map<BlockPos,Container> containers=new HashMap<>();public Set<BlockPos> protectedBlocks=new HashSet<>();
 public boolean hasChunkAt(BlockPos p){return true;}public long getGameTime(){return time;}public Dim dimension(){return new Dim(dimensionId);}
 public Object getBlockEntity(BlockPos p){return containers.get(p);}public State getBlockState(BlockPos p){return new State(containers.containsKey(p));}
 public Fluid getFluidState(BlockPos p){return new Fluid();}public record Fluid(){public boolean isEmpty(){return true;}}
 public record Dim(String location){}
 public record State(boolean chest){public boolean is(String block){return chest&&block.equals("chest");}public boolean isAir(){return !chest;}public boolean isSolidRender(ServerLevel w,BlockPos p){return true;}}
}''',
'com/player2/playerengine/automaton/api/entity/LivingEntityInventory.java': '''package com.player2.playerengine.automaton.api.entity;
import java.util.*;import net.minecraft.world.item.*;public class LivingEntityInventory{public int selectedSlot;
 public final List<ItemStack> main=new ArrayList<>(Collections.nCopies(36,ItemStack.EMPTY));public ItemStack getItem(int i){return main.get(i);}public int getMaxStackSize(){return 64;}public void setChanged(){}
 public void insertStack(ItemStack stack){for(int i=0;i<36&&!stack.isEmpty();i++){ItemStack s=main.get(i);if(s.isEmpty()){main.set(i,stack.split(Math.min(64,stack.getCount())));}else if(ItemStack.isSameItemSameComponents(s,stack)){int n=Math.min(64-s.getCount(),stack.getCount());s.grow(n);stack.shrink(n);}}}}
''',
'com/player2/playerengine/PlayerEngineController.java': '''package com.player2.playerengine;
import net.minecraft.core.*;import net.minecraft.world.item.*;import net.minecraft.world.phys.*;import net.minecraft.server.level.*;import com.player2.playerengine.automaton.api.entity.*;
public class PlayerEngineController{
 public final ServerLevel world=new ServerLevel();public final LivingEntityInventory inventory=new LivingEntityInventory();public final Entity entity=new Entity();public final Behaviour behaviour=new Behaviour();public String identity="test";
 public ServerLevel getWorld(){return world;}public LivingEntityInventory getInventory(){return inventory;}public Entity getEntity(){return entity;}public Storage getItemStorage(){return new Storage();}public Behaviour getBehaviour(){return behaviour;}public Data getAIPersistantData(){return new Data();}
 public class Data{public String getCharacterId(){return identity;}}
 public Settings getModSettings(){return new Settings();}public class Settings{public boolean isImportant(Item item){return false;}}
 public final UserTaskChain chain=new UserTaskChain();public UserTaskChain getUserTaskChain(){return chain;}public class UserTaskChain{public com.player2.playerengine.tasks.base.Task current;public com.player2.playerengine.tasks.base.Task getCurrentTask(){return current;}}
 public class Entity{public BlockPos pos=BlockPos.ZERO;public double distanceToSqr(double x,double y,double z){return position().distanceToSqr(x,y,z);}public Vec3 position(){return new Vec3(pos.x(),pos.y(),pos.z());}public BlockPos blockPosition(){return pos;}}
 public class Storage{public int getItemCountInventoryOnly(Item... items){int n=0;for(var s:inventory.main)for(var i:items)if(s.is(i))n+=s.getCount();return n;}public int getItemCount(Item... items){return getItemCountInventoryOnly(items);}}
 public class Behaviour{public java.util.Set<Item> protectedItems=new java.util.HashSet<>();public boolean isProtected(Item i){return protectedItems.contains(i);}}
}''',
'com/player2/playerengine/tasks/base/Task.java': '''package com.player2.playerengine.tasks.base;
import com.player2.playerengine.PlayerEngineController;import java.util.function.Predicate;
public abstract class Task{
 public PlayerEngineController controller;private boolean stopped,started;public Task child;
 public void step(PlayerEngineController c){controller=c;if(!started){onStart();started=true;}Task next=onTick();if(next!=null){child=next;next.step(c);}}
 public void stop(){stopped=true;if(started)onStop(null);if(child!=null)child.stop();}public boolean stopped(){return stopped;}
 public boolean isFinished(){return false;}public boolean containsTask(Predicate<Task> test){return test.test(this)||child!=null&&child.containsTask(test);}
 protected void setDebugState(String s){}protected abstract void onStart();protected abstract Task onTick();protected abstract void onStop(Task next);protected abstract boolean isEqual(Task t);protected abstract String toDebugString();
}''',
'com/player2/playerengine/tasks/ResourceTask.java': '''package com.player2.playerengine.tasks;import com.player2.playerengine.tasks.base.Task;import com.player2.playerengine.util.ItemTarget;
public class ResourceTask extends Task{public boolean finished;public ItemTarget[] targets={};public ItemTarget[] getItemTargets(){return targets;}public boolean isFinished(){return finished;}protected void onStart(){}protected Task onTick(){return null;}protected void onStop(Task n){}protected boolean isEqual(Task t){return this==t;}protected String toDebugString(){return "resource";}}
''',
'com/player2/playerengine/schematic/SchematicProjectTask.java': '''package com.player2.playerengine.schematic;import com.player2.playerengine.tasks.base.Task;import com.player2.playerengine.tasks.ResourceTask;import com.player2.playerengine.PlayerEngineController;public class SchematicProjectTask extends ResourceTask{public boolean reservesMaterial(String id){return id.equals("minecraft:stone");}public static boolean isProjectMaterial(PlayerEngineController mod,String id){return id.equals("minecraft:stone");}}
''',
'com/player2/playerengine/util/ItemTarget.java': '''package com.player2.playerengine.util;import net.minecraft.world.item.*;public record ItemTarget(Item item,int count){public ItemTarget(Item item){this(item,1);}public boolean matches(Item i){return item.equals(i);}public Item[] getMatches(){return new Item[]{item};}public int getTargetCount(){return count;}public boolean isEmpty(){return item==null;}public static boolean nullOrEmpty(ItemTarget t){return t==null||t.isEmpty();}}''',
'com/player2/playerengine/tasks/movement/GetToBlockTask.java': '''package com.player2.playerengine.tasks.movement;import com.player2.playerengine.tasks.base.Task;import net.minecraft.core.BlockPos;public class GetToBlockTask extends Task{
 private final BlockPos target;public GetToBlockTask(BlockPos p){target=p;}protected void onStart(){}protected Task onTick(){controller.entity.pos=target;return null;}protected void onStop(Task n){}protected boolean isEqual(Task t){return this==t;}protected String toDebugString(){return "fixture travel";}}
''',
'com/player2/playerengine/tasks/crafting/DescribesProgress.java': '''package com.player2.playerengine.tasks.crafting;public interface DescribesProgress{String describeProgress();}''',
'com/player2/playerengine/tasks/deferred/DeferredJobStore.java': '''package com.player2.playerengine.tasks.deferred;import java.nio.file.*;public class DeferredJobStore{public static Path root;public static DeferredJobStore get(){return new DeferredJobStore();}public Path worldRoot(){return root;}}
''',
'com/player2/playerengine/agentic/InventoryCleanupStorage.java': '''package com.player2.playerengine.agentic;import com.player2.playerengine.PlayerEngineController;import net.minecraft.core.BlockPos;import java.util.*;public class InventoryCleanupStorage{public static Optional<BlockPos> legacy=Optional.empty();public static Optional<BlockPos> destination(PlayerEngineController m){return legacy;}public static boolean isProjectStorage(PlayerEngineController m,BlockPos p){return m.world.protectedBlocks.contains(p);}}
''',
'com/player2/playerengine/agentic/StorageIntelligence.java': '''package com.player2.playerengine.agentic;public class StorageIntelligence{public static void afterTransfer(Object t){}}
''',
'com/player2/playerengine/safezone/SafeZoneService.java': '''package com.player2.playerengine.safezone;import net.minecraft.core.*;import net.minecraft.server.level.*;public class SafeZoneService{public static boolean protectedAt(ServerLevel w,BlockPos p){return w.protectedBlocks.contains(p);}}
''',
'com/player2/playerengine/observatory/ObservatoryTelemetry.java': '''package com.player2.playerengine.observatory;import java.util.*;public class ObservatoryTelemetry{public static final List<String> events=new ArrayList<>();public static void event(String type,String detail){events.add(type+":"+detail);}public static void set(String key,String detail){}}
''',
'com/player2/playerengine/containeraccess/StorageItemArgs.java': '''package com.player2.playerengine.containeraccess;import net.minecraft.world.item.Item;public class StorageItemArgs{public static final int MAX_COUNT=3456;public record ItemQuery(Item item,String displayId,int countOrAll){public boolean isAll(){return countOrAll<0;}}public static String displayIdFor(Item item){return item.id();}}
''',
'com/player2/playerengine/containeraccess/StorageAccessCode.java': '''package com.player2.playerengine.containeraccess;public enum StorageAccessCode{OK,CONTAINER_UNREACHABLE,CONTAINER_TOO_FAR,CONTAINER_MISSING,INSUFFICIENT_ITEMS,INSUFFICIENT_SPACE,ITEM_NOT_ALLOWED,CONTAINER_CHANGED;public String token(){return name();}}
''',
'com/player2/playerengine/containeraccess/ScanReportFormatter.java': '''package com.player2.playerengine.containeraccess;public class ScanReportFormatter{public enum TransferDirection{DEPOSIT,WITHDRAW}public record EntryFailure(StorageAccessCode code,String id,String detail){}public record EntryOutcome(String id,int moved,int requested,int present,String shortReason){}}
''',
'com/player2/playerengine/containeraccess/ResolvedContainer.java': '''package com.player2.playerengine.containeraccess;import net.minecraft.core.BlockPos;import net.minecraft.world.Container;public record ResolvedContainer(Container container,Kind kind,BlockPos canonicalPos,BlockPos secondaryPos){public record Kind(){public String token(){return "double-chest";}}}
''',
'com/player2/playerengine/containeraccess/ContainerResolver.java': '''package com.player2.playerengine.containeraccess;import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;public class ContainerResolver{
 public record Resolution(ResolvedContainer resolved,StorageAccessCode code){public boolean ok(){return resolved!=null;}public String detail(){return code.name();}}
 public static Resolution resolve(ServerLevel w,BlockPos p){var c=w.containers.get(p);if(c==null)return new Resolution(null,StorageAccessCode.CONTAINER_MISSING);BlockPos first=p.x()%4==2?p.offset(-1,0,0):p;return new Resolution(new ResolvedContainer(c,new ResolvedContainer.Kind(),first,first.offset(1,0,0)),StorageAccessCode.OK);}
 public static String formatPos(BlockPos p){return p.toShortString();}}
''',
'com/player2/playerengine/containeraccess/ContainerAnimationHelper.java': '''package com.player2.playerengine.containeraccess;public class ContainerAnimationHelper{public static void open(Object w,Object c){}public static void close(Object w,Object c){}}
''',
'com/player2/playerengine/containeraccess/ContainerScanService.java': '''package com.player2.playerengine.containeraccess;public class ContainerScanService{public static void refreshLegacyCache(Object c,Object a,Object b){}}
''',
}
# Construction fixtures model block state, material consumption and builder ownership.
STUBS.update({
'net/minecraft/world/level/block/state/properties/ChestType.java': 'package net.minecraft.world.level.block.state.properties;public enum ChestType{SINGLE,LEFT,RIGHT}',
'net/minecraft/world/level/block/state/properties/Property.java': 'package net.minecraft.world.level.block.state.properties;public record Property<T>(String name){}',
'net/minecraft/world/level/block/Blocks.java': '''package net.minecraft.world.level.block;import net.minecraft.world.level.block.state.BlockState;public class Blocks{
 public static class Block{private final String name;public Block(String name){this.name=name;}public String name(){return name;}public BlockState defaultBlockState(){return new BlockState(this);}}
 public static final Block CHEST=new Block("chest"),COBBLESTONE=new Block("cobblestone"),BLACKSTONE=new Block("blackstone"),CRIMSON_STEM=new Block("crimson_stem"),WARPED_STEM=new Block("warped_stem"),CRIMSON_FUNGUS=new Block("crimson_fungus"),WARPED_FUNGUS=new Block("warped_fungus"),CRIMSON_ROOTS=new Block("crimson_roots"),WARPED_ROOTS=new Block("warped_roots"),NETHER_SPROUTS=new Block("nether_sprouts"),NETHER_WART_BLOCK=new Block("nether_wart_block"),WARPED_WART_BLOCK=new Block("warped_wart_block"),SHROOMLIGHT=new Block("shroomlight"),WEEPING_VINES=new Block("weeping_vines"),WEEPING_VINES_PLANT=new Block("weeping_vines_plant"),TWISTING_VINES=new Block("twisting_vines"),TWISTING_VINES_PLANT=new Block("twisting_vines_plant"),NETHER_BRICKS=new Block("nether_bricks"),NETHERRACK=new Block("netherrack"),FIRE=new Block("fire"),SOUL_FIRE=new Block("soul_fire"),STONE=new Block("stone"),CRIMSON_NYLIUM=new Block("crimson_nylium"),PLANKS=new Block("planks"),BEDROCK=new Block("bedrock"),AIR=new Block("air"),MAGMA_BLOCK=new Block("magma"),GRAVEL=new Block("gravel"),SNOW=new Block("snow"),SHORT_GRASS=new Block("short_grass"),TALL_GRASS=new Block("tall_grass"),FERN=new Block("fern"),LARGE_FERN=new Block("large_fern"),DIRT=new Block("dirt"),OAK_LOG=new Block("log");}''',
'net/minecraft/world/level/block/GameMasterBlock.java': 'package net.minecraft.world.level.block;public interface GameMasterBlock{}',
'net/minecraft/tags/BlockTags.java': '''package net.minecraft.tags; public enum BlockTags {LOGS,LEAVES,DIRT,SAND,BASE_STONE_OVERWORLD,BASE_STONE_NETHER,MINEABLE_WITH_AXE,MINEABLE_WITH_SHOVEL,MINEABLE_WITH_PICKAXE}''',
'net/minecraft/world/level/block/state/BlockState.java': '''package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.properties.*;import net.minecraft.core.*;import net.minecraft.server.level.*;
public class BlockState{
 public final Blocks.Block block;public Direction facing=Direction.NORTH;public ChestType type=ChestType.SINGLE;
 public BlockState(Blocks.Block b){block=b;}public boolean is(Blocks.Block b){return b.equals(block);}public boolean isAir(){return is(Blocks.AIR);}
 public Blocks.Block getBlock(){return block;}public boolean is(net.minecraft.tags.BlockTags tag){return switch(tag){case LOGS->is(Blocks.OAK_LOG)||is(Blocks.CRIMSON_STEM)||is(Blocks.WARPED_STEM);case DIRT->is(Blocks.DIRT);case BASE_STONE_OVERWORLD->is(Blocks.STONE);case BASE_STONE_NETHER->is(Blocks.NETHERRACK)||is(Blocks.BLACKSTONE);case MINEABLE_WITH_AXE->is(Blocks.OAK_LOG)||is(Blocks.CRIMSON_STEM)||is(Blocks.WARPED_STEM)||is(Blocks.PLANKS);case MINEABLE_WITH_SHOVEL->is(Blocks.DIRT);case MINEABLE_WITH_PICKAXE->is(Blocks.NETHERRACK)||is(Blocks.CRIMSON_NYLIUM)||is(Blocks.BLACKSTONE)||is(Blocks.NETHER_BRICKS)||is(Blocks.STONE);default->false;};}
 public float getDestroySpeed(ServerLevel w,BlockPos p){return is(Blocks.BEDROCK)?-1:1;}public boolean isSolidRender(ServerLevel w,BlockPos p){return !isAir()&&!is(Blocks.CHEST)&&!is(Blocks.CRIMSON_FUNGUS)&&!is(Blocks.WARPED_FUNGUS);}
 @SuppressWarnings("unchecked") public <T>T getValue(Property<T> p){return (T)(p.name().equals("facing")?facing:type);}
 public <T>BlockState setValue(Property<T> p,T value){var s=new BlockState(block);s.facing=facing;s.type=type;if(p.name().equals("facing"))s.facing=(Direction)value;else s.type=(ChestType)value;return s;}}
''',
'net/minecraft/world/level/block/ChestBlock.java': '''package net.minecraft.world.level.block;import net.minecraft.core.*;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.*;
public class ChestBlock{public static final Property<Direction> FACING=new Property<>("facing");public static final Property<ChestType> TYPE=new Property<>("type");
 public static Direction getConnectedDirection(BlockState s){boolean left=s.type==ChestType.LEFT;return s.facing==Direction.NORTH?(left?Direction.EAST:Direction.WEST):(left?Direction.WEST:Direction.EAST);}}
''',
'net/minecraft/world/item/Items.java': '''package net.minecraft.world.item;public class Items{public static final Item COBBLESTONE=new Item("minecraft:cobblestone"),CHEST=new Item("minecraft:chest"),BLACKSTONE=new Item("minecraft:blackstone"),NETHER_BRICKS=new Item("minecraft:nether_bricks"),STICK=new Item("minecraft:stick");}''',
'com/player2/playerengine/automaton/api/schematic/AbstractSchematic.java': '''package com.player2.playerengine.automaton.api.schematic;import java.util.*;import net.minecraft.world.level.block.state.*;public abstract class AbstractSchematic{public AbstractSchematic(int x,int y,int z){}public boolean placeFromBelow(){return false;}public abstract BlockState desiredState(int x,int y,int z,BlockState current,List<BlockState> available);}''',
'com/player2/playerengine/nativecommands/NativeAccess.java': '''package com.player2.playerengine.nativecommands;public class NativeAccess{public static Object get(Object obj,String field)throws Exception{return obj.getClass().getField(field).get(obj);}}''',
'com/player2/playerengine/TaskCatalogue.java': '''package com.player2.playerengine;import com.player2.playerengine.tasks.*;import com.player2.playerengine.tasks.base.*;import net.minecraft.world.item.*;
public class TaskCatalogue{public static boolean failMaterials;public static ResourceTask getItemTask(Item item,int amount){return new ResourceTask(){protected Task onTick(){if(!failMaterials){for(int i=0;i<36;i++)if(controller.inventory.main.get(i).isEmpty()){controller.inventory.main.set(i,new ItemStack(item,amount));finished=true;break;}}return null;}};}}
''',
'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
import java.util.*;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;
public class ServerLevel{
 public long time;public String dimensionId="minecraft:overworld";public Map<BlockPos,Container> containers=new HashMap<>();public Set<BlockPos> protectedBlocks=new HashSet<>();public Map<BlockPos,BlockState> blocks=new HashMap<>();
 public boolean hasChunkAt(BlockPos p){return true;}public long getGameTime(){return time;}public Dim dimension(){return new Dim(dimensionId);}
 public Object getBlockEntity(BlockPos p){return containers.get(p);}public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,(containers.containsKey(p)?Blocks.CHEST:p.y()<0?Blocks.STONE:Blocks.AIR).defaultBlockState());}
 public Fluid getFluidState(BlockPos p){return new Fluid();}public record Fluid(){public boolean isEmpty(){return true;}}public record Dim(String location){}
}''',
})
STUBS['net/minecraft/core/BlockPos.java']=STUBS['net/minecraft/core/BlockPos.java'].replace('public BlockPos immutable()', 'public long asLong(){return ((long)x<<38)|((long)z&0x3ffffff)<<12|((long)y&4095);}public BlockPos west(){return offset(-1,0,0);}public BlockPos immutable()')
STUBS['net/minecraft/world/item/Items.java']=STUBS['net/minecraft/world/item/Items.java'][:-1]+''.join(
    f'public static final Item {tier}_{tool}=new Item("minecraft:{tier.lower()}_{tool.lower()}");'
    for tier in ('WOODEN','STONE','IRON','GOLDEN','DIAMOND','NETHERITE')
    for tool in ('AXE','PICKAXE','SHOVEL')
)+'}'
STUBS['com/player2/playerengine/reliability/WorksiteAnchor.java']='''package com.player2.playerengine.reliability;public class WorksiteAnchor{public static void open(Object owner,Object controller,Object origin,String blueprint){}public static void close(Object owner){}}'''
STUBS['com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.java']='''package com.player2.playerengine.tasks.crafting;import com.player2.playerengine.tasks.ResourceTask;import net.minecraft.world.item.Item;public class CraftMacroResourceTask extends ResourceTask{public Item ingredient;public enum Phase{COLLECT,FIND_TABLE}public Phase getPhase(){return Phase.COLLECT;}public boolean keepsIngredient(Item item){return item.equals(ingredient);}}'''
STUBS['com/player2/playerengine/schematic/SchematicBuildSupport.java']='''package com.player2.playerengine.schematic;import net.minecraft.core.BlockPos;public class SchematicBuildSupport{public interface OrientedChest{BlockPos partner();}public static void bindEntity(Object entity,Object builder){}}'''
STUBS['com/player2/playerengine/acquisition/NativeAcquisitionPlanner.java']='''package com.player2.playerengine.acquisition;import com.player2.playerengine.*;import com.player2.playerengine.agentic.MaterialReservationService;import com.player2.playerengine.tasks.base.Task;import net.minecraft.world.item.Item;
public class NativeAcquisitionPlanner{public record Decision(Task task,String reason,String route){}public static Decision choose(PlayerEngineController mod,String id,int count,MaterialReservationService ledger){return new Decision(TaskCatalogue.getItemTask(new Item(id),count),"fixture","fixture");}}'''
STUBS['com/player2/playerengine/structureprotection/PlayerPlacedBlockStore.java']='''package com.player2.playerengine.structureprotection;import net.minecraft.core.BlockPos;public class PlayerPlacedBlockStore{public static final java.util.Set<BlockPos> placed=new java.util.HashSet<>();public static PlayerPlacedBlockStore get(){return new PlayerPlacedBlockStore();}public boolean contains(String dimension,BlockPos pos){return placed.contains(pos);}}'''
STUBS['com/player2/playerengine/tasks/base/Task.java']=STUBS['com/player2/playerengine/tasks/base/Task.java'].replace(
    'public boolean stopped(){return stopped;}', '''public boolean stopped(){return stopped;}
 public void pauseForGear(){if(this instanceof TransientlyResumableTask resumable && resumable.prepareForTransientResume(TaskSuspensionCause.HIGHER_PRIORITY_CHAIN)){if(child!=null)child.stop();child=null;resumable.afterChildrenStopped();started=false;}}
 ''')
STUBS['com/player2/playerengine/safezone/SafeZoneService.java']=STUBS['com/player2/playerengine/safezone/SafeZoneService.java'].replace('public static boolean protectedAt','public static boolean deny(ServerLevel w,BlockPos p,String action){return protectedAt(w,p);}public static boolean protectedAt')
STUBS['com/player2/playerengine/PlayerEngineController.java']=STUBS['com/player2/playerengine/PlayerEngineController.java'].replace('public class Behaviour{','''public final Baritone baritone=new Baritone();public Baritone getBaritone(){return baritone;}
 public class Baritone{public final Builder builder=new Builder();public Builder getBuilderProcess(){return builder;}}
 public class Builder{
  public Object schematic,realSchematic;public boolean active;public int placed,cancelled,cleared;public final java.util.List<BlockPos> placementOrder=new java.util.ArrayList<>();
  public boolean isActive(){return active;}public void onLostControl(){active=false;cancelled++;schematic=null;realSchematic=null;}
  public void buildSingleCell(String name,com.player2.playerengine.automaton.api.schematic.AbstractSchematic plan,BlockPos target){build(name,plan,target);}
  public void build(String name,com.player2.playerengine.automaton.api.schematic.AbstractSchematic plan,BlockPos target){
   schematic=plan;active=true;var state=plan.desiredState(0,0,0,world.getBlockState(target),java.util.List.of());
   if(state.isAir()){world.blocks.put(target,state);cleared++;return;}
   Item material=state.is(net.minecraft.world.level.block.Blocks.CHEST)?net.minecraft.world.item.Items.CHEST:state.is(net.minecraft.world.level.block.Blocks.BLACKSTONE)?net.minecraft.world.item.Items.BLACKSTONE:state.is(net.minecraft.world.level.block.Blocks.NETHER_BRICKS)?net.minecraft.world.item.Items.NETHER_BRICKS:net.minecraft.world.item.Items.COBBLESTONE;
   boolean found=false;for(var stack:inventory.main)if(stack.is(material)&&!stack.isEmpty()){stack.shrink(1);found=true;break;}if(!found)return;
   world.blocks.put(target,state);placed++;placementOrder.add(target);
   if(state.is(net.minecraft.world.level.block.Blocks.CHEST)){
    var partner=((com.player2.playerengine.schematic.SchematicBuildSupport.OrientedChest)plan).partner();
    var existing=partner==null?null:world.containers.get(partner);
    if(partner!=null&&(existing==null||world.getBlockState(partner).facing!=state.facing||state.type==net.minecraft.world.level.block.state.properties.ChestType.SINGLE||!target.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)).equals(partner)))throw new AssertionError("Placement must join the saved half and use its facing");
    world.containers.put(target,existing==null?new warehousefixture.WarehouseTests.Chest():existing);
   }
  }
 }
 public class Behaviour{public int depth;public void push(){depth++;}public void pop(){depth--;}public void addProtectedItems(Item... items){}public void avoidBlockBreaking(java.util.function.Predicate<BlockPos> p){}public void avoidBlockPlacing(java.util.function.Predicate<BlockPos> p){}''')
STUBS['com/player2/playerengine/containeraccess/ContainerResolver.java']=STUBS['com/player2/playerengine/containeraccess/ContainerResolver.java'].replace('return new Resolution(new ResolvedContainer(c,new ResolvedContainer.Kind(),first,first.offset(1,0,0)),StorageAccessCode.OK);','BlockPos second=first.offset(1,0,0);if(!w.containers.containsKey(first)||!w.containers.containsKey(second))return new Resolution(new ResolvedContainer(c,new ResolvedContainer.Kind(),p,null),StorageAccessCode.OK);return new Resolution(new ResolvedContainer(c,new ResolvedContainer.Kind(),first,second),StorageAccessCode.OK);')
HARNESS='''package warehousefixture;
import java.nio.file.*;import java.util.*;import com.player2.playerengine.*;import com.player2.playerengine.warehouse.*;import com.player2.playerengine.agentic.*;import com.player2.playerengine.tasks.*;import com.player2.playerengine.tasks.base.*;import com.player2.playerengine.tasks.container.*;import com.player2.playerengine.tasks.deferred.*;import com.player2.playerengine.containeraccess.*;import com.player2.playerengine.util.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.core.*;
public class WarehouseTests{
 static int tests;static final Item DIRT=new Item("minecraft:dirt"),STONE=new Item("minecraft:stone"),WOOD=new Item("minecraft:oak_planks"),GOLD=new Item("minecraft:gold_ingot");
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);tests++;System.out.println("PASS "+label);}
 public static class Chest implements Container{ItemStack[] slots=new ItemStack[54];public Chest(){Arrays.fill(slots,ItemStack.EMPTY);}public int getContainerSize(){return 54;}public ItemStack getItem(int n){return slots[n];}public void setItem(int n,ItemStack s){slots[n]=s;}}
 static PlayerEngineController create(String name,int occupied)throws Exception{var mod=new PlayerEngineController();mod.identity=name;for(int i=0;i<occupied;i++)mod.inventory.main.set(i,new ItemStack(DIRT,64));var saved=WarehouseManager.ledger(mod);saved.set("origin","0,0,0");saved.set("dimension","minecraft:overworld");saved.set("requested",4);saved.set("enabled",true);for(var cell:WarehouseLayout.floor(4)){var p=WarehouseManager.at(saved,cell);mod.world.blocks.put(p,net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());saved.set("placed."+WarehouseManager.key(p),true);}saved.save();return mod;}
 static BlockPos chest(PlayerEngineController mod,int index,boolean full)throws Exception{var saved=WarehouseManager.ledger(mod);var pair=WarehouseLayout.chests(4).get(index);var first=WarehouseManager.at(saved,pair.first());var second=WarehouseManager.at(saved,pair.second());Chest chest=new Chest();if(full)for(int i=0;i<54;i++)chest.slots[i]=new ItemStack(GOLD,64);mod.world.containers.put(first,chest);mod.world.containers.put(second,chest);saved.set("container."+WarehouseManager.key(first),true);saved.set("secondary."+WarehouseManager.key(first),WarehouseManager.key(second));saved.save();return first;}
 static ResourceTask root(PlayerEngineController mod){var task=new ResourceTask();task.controller=mod;task.targets=new ItemTarget[]{new ItemTarget(WOOD)};return task;}
 static Task advance(ResourceTask root,int ticks){Task choice=null;for(int i=0;i<ticks;i++){root.controller.world.time++;choice=AutoInventoryCleanup.beforeResourceTick(root);if(choice!=null)choice.step(root.controller);}return choice;}
 public static void main(String[] args)throws Exception{
 DeferredJobStore.root=Files.createTempDirectory("warehouse-logic-");
 var four=WarehouseLayout.chests(4);var eight=WarehouseLayout.chests(8);check(eight.subList(0,4).equals(four),"four-to-eight expansion preserves existing pairs");
 check(WarehouseLayout.floor(4).size()==56&&WarehouseLayout.floor(8).size()==112,"bounded rectangular modules");
 Set<WarehouseLayout.Cell> visited=new HashSet<>();var aisle=WarehouseLayout.aisle(8);ArrayDeque<WarehouseLayout.Cell> q=new ArrayDeque<>();q.add(aisle.iterator().next());while(!q.isEmpty()){var p=q.remove();if(!aisle.contains(p)||!visited.add(p))continue;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})q.add(new WarehouseLayout.Cell(p.x()+d[0],1,p.z()+d[1]));}check(visited.equals(aisle),"all pathways connected after expansion");
 for(var pair:eight)check(aisle.contains(new WarehouseLayout.Cell(pair.first().x(),1,pair.first().z()+(pair.north()?-1:1))),"each chest has aisle access");
 check(WarehousePolicy.needsRecovery(0,4)&&WarehousePolicy.needsRecovery(3,4)&&!WarehousePolicy.needsRecovery(4,4),"36/36 and 33/36 trigger recovery; four free resumes");
 var mod=create("full",36);var first=chest(mod,0,true);var second=chest(mod,1,false);check(WarehouseManager.allocate(mod,DIRT.getDefaultInstance(),Set.of()).pos().equals(second),"full first double chest allocates second");
 var task=root(mod);check(advance(task,120)==null&&InventorySpace.inspect(mod).free()>=4,"full inventory unloads and original resource task resumes");
 check(!task.finished,"recovery retains parent goal instead of marking it complete");
 check(mod.world.containers.get(second).countItem(DIRT)==36*64,"deposit conservation across all 36 slots");
 var partial=create("partial",33);var batchDepot=chest(partial,0,false);var batchRoot=root(partial);batchRoot.targets=new ItemTarget[]{new ItemTarget(WOOD,256)};
 var unrelated=new Item("test:kept_item");var gravel=new Item("minecraft:gravel");for(int i=0;i<33;i++)partial.inventory.main.set(i,new ItemStack(unrelated,1));
 for(int i=0;i<5;i++)partial.inventory.main.set(i,new ItemStack(i<2?DIRT:gravel,64));
 check(advance(batchRoot,120)==null&&InventorySpace.inspect(partial).free()==8&&partial.world.containers.get(batchDepot).countItem(gravel)==192&&partial.world.containers.get(batchDepot).countItem(DIRT)==128,"four-slot task triggers below four and unloads multiple item types up to eight free slots");
 partial.inventory.main.set(0,new ItemStack(unrelated,1));check(AutoInventoryCleanup.beforeResourceTick(batchRoot)==null,"one new occupied slot after a batch does not send Ellie back");
 var merge=create("merge-headroom",36);chest(merge,0,false);merge.inventory.main.set(0,new ItemStack(WOOD,63));var mergeRoot=root(merge);mergeRoot.targets=new ItemTarget[]{new ItemTarget(WOOD,64)};
 check(AutoInventoryCleanup.beforeResourceTick(mergeRoot)==null,"remaining output fits a partial stack even with zero empty slots");
 var room=create("reserve-room",25);chest(room,0,false);var roomLedger=WarehouseManager.ledger(room);roomLedger.set("reserve",12);roomLedger.save();
 check(AutoInventoryCleanup.beforeResourceTick(root(room))==null,"one-slot output does not trigger a trip with eleven free slots and reserve twelve");
 var pickaxe=create("pickaxe",36);chest(pickaxe,0,false);var craft=root(pickaxe);craft.targets=new ItemTarget[]{new ItemTarget(new Item("minecraft:stone_pickaxe"))};check(advance(craft,120)==null&&InventorySpace.inspect(pickaxe).free()>=4,"replacement-pickaxe resource request recovers full inventory before crafting");
 var blocked=create("blocked",36);for(int i=0;i<4;i++)chest(blocked,i,true);Task waiting=advance(root(blocked),200);check(waiting!=null&&!(waiting instanceof BoundedContainerTransferTask)&&InventorySpace.inspect(blocked).free()==0,"all chests full pauses safely without losing inventory");
 var protectedMod=create("reserved",36);chest(protectedMod,0,false);var reservation=new MaterialReservationService();reservation.reserve(protectedMod,DIRT,64);check(!(advance(root(protectedMod),20) instanceof BoundedContainerTransferTask)&&InventorySpace.inspect(protectedMod).free()==0,"active ledger reservations never unloaded");
 var schematic=create("schematic",36);for(int i=0;i<36;i++)schematic.inventory.main.set(i,new ItemStack(STONE,64));chest(schematic,0,false);check(!(advance(root(schematic),20) instanceof BoundedContainerTransferTask),"schematic project supplies retained");
 var gear=create("gear",36);for(int i=0;i<36;i++)gear.inventory.main.set(i,new ItemStack(new Item("minecraft:diamond_pickaxe"),1));chest(gear,0,false);check(!(advance(root(gear),20) instanceof BoundedContainerTransferTask)&&InventorySpace.inspect(gear).free()==0,"valuable tools are never discarded or unloaded");
 var upgraded=create("upgraded",36);var upgradeDepot=chest(upgraded,0,false);var oldPick=new net.minecraft.world.item.TieredItem("minecraft:wooden_pickaxe",59);var bestPick=new net.minecraft.world.item.TieredItem("minecraft:diamond_pickaxe",1561);var filler=new Item("test:kept_item");
 for(int i=0;i<36;i++)upgraded.inventory.main.set(i,new ItemStack(filler,1));
 var namedPick=new ItemStack(oldPick,1);namedPick.components="custom name";var wornPick=new ItemStack(oldPick,1);wornPick.setDamageValue(10);var equippedPick=new ItemStack(oldPick,1);equippedPick.setDamageValue(10);
 upgraded.inventory.main.set(0,equippedPick);upgraded.inventory.main.set(1,namedPick);upgraded.inventory.main.set(2,wornPick);upgraded.inventory.main.set(3,new ItemStack(bestPick,1));
 var highReserve=WarehouseManager.ledger(upgraded);highReserve.set("reserve",20);highReserve.save();var upgradeTask=root(upgraded);AutoInventoryCleanup.beforeResourceTick(upgradeTask);check(AutoInventoryCleanup.transferringWithin(upgradeTask),"acquisition can detect a pending inventory recovery transfer");var cleanup=advance(upgradeTask,40);
 check(upgraded.world.containers.get(upgradeDepot).countItem(oldPick)==1&&upgraded.inventory.main.get(2).isEmpty()&&upgraded.inventory.main.get(0)==equippedPick&&upgraded.inventory.main.get(1)==namedPick&&upgraded.inventory.main.get(3).is(bestPick),"stashes only the superseded tool variant and keeps equipped, named and better tools");
 check(cleanup==null&&InventorySpace.inspect(upgraded).free()==1,"high reserve remains a target when protected items occupy the remaining slots");
 upgraded.inventory.main.add(ItemStack.EMPTY);check(InventorySpace.inspect(upgraded).total()==37&&InventorySpace.inspect(upgraded).free()==2,"inventory snapshot derives capacity from the live inventory");
 var crafting=create("recipe-ingredients",36);var craftingDepot=chest(crafting,0,false);for(int i=0;i<36;i++)crafting.inventory.main.set(i,new ItemStack(filler,1));
 var plank=new net.minecraft.world.item.BlockItem("minecraft:oak_planks",new Object());var fence=new net.minecraft.world.item.BlockItem("minecraft:nether_brick_fence",new Object());crafting.inventory.main.set(1,new ItemStack(plank,32));crafting.inventory.main.set(2,new ItemStack(fence,14));
 var craftingRoot=new com.player2.playerengine.tasks.crafting.CraftMacroResourceTask();craftingRoot.ingredient=plank;craftingRoot.controller=crafting;var gatheringChild=root(crafting);gatheringChild.targets=new ItemTarget[]{new ItemTarget(new Item("minecraft:oak_log"))};craftingRoot.child=gatheringChild;crafting.chain.current=craftingRoot;advance(gatheringChild,30);
 check(crafting.world.containers.get(craftingDepot).countItem(fence)==14&&crafting.inventory.main.get(1).getCount()==32,"ordinary surplus blocks are stored and active recipe ingredients stay carried");
 var safe=create("safe",36);var p=chest(safe,0,false);safe.world.protectedBlocks.add(p.offset(1,0,0));check(WarehouseManager.allocate(safe,DIRT.getDefaultInstance(),Set.of())==null,"protected double-chest half rejects entire destination");
 var dim=create("dimension",36);chest(dim,0,false);var ledger=WarehouseManager.ledger(dim);ledger.set("dimension","minecraft:the_nether");ledger.save();check(!(advance(root(dim),20) instanceof BoundedContainerTransferTask),"dimension mismatch blocks recovery");
 var noImport=create("legacy",36);var legacy=chest(noImport,0,false);InventoryCleanupStorage.legacy=Optional.of(legacy);var l=WarehouseManager.ledger(noImport);l.set("enabled",false);l.save();check(!(advance(root(noImport),20) instanceof BoundedContainerTransferTask),"warehouse off cannot fall back to legacy chest");InventoryCleanupStorage.legacy=Optional.empty();
 var paused=create("cancel",36);chest(paused,0,false);var original=root(paused);Task active=AutoInventoryCleanup.beforeResourceTick(original);active.step(paused);AutoInventoryCleanup.cancel(original);check(active.stopped()&&InventorySpace.inspect(paused).free()==0,"cancel closes pending recovery before moving items");
 var stackMod=create("stack",0);var stackChest=chest(stackMod,0,true);var c=stackMod.world.containers.get(stackChest);c.setItem(2,new ItemStack(DIRT,60));check(WarehouseManager.capacity(c,DIRT.getDefaultInstance())==4,"allocation uses partial-stack headroom even with no empty slots");
 var organize=create("organize",0);var oa=chest(organize,0,false);var ob=chest(organize,1,false);organize.world.containers.get(oa).setItem(0,new ItemStack(DIRT,60));organize.world.containers.get(ob).setItem(0,new ItemStack(DIRT,20));var sort=new WarehouseOrganizeTask();for(int i=0;i<160;i++){organize.world.time++;sort.step(organize);}check(sort.isSuccessful()&&organize.world.containers.get(oa).countItem(DIRT)==80&&organize.world.containers.get(ob).countItem(DIRT)==0,"bounded organization consolidates matching authorized stock");
 var interrupted=create("organize-cancel",0);var ia=chest(interrupted,0,false);var ib=chest(interrupted,1,false);interrupted.world.containers.get(ia).setItem(0,new ItemStack(DIRT,1));interrupted.world.containers.get(ib).setItem(0,new ItemStack(DIRT,32));var cancelSort=new WarehouseOrganizeTask();for(int i=0;i<100&&interrupted.getItemStorage().getItemCountInventoryOnly(DIRT)==0;i++){interrupted.world.time++;cancelSort.step(interrupted);}cancelSort.stop();check(interrupted.getItemStorage().getItemCountInventoryOnly(DIRT)==32,"cancelled organization retains withdrawn stock");
 var outcome=create("outcome",1);var op=chest(outcome,0,false);var deposit=new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.DEPOSIT,op,List.of(new StorageItemArgs.ItemQuery(DIRT,"minecraft:dirt",64)));for(int i=0;i<40;i++)deposit.step(outcome);check(deposit.outcome()==BoundedContainerTransferTask.Outcome.MOVED_ALL&&deposit.entryOutcomes().get(0).moved()==64,"deposit reports verified moved amount instead of zero");
 var changed=create("changed",36);var ca=chest(changed,0,false);var cb=chest(changed,1,false);var changing=root(changed);Task chosen=AutoInventoryCleanup.beforeResourceTick(changing);var changedChest=changed.world.containers.get(ca);for(int i=0;i<54;i++)changedChest.setItem(i,new ItemStack(GOLD,64));chosen.step(changed);check(advance(changing,160)==null&&changed.world.containers.get(cb).countItem(DIRT)==2304,"failed transfer retries a different available chest and resumes");
 var stockMod=create("live-withdraw",0);
 var stockA=chest(stockMod,0,false);var stockB=chest(stockMod,1,false);
 for(int i=0;i<16;i++)stockMod.world.containers.get(stockA).setItem(i,new ItemStack(STONE,64));
 stockMod.world.containers.get(stockB).setItem(0,new ItemStack(WOOD,7));
 var stockSession=new WarehouseWithdrawal();
 var stockTargets=List.of(new ItemTarget(WOOD,7),new ItemTarget(STONE,1100));
 check(WarehouseWithdrawal.hasStock(stockMod,new ItemTarget(STONE,1100)),"live warehouse has bulk stock without a prior inventory scan");
 for(int i=0;i<180;i++){stockMod.world.time++;var withdrawal=stockSession.next(stockMod,stockTargets);if(withdrawal!=null)withdrawal.step(stockMod);}
 check(stockMod.getItemStorage().getItemCountInventoryOnly(WOOD)==7&&stockMod.getItemStorage().getItemCountInventoryOnly(STONE)==1024,"withdrawals use actual stock and leave only the shortage to native acquisition");
 stockMod.world.containers.get(stockA).setItem(0,new ItemStack(STONE,64));stockMod.world.protectedBlocks.add(stockA);
 check(!WarehouseWithdrawal.hasStock(stockMod,new ItemTarget(STONE,1100)),"withdrawal skips chests protected after registration");
 var revoke=create("revoke-withdraw",0);var revokePos=chest(revoke,0,false);
 revoke.world.containers.get(revokePos).setItem(0,new ItemStack(STONE,64));
 var revocation=new WarehouseWithdrawal();var revocationTargets=List.of(new ItemTarget(STONE,64));
 var queued=revocation.next(revoke,revocationTargets);check(queued!=null,"live stock schedules a bounded withdrawal");
 var revokeLedger=WarehouseManager.ledger(revoke);revokeLedger.set("enabled",false);revokeLedger.save();
 check(revocation.next(revoke,revocationTargets)==null&&revoke.getItemStorage().getItemCountInventoryOnly(STONE)==0,"revoked warehouse cancels pending withdrawal");
 var replacement=create("replacement",1);var rp=chest(replacement,0,false);var staleTransfer=new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.DEPOSIT,rp,List.of(new StorageItemArgs.ItemQuery(DIRT,"minecraft:dirt",64)));staleTransfer.step(replacement);Chest swapped=new Chest();replacement.world.containers.put(rp,swapped);replacement.world.containers.put(rp.offset(1,0,0),swapped);staleTransfer.step(replacement);check(staleTransfer.isFinished()&&staleTransfer.outcome()==BoundedContainerTransferTask.Outcome.FAILED&&replacement.getItemStorage().getItemCountInventoryOnly(DIRT)==64,"container replacement during open invalidates transfer without item loss");
 var file=DeferredJobStore.root.resolve("restart.properties");var before=new WarehouseLedger(file);before.set("origin","1,2,3");before.set("requested",8);before.set("placed.1,2,3",true);before.set("snapshot.1,3,3","dirt=64");before.save();var after=new WarehouseLedger(file);check(after.number("requested",0)==8&&after.get("placed.1,2,3","").equals("true")&&after.get("snapshot.1,3,3","").equals("dirt=64"),"restart restores construction and inventory metadata");
 boolean rejected=false;Files.writeString(file,"schema=99\\norigin=1,2,3\\n");try{new WarehouseLedger(file);}catch(java.io.IOException e){rejected=true;}check(rejected,"unsupported persistence schema fails closed");
 var build=create("build",0);build.world.blocks.clear();build.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,56));build.inventory.main.set(1,new ItemStack(net.minecraft.world.item.Items.CHEST,8));var builder=new WarehouseBuildTask();for(int i=0;i<200&&!builder.isFinished();i++){build.world.time++;builder.step(build);}check(builder.isSuccessful()&&build.baritone.builder.placed==64&&build.behaviour.depth==0,"warehouse builder completes supported module and restores behavior");
 var initialStock=WarehouseManager.at(WarehouseManager.ledger(build),WarehouseLayout.chests(4).get(0).first());build.world.containers.get(initialStock).setItem(0,new ItemStack(DIRT,12));var expanded=WarehouseManager.ledger(build);expanded.set("requested",8);expanded.set("state","REQUESTED");expanded.save();build.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,56));build.inventory.main.set(1,new ItemStack(net.minecraft.world.item.Items.CHEST,8));var extension=new WarehouseBuildTask();for(int i=0;i<200&&!extension.isFinished();i++){build.world.time++;extension.step(build);}check(extension.isSuccessful()&&build.world.containers.get(initialStock).countItem(DIRT)==12&&build.baritone.builder.placed==128,"construction expands four to eight without changing stored items");
 var unsafe=create("unsupported",0);unsafe.world.blocks.clear();for(int y=-1;y>=-13;y--)unsafe.world.blocks.put(new BlockPos(3,y,3),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());var unsafeBuild=new WarehouseBuildTask();unsafeBuild.step(unsafe);check(unsafeBuild.isTerminal()&&!unsafeBuild.isSuccessful()&&unsafe.baritone.builder.placed==0,"foundation beyond depth limit fails before construction");
 check(WarehouseLayout.expandedCount(4,8)==12,"expand 8 adds eight double chests across two modules");
 boolean excess=false;try{WarehouseLayout.expandedCount(28,8);}catch(IllegalArgumentException expected){excess=true;}check(excess,"expansion rejects requests over the 32-double-chest cap");
 var deep=WarehouseManager.ledger(build);deep.set("requested",WarehouseLayout.expandedCount(8,8));deep.set("state","REQUESTED");deep.save();
 for(int y=-1;y>=-11;y--)build.world.blocks.put(new BlockPos(3,y,16),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
 var deepExtension=new WarehouseBuildTask();for(int i=0;i<420&&!deepExtension.isFinished();i++){build.world.time++;deepExtension.step(build);}
 check(deepExtension.isSuccessful()&&build.baritone.builder.placed==267&&build.world.containers.get(initialStock).countItem(DIRT)==12,"two-module expansion fills an eleven-block gap and preserves existing stock");
 check(build.baritone.builder.placementOrder.subList(128,139).equals(java.util.stream.IntStream.rangeClosed(-11,-1).mapToObj(y->new BlockPos(3,y,16)).toList()),"deep foundation is placed from solid ground upwards");
 var zone=create("zone-build",0);zone.world.blocks.clear();zone.world.protectedBlocks.add(new BlockPos(3,0,3));var zoneBuild=new WarehouseBuildTask();zoneBuild.step(zone);check(zoneBuild.isTerminal()&&!zoneBuild.isSuccessful()&&zone.baritone.builder.placed==0,"Safe Zone obstruction fails before construction");
 var interruptedBuild=create("build-interrupt",0);interruptedBuild.world.blocks.clear();interruptedBuild.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,56));interruptedBuild.inventory.main.set(1,new ItemStack(net.minecraft.world.item.Items.CHEST,8));var interruptTask=new WarehouseBuildTask();interruptTask.step(interruptedBuild);interruptTask.stop();check(WarehouseManager.ledger(interruptedBuild).get("state","").equals("BLOCKED")&&interruptedBuild.behaviour.depth==0,"interrupted construction saves progress and restores policy");var resumed=new WarehouseBuildTask();for(int i=0;i<200&&!resumed.isFinished();i++){interruptedBuild.world.time++;resumed.step(interruptedBuild);}check(resumed.isSuccessful()&&interruptedBuild.baritone.builder.placed==64,"resumed construction does not rebuild completed cells");
 var material=create("no-wood",0);material.world.blocks.clear();material.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,56));com.player2.playerengine.TaskCatalogue.failMaterials=true;var missingWood=new WarehouseBuildTask();for(int i=0;i<1300&&!missingWood.isFinished();i++){material.world.time++;missingWood.step(material);}com.player2.playerengine.TaskCatalogue.failMaterials=false;check(missingWood.isTerminal()&&!missingWood.isSuccessful()&&material.baritone.builder.placed==0,"unavailable chest materials stop after bounded no-progress interval");
 var foreign=create("foreign-builder",0);foreign.world.blocks.clear();foreign.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,56));foreign.inventory.main.set(1,new ItemStack(net.minecraft.world.item.Items.CHEST,8));var own=new WarehouseBuildTask();own.step(foreign);foreign.baritone.builder.schematic=new Object();int cancelled=foreign.baritone.builder.cancelled;own.stop();check(foreign.baritone.builder.cancelled==cancelled&&foreign.baritone.builder.active,"warehouse cancellation does not cancel unrelated builder ownership");
 var halfWorld=create("half-chest",0);var halfPlan=WarehouseManager.ledger(halfWorld);for(int i=0;i<2;i++){var completedPair=chest(halfWorld,i,false);for(var pos:List.of(completedPair,completedPair.offset(1,0,0)))halfPlan.set("placed."+WarehouseManager.key(pos),true);}
 var northPair=WarehouseLayout.chests(4).get(2);var half=WarehouseManager.at(halfPlan,northPair.first());var otherHalf=WarehouseManager.at(halfPlan,northPair.second());
 var savedHalf=net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,Direction.SOUTH);var savedStock=new Chest();savedStock.setItem(0,new ItemStack(DIRT,5));halfWorld.world.blocks.put(half,savedHalf);halfWorld.world.containers.put(half,savedStock);halfPlan.set("placed."+WarehouseManager.key(half),true);halfPlan.set("state","BLOCKED");halfPlan.save();halfWorld.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.CHEST,3));
 var repair=new WarehouseBuildTask();for(int i=0;i<200&&!repair.isFinished();i++){halfWorld.world.time++;repair.step(halfWorld);}check(repair.isSuccessful()&&halfWorld.baritone.builder.placed==3&&halfWorld.world.getBlockState(half)==savedHalf&&halfWorld.world.getBlockState(otherHalf).facing==Direction.SOUTH&&halfWorld.world.containers.get(otherHalf)==savedStock&&savedStock.countItem(DIRT)==5,"resume joins an opposite-facing saved half and preserves both completed pairs and stored items");
 var rough=create("rough-ground",0);rough.world.blocks.clear();var soil=new BlockPos(2,0,2);var hole=new BlockPos(3,-1,3);rough.world.blocks.put(soil,net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());rough.world.blocks.put(hole,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
 var roughBuild=new WarehouseBuildTask();roughBuild.step(rough);var footprint=new BlockPos(7,-4,6);check(!com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard.allowResourceMining(rough,footprint),"shared village guard excludes mining under warehouse");roughBuild.pauseForGear();check(!roughBuild.isTerminal()&&rough.behaviour.depth==0,"gear pause preserves project and unwinds behavior");
 for(int i=0;i<250&&!roughBuild.isFinished();i++){rough.world.time++;roughBuild.step(rough);}check(roughBuild.isSuccessful()&&rough.baritone.builder.cleared==1&&rough.baritone.builder.placed==65&&rough.behaviour.depth==0,"resumed warehouse crafts tools, clears soil, fills a gap and completes construction");
 check(rough.getItemStorage().getItemCountInventoryOnly(net.minecraft.world.item.Items.WOODEN_AXE,net.minecraft.world.item.Items.WOODEN_PICKAXE,net.minecraft.world.item.Items.WOODEN_SHOVEL)==3,"tool prerequisites craft once before gathering and excavation");
 check(com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard.allowResourceMining(rough,footprint),"completion releases temporary mining exclusion");
 var draftTest=new PlayerEngineController();draftTest.identity="draft";var previous=WarehouseManager.ledger(draftTest);previous.set("origin","0,0,0");previous.set("state","BLOCKED");var proposed=previous.draft();proposed.set("origin","3,0,3");check(previous.canChooseSite()&&previous.get("origin","").equals("0,0,0"),"candidate site stays detached before validation");proposed.set("terrainStarted",true);previous.commit(proposed);check(!previous.canChooseSite()&&previous.get("origin","").equals("3,0,3"),"terrain receipts prevent relocation after site work starts");
 var dual=new PlayerEngineController();dual.identity="dual";
 var legacyKey=java.util.UUID.nameUUIDFromBytes(dual.identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
 var legacyFile=DeferredJobStore.root.resolve("ellie-warehouses").resolve(legacyKey+".properties");
 var oldHome=new WarehouseLedger(legacyFile);oldHome.set("origin","0,0,0");oldHome.set("dimension","minecraft:overworld");oldHome.set("enabled",true);oldHome.set("state","READY");oldHome.set("reserve",7);oldHome.save();
 check(WarehouseManager.ledger(dual).number("reserve",0)==7,"old single-file warehouse opens in its saved dimension");
 var nether=new PlayerEngineController();nether.identity=dual.identity;nether.world.dimensionId="minecraft:the_nether";
 var bunker=WarehouseManager.ledger(nether);check(!bunker.exists(),"legacy Overworld warehouse does not occupy Nether slot");
 bunker.set("origin","0,0,0");bunker.set("dimension",nether.world.dimensionId);bunker.set("style","nether_bunker");bunker.set("enabled",true);bunker.set("requested",4);bunker.save();
 check(WarehouseManager.ledger(dual).get("style","open").equals("open")&&WarehouseManager.ledger(nether).canChooseSite(),"same coordinates use independent ledgers");
 nether.world.blocks.put(new BlockPos(2,0,2),net.minecraft.world.level.block.Blocks.NETHERRACK.defaultBlockState());
 var roots=new BlockPos(3,-1,2);var stem=new BlockPos(4,-1,2);var lowerStem=new BlockPos(4,-2,2);
 nether.world.blocks.put(roots,net.minecraft.world.level.block.Blocks.CRIMSON_FUNGUS.defaultBlockState());
 nether.world.blocks.put(stem,net.minecraft.world.level.block.Blocks.CRIMSON_STEM.defaultBlockState());
 nether.world.blocks.put(lowerStem,net.minecraft.world.level.block.Blocks.CRIMSON_STEM.defaultBlockState());
 nether.world.blocks.put(new BlockPos(4,1,2),net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK.defaultBlockState());
 nether.world.protectedBlocks.add(stem);
 try{WarehouseBuildTask.validate(nether,bunker);throw new AssertionError("protected foundation stem was accepted");}
 catch(java.io.IOException expected){check(expected.getMessage().contains("protection"),"protected foundation stems remain off limits");}
 nether.world.protectedBlocks.remove(stem);
 nether.inventory.main.set(0,new ItemStack(net.minecraft.world.item.Items.BLACKSTONE,4));
 nether.inventory.main.set(1,new ItemStack(net.minecraft.world.item.Items.NETHER_BRICKS,64));
 var nylium=new BlockPos(5,0,2);var userPlanks=new BlockPos(6,0,2);var outside=new BlockPos(9,0,2);
 nether.world.blocks.put(nylium,net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM.defaultBlockState());
 nether.world.blocks.put(userPlanks,net.minecraft.world.level.block.Blocks.PLANKS.defaultBlockState());
 nether.world.blocks.put(outside,net.minecraft.world.level.block.Blocks.PLANKS.defaultBlockState());
 com.player2.playerengine.structureprotection.PlayerPlacedBlockStore.placed.add(userPlanks);
 var bedrock=new BlockPos(5,2,2);nether.world.blocks.put(bedrock,net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
 try{WarehouseBuildTask.validate(nether,bunker);throw new AssertionError("unbreakable block was accepted");}
 catch(java.io.IOException expected){check(expected.getMessage().contains("Unbreakable"),"survival-unbreakable blocks are reported during preflight");}
 nether.world.blocks.remove(bedrock);
 WarehouseBuildTask.validate(nether,bunker);
 var buildBunker=new WarehouseBuildTask();
 for(int i=0;i<1800&&!buildBunker.isFinished();i++){nether.world.time++;buildBunker.step(nether);}
 check(buildBunker.isSuccessful()&&nether.baritone.builder.cleared==7,"Nether bunker clears nylium and player-placed planks as well as vegetation");
 check(nether.world.getBlockState(outside).is(net.minecraft.world.level.block.Blocks.PLANKS),"clearance stays within the warehouse footprint");
 com.player2.playerengine.structureprotection.PlayerPlacedBlockStore.placed.clear();
 check(WarehouseManager.isStructure(bunker,nether.world.getBlockState(roots))&&WarehouseManager.isStructure(bunker,nether.world.getBlockState(lowerStem)),"cleared foundation columns are filled from solid ground");
 var builtFloor=WarehouseLayout.floor(4,true).stream().map(cell->nether.world.getBlockState(WarehouseManager.at(bunker,cell))).toList();
 check(builtFloor.stream().anyMatch(state->state.is(net.minecraft.world.level.block.Blocks.NETHER_BRICKS))
     &&builtFloor.stream().anyMatch(state->state.is(net.minecraft.world.level.block.Blocks.BLACKSTONE)),"either carried structure block can fill a bunker cell, including in a mixed floor");
 check(WarehouseLayout.shell(4,true).stream().allMatch(cell->WarehouseManager.isStructure(bunker,nether.world.getBlockState(WarehouseManager.at(bunker,cell)))),"every Nether bunker wall, roof and exterior step is completed");
 check(nether.world.getBlockState(new BlockPos(-3,1,3)).isAir()&&nether.world.getBlockState(new BlockPos(-1,1,0)).isAir()
     &&nether.world.getBlockState(new BlockPos(-2,1,1)).is(net.minecraft.world.level.block.Blocks.BLACKSTONE),"covered dogleg entrance stays open at both ends");
 BlockPos stocked=WarehouseManager.at(bunker,WarehouseLayout.chests(4).getFirst().first());
 nether.world.containers.get(stocked).setItem(0,new ItemStack(DIRT,12));
 bunker.set("requested",8);bunker.set("state","REQUESTED");bunker.save();
 var extendBunker=new WarehouseBuildTask();
 for(int i=0;i<1800&&!extendBunker.isFinished();i++){nether.world.time++;extendBunker.step(nether);}
 check(extendBunker.isSuccessful()&&nether.world.getBlockState(new BlockPos(3,1,7)).isAir()
     &&nether.world.getBlockState(new BlockPos(3,1,14)).is(net.minecraft.world.level.block.Blocks.BLACKSTONE)
     &&nether.world.containers.get(stocked).countItem(DIRT)==12,"Nether expansion moves only its old end wall and preserves original storage");
 check(WarehouseManager.ledger(dual).number("requested",4)==4&&new WarehouseLedger(legacyFile).get("state","").equals("READY"),"Nether expansion does not mutate the legacy Overworld plan");
 for(int i=0;i<36;i++)nether.inventory.main.set(i,new ItemStack(DIRT,64));
 var recoverNether=root(nether);advance(recoverNether,140);
 check(nether.world.containers.get(stocked).countItem(DIRT)>12&&InventorySpace.inspect(nether).free()>0,"full Nether producer unloads to the local warehouse");
 if(System.getProperty("os.name").startsWith("Windows")){
  var locked=create("locked-journal",0);var journal=WarehouseManager.ledger(locked);
  var identity=java.util.UUID.nameUUIDFromBytes(locked.identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var dimensionKey=java.util.UUID.nameUUIDFromBytes("minecraft:overworld".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var journalFile=DeferredJobStore.root.resolve("ellie-warehouses").resolve(identity+"-"+dimensionKey+".properties");
  try(var handle=java.nio.channels.FileChannel.open(journalFile,StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)){
   journal.set("requested",8);boolean failedWrite=false;try{journal.save();}catch(java.io.IOException expected){failedWrite=true;}
   check(failedWrite&&journal.fault()!=null&&new WarehouseLedger(journalFile).number("requested",0)==4,"locked Windows journal retains last committed plan");
  }
  var reloaded=WarehouseManager.ledgerForResume(locked);check(reloaded!=journal&&reloaded.fault()==null&&reloaded.number("requested",0)==4,"resume reloads disk receipts after the Windows lock is released");
  try(var handle=java.nio.channels.FileChannel.open(journalFile,StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)){
   var unlock=Thread.startVirtualThread(()->{try{Thread.sleep(35);handle.close();}catch(Exception failure){throw new IllegalStateException(failure);}});
   reloaded.set("requested",8);reloaded.save();unlock.join();
   check(new WarehouseLedger(journalFile).number("requested",0)==8,"atomic journal replacement retries a short Windows file lock");
  }
 }
 System.out.println("WarehouseTests: "+tests+" checks passed (in-memory fixtures; no gameplay)");
 }
}
'''
if __name__ == '__main__':
    with tempfile.TemporaryDirectory(prefix='ellie-warehouse-tests-') as tmp:
        tmp=Path(tmp); files=[]
        for rel,text in STUBS.items():
            p=tmp/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);files.append(str(p))
        p=tmp/'warehousefixture/WarehouseTests.java';p.parent.mkdir();p.write_text(HARNESS);files.append(str(p))
        actual=['warehouse/WarehouseLayout.java','warehouse/WarehouseLedger.java','warehouse/WarehousePolicy.java','warehouse/WarehouseManager.java','warehouse/WarehouseWithdrawal.java','agentic/AutoInventoryCleanup.java','agentic/InventorySpace.java','agentic/MaterialReservationService.java','tasks/container/BoundedContainerTransferTask.java','tasks/base/TrackedTaskOutcomeProvider.java','tasks/base/TaskSuspensionCause.java','tasks/base/TransientlyResumableTask.java','tasks/construction/ConstructionTools.java','tasks/construction/GroundedFoundation.java','tasks/construction/build_structure/VillageSiteGuard.java','tasks/setup/SetupBuildZone.java','warehouse/WarehouseSite.java','warehouse/WarehouseOrganizeTask.java','warehouse/WarehouseBuildTask.java']
        files += [str(SOURCE/'com/player2/playerengine'/p) for p in actual]
        classes=tmp/'classes';classes.mkdir()
        subprocess.run(['javac','--release','21','-d',str(classes),*files],check=True)
        subprocess.run(['java','-ea','-cp',str(classes),'warehousefixture.WarehouseTests'],check=True)
