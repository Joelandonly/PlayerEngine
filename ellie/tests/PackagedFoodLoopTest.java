import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.*;

/** Executes packaged native food/smoker/furnace methods. Combat, acquisition, world ticks and physics
 * are explicit test boundaries; this is not a Minecraft gameplay test. */
public final class PackagedFoodLoopTest {
    static final String P="com/player2/playerengine/";
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}

    public interface Outcome {boolean isTerminal();boolean isSuccessful();String controlledReason();}
    public interface SurvivalInterrupt {}
    public static final class CommandException extends Exception {public CommandException(String message){super(message);}}
    public interface InventoryProvider {Inventory getLivingInventory();}
    public interface Component {
        String getString();
        static Text translatable(String key,Object...args){return new Text(key+Arrays.toString(args));}
    }
    public record Text(String value) implements Component {public String getString(){return value;}}
    public interface ItemLike {}
    public static final class Item implements ItemLike {
        final String id;Item(String id){this.id=id;}
        public Component getDescription(){return new Text(id);}
        public String getDescriptionId(){return id;}
        public String toString(){return id;}
    }
    static final Map<String,Item> ITEMS=new HashMap<>();
    static Item item(String id){return ITEMS.computeIfAbsent(id,Item::new);}
    public static final class Items {
        public static final Item ENCHANTED_GOLDEN_APPLE=item("enchanted_golden_apple"),GOLDEN_APPLE=item("golden_apple"),
                GOLDEN_CARROT=item("golden_carrot"),BREAD=item("bread"),BAKED_POTATO=item("baked_potato"),
                HAY_BLOCK=item("hay_block"),SWEET_BERRIES=item("sweet_berries"),WHEAT=item("wheat"),CARROT=item("carrot"),
                SMOKER=item("smoker"),FURNACE=item("furnace"),COAL=item("coal"),CHARCOAL=item("charcoal"),LAVA_BUCKET=item("lava_bucket");
    }
    public static class Stack {
        public static final Stack EMPTY=new Stack(item("air"),0);
        Item item;int count;
        public Stack(Item item){this(item,1);}public Stack(Item item,int count){this.item=item;this.count=count;}
        public Stack(ItemLike item){this((Item)item,1);}
        public boolean isEmpty(){return count<=0;}public Item getItem(){return item;}public int getCount(){return count;}
        public void setCount(int n){count=n;}public int getMaxStackSize(){return 64;}
        public Stack copy(){return new Stack(item,count);}public void grow(int n){count+=n;}public void shrink(int n){count=Math.max(0,count-n);}
    }
    public static final class Inventory {
        public final List<Stack> main=new ArrayList<>(Collections.nCopies(36,Stack.EMPTY));
        public int getContainerSize(){return main.size();}public Stack getItem(int i){return main.get(i);}
        public void setItem(int i,Stack stack){main.set(i,stack);}
        public int getSlotWithStack(Stack stack){for(int i=0;i<36;i++)if(main.get(i).item==stack.item&&!main.get(i).isEmpty())return i;return -1;}
        public int findSlotMatchingItem(Stack stack){return getSlotWithStack(stack);}
        public Stack removeItem(int slot,int count){Stack old=main.get(slot);int n=Math.min(count,old.count);Stack out=new Stack(old.item,n);old.shrink(n);if(old.isEmpty())main.set(slot,Stack.EMPTY);return out;}
        public boolean insertStack(Stack incoming){
            for(int i=0;i<36;i++){Stack s=main.get(i);if(s.item==incoming.item&&s.count<64&&!s.isEmpty()){int n=Math.min(64-s.count,incoming.count);s.grow(n);incoming.shrink(n);}}
            for(int i=0;i<36&&!incoming.isEmpty();i++)if(main.get(i).isEmpty()){int n=Math.min(64,incoming.count);main.set(i,new Stack(incoming.item,n));incoming.shrink(n);}
            return incoming.isEmpty();
        }
        int count(Item i){return main.stream().filter(s->s.item==i).mapToInt(s->s.count).sum();}
        void add(String id,int n){if(!insertStack(new Stack(item(id),n)))throw new AssertionError("test inventory full");}
    }
    public static class VecPos {
        final int x,y,z;public VecPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public boolean closerThan(VecPos other,double range){return Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2)<range*range;}
    }
    public static class Pos extends VecPos {
        public Pos(int x,int y,int z){super(x,y,z);}public Pos above(){return new Pos(x,y+1,z);}public Pos immutable(){return this;}
        public String toShortString(){return x+","+y+","+z;}
    }
    public static final class Vec {
        public final double x,y,z;public Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public double distanceToSqr(Vec v){return Math.pow(x-v.x,2)+Math.pow(y-v.y,2)+Math.pow(z-v.z,2);}
    }
    public static class EntityType {public Component getDescription(){return new Text("animal");}}
    public static class Entity {
        public Vec position(){return new Vec(3,64,0);}public EntityType getType(){return new EntityType();}
        public boolean isAlive(){return true;}public boolean isVehicle(){return false;}
        public float distanceTo(Entity e){return (float)Math.sqrt(position().distanceToSqr(e.position()));}
    }
    public static class Living extends Entity implements InventoryProvider {
        public final Inventory inventory=new Inventory();public Inventory getLivingInventory(){return inventory;}
        public boolean isBaby(){return false;}public double getX(){return 0;}public double getY(){return 64;}public double getZ(){return 0;}
        public Vec position(){return new Vec(0,64,0);}
    }
    public static final class Chicken extends Living {public Vec position(){return new Vec(3,64,0);}}
    public static final class Cow extends Living {}
    public static final class Pig extends Living {}
    public static final class Sheep extends Living {}
    public static final class Rabbit extends Living {}
    public static final class DroppedItem extends Entity {public Stack getItem(){return Stack.EMPTY;}}
    public static class Block {final String id;Block(String id){this.id=id;}public String toString(){return id;}}
    public static final class Crop extends Block {Crop(){super("crop");}public boolean isMaxAge(BlockState s){return true;}}
    public static final class Blocks {
        public static final Block AIR=new Block("air"),SMOKER=new Block("smoker"),FURNACE=new Block("furnace"),
                HAY_BLOCK=new Block("hay_block"),CARVED_PUMPKIN=new Block("pumpkin"),WHEAT=new Crop(),CARROTS=new Crop(),SWEET_BERRY_BUSH=new Block("berries");
    }
    public static final class BlockState {
        final Block block;BlockState(Block block){this.block=block;}public Block getBlock(){return block;}public boolean is(Block other){return block==other;}
    }
    public static class BlockEntity {}
    public static final class Furnace extends BlockEntity {
        public int litTime;public int cook;public Stack[] slots={Stack.EMPTY,Stack.EMPTY,Stack.EMPTY};
        public Stack getItem(int i){return slots[i];}public void setItem(int i,Stack s){slots[i]=s;}public void setChanged(){}
        public static Map<Item,Integer> getFuel(){return Map.of(Items.COAL,1600,Items.CHARCOAL,1600,item("oak_planks"),300,item("oak_log"),300);}
    }
    public static final class World {
        int ticks,animals=30,kills,acquired,stationPlacements,cooked;
        boolean fuelAccessible=true,coalAccessible=true,woodAccessible=true,placementBlocked,sourceAnimals=true;
        Block station=Blocks.AIR;final Pos pos=new Pos(1,64,0);final Furnace furnace=new Furnace();
        public BlockEntity getBlockEntity(Pos pos){return station==Blocks.AIR?null:furnace;}
        public BlockState getBlockState(Pos pos){return new BlockState(station);}
        public long getGameTime(){return ticks;}
        public boolean isLoaded(Pos pos){return true;}
        void tick(){
            ticks++;
            if(furnace.litTime>0)furnace.litTime--;
            if(station==Blocks.AIR||furnace.slots[0].isEmpty())return;
            if(furnace.litTime<=0&&!furnace.slots[1].isEmpty()){
                furnace.litTime=Furnace.getFuel().getOrDefault(furnace.slots[1].item,0);furnace.slots[1].shrink(1);
            }
            if(furnace.litTime<=0)return;
            if(++furnace.cook<(station==Blocks.SMOKER?100:200))return;
            furnace.cook=0;Item out=item("cooked_"+furnace.slots[0].item.id);
            if(furnace.slots[2].isEmpty())furnace.slots[2]=new Stack(out,1);else furnace.slots[2].grow(1);
            furnace.slots[0].shrink(1);cooked++;
        }
    }
    public static final class Behaviour {
        int depth;public void push(){depth++;}public BehaviourState pop(){if(--depth<0)throw new AssertionError("unbalanced behaviour stack");return new BehaviourState();}
        public void addProtectedItems(Item...items){}
    }
    public static final class BehaviourState {}
    public static final class Storage {
        final Controller c;Storage(Controller c){this.c=c;}
        public int getItemCount(Item...items){int total=0;for(Item item:items)total+=c.entity.inventory.count(item);return total;}
        public int getItemCount(Target...targets){int total=0;for(Target t:targets)total+=getItemCount(t.items);return total;}
        public int getItemCountInventoryOnly(Item...items){return getItemCount(items);}
        public boolean hasItem(Item...items){return getItemCount(items)>0;}public boolean hasItem(Target...targets){for(Target t:targets)if(getItemCount(t)<t.count)return false;return true;}
        public List<Stack> getItemStacksPlayerInventory(boolean cursor){return c.entity.inventory.main;}
        public void registerSlotAction(){}
    }
    public static final class Scanner {
        final Controller c;Scanner(Controller c){this.c=c;}
        public Optional<Pos> getNearestBlock(Block...blocks){return Arrays.asList(blocks).contains(c.world.station)?Optional.of(c.world.pos):Optional.empty();}
        public boolean anyFound(Block...blocks){return false;}
        public boolean anyFound(Predicate<Pos> predicate,Block...blocks){return false;}
        public List<Pos> getKnownLocations(Block...blocks){return List.of();}
        public void requestBlockUnreachable(Pos p,int reason){}
    }
    public static final class Tracker {
        final Controller c;Tracker(Controller c){this.c=c;}
        public List<Entity> getTrackedEntities(Class<?> type){return List.of();}
        public boolean itemDropped(Item...items){return false;}
        public boolean entityFound(Class<?>...types){return c.world.sourceAnimals&&c.world.animals>0&&Arrays.asList(types).contains(Chicken.class);}
        public Optional<Entity> getClosestEntity(Vec pos,Predicate<Entity> predicate,Class<?>...types){return entityFound(types)?Optional.of(new Chicken()):Optional.empty();}
        public Optional<DroppedItem> getClosestItemDrop(Vec pos,Item...items){return Optional.empty();}
        public void requestEntityUnreachable(Entity entity){}
    }
    public static final class Settings {public int getWanderBoundDefaultSeconds(){return 1;}}
    public static final class Hunger {int level=20;public int getFoodLevel(){return level;}}
    public static final class Context {final Hunger hunger=new Hunger();public Context getEntityContext(){return this;}public Hunger hungerManager(){return hunger;}}
    public static final class UserChain {public Task mainTask;}
    public static final class Controller {
        public final World world=new World();public final Living entity=new Living();public final Behaviour behaviour=new Behaviour();
        public final Storage storage=new Storage(this);public final Scanner scanner=new Scanner(this);public final Tracker tracker=new Tracker(this);
        public final UserChain user=new UserChain();public final Context context=new Context();
        public Living getEntity(){return entity;}public Living getPlayer(){return entity;}public World getWorld(){return world;}
        public Storage getItemStorage(){return storage;}public Scanner getBlockScanner(){return scanner;}public Tracker getEntityTracker(){return tracker;}
        public Behaviour getBehaviour(){return behaviour;}public Settings getModSettings(){return new Settings();}
        public UserChain getUserTaskChain(){return user;}public Context getBaritone(){return context;}
        public void log(String message){}public void reportAgenticProgress(String message,boolean milestone){}
        public void reportAgenticProgress(Component message,boolean milestone){}
    }
    public static class Task {
        public Controller controller;public Task sub;boolean active,stopped;
        protected void onStart(){}protected Task onTick(){return null;}protected void onStop(Task next){}
        protected boolean isEqual(Task task){return getClass()==(task==null?null:task.getClass());}
        protected String toDebugString(){return getClass().getSimpleName();}
        protected void setDebugState(String state){}
        public boolean isFinished(){return false;}public boolean isActive(){return active&&!stopped;}public boolean stopped(){return stopped;}
        public boolean thisOrChildAreTimedOut(){return sub instanceof Wander;}
        public boolean thisOrChildSatisfies(Predicate<Task> predicate){for(Task t=this;t!=null;t=t.sub)if(predicate.test(t))return true;return false;}
        public String getTaskTree(){return toString()+(sub==null?"":" -> "+sub.getTaskTree());}
        public void reset(){active=false;stopped=false;}
        public void stop(){stop(null);}public void stop(Task next){if(stopped)return;if(active)onStop(next);if(sub!=null)sub.stop(next);active=false;stopped=true;}
        void tick(){
            if(!active){active=true;stopped=false;onStart();}
            if(stopped)return;Task proposed=onTick();
            if(proposed!=null){if(sub==null||!proposed.isEqual(sub)){if(sub!=null)sub.stop(proposed);sub=proposed;}sub.controller=controller;sub.tick();}
            else if(sub!=null){sub.stop();sub=null;}
        }
    }
    public static class Resource extends Task {
        protected Target[] itemTargets;String failure;
        public Resource(Target...targets){itemTargets=targets;}
        protected void onResourceStart(Controller controller){}protected Task onResourceTick(Controller controller){return null;}
        protected void onResourceStop(Controller controller,Task next){}protected boolean shouldAvoidPickingUp(Controller controller){return false;}
        protected String toDebugStringName(){return getClass().getSimpleName();}
        protected boolean isEqualResource(Resource resource){return Arrays.equals(itemTargets,resource.itemTargets);}
        protected boolean isEqual(Task task){return task instanceof Resource r&&getClass()==task.getClass()&&isEqualResource(r);}
        protected void onStart(){controller.behaviour.push();onResourceStart(controller);}
        protected Task onTick(){return isFinished()?null:onResourceTick(controller);}
        protected void onStop(Task next){onResourceStop(controller,next);controller.behaviour.pop();}
        public boolean isFinished(){if(controller==null)return false;for(Target t:itemTargets)if(controller.storage.getItemCount(t)<t.count)return false;return true;}
        protected void recordFailureReason(String reason){failure=reason;}
        public Optional<String> getAggregatedFailureReason(){return Optional.ofNullable(failure);}
    }
    public static final class Target {
        final Item[] items;final int count;
        public Target(Item item,int count){this(new Item[]{item},count);}public Target(Item...items){this(items,1);}
        public Target(Item[] items,int count){this.items=items;this.count=count;}
        public Item[] getMatches(){return items;}public int getTargetCount(){return count;}
        public boolean equals(Object o){return o instanceof Target t&&count==t.count&&Arrays.equals(items,t.items);}public int hashCode(){return count;}
    }
    public static final class SmeltTarget {
        final Target output,input;
        public SmeltTarget(Target output,Target input,Item...optional){this.output=output;this.input=input;}
        public Target getItem(){return output;}public Target getMaterial(){return input;}
        public boolean equals(Object o){return o instanceof SmeltTarget t&&output.equals(t.output)&&input.equals(t.input);}public int hashCode(){return output.hashCode();}
    }
    public static final class Food {final int hunger;Food(int hunger){this.hunger=hunger;}public int getHunger(){return hunger;}}
    public static final class ItemVer {
        public static Food getFoodComponent(Item item){return item.id.equals("chicken")?new Food(2):item.id.equals("cooked_beef")||item.id.equals("cooked_porkchop")?new Food(8):item.id.startsWith("cooked_")?new Food(6):item.id.equals("bread")?new Food(5):null;}
        public static boolean isFood(Item i){return getFoodComponent(i)!=null;}public static boolean isFood(Stack s){return isFood(s.item);}
    }
    public enum Tier {HAND,WOOD,STONE,IRON,DIAMOND}
    public static final class ItemHelper {
        public static final Item[] PLANKS={item("oak_planks")},LOG={item("oak_log")};
        public static boolean isFuel(Item item){return Furnace.getFuel().containsKey(item);}
    }
    public static final class StorageHelper {
        public static int calculateInventoryFoodScore(Controller c){int sum=0;for(Stack s:c.entity.inventory.main){Food f=ItemVer.getFoodComponent(s.item);if(f!=null)sum+=s.count*f.hunger;}return sum;}
        public static boolean miningRequirementMetInventory(Controller c,Tier t){return true;}
    }
    public static final class Reservations {public int freeInventoryOnly(Controller c,Item i){return c.storage.getItemCount(i);}}
    public static class Acquire extends Resource {
        int ticks;public Acquire(Target...targets){super(targets);}public Acquire(int count){this(new Target(item("oak_planks"),count));}
        protected Task onResourceTick(Controller c){
            if(++ticks<12)return null;
            for(Target t:itemTargets){
                String id=t.items[0].id;
                if((id.equals("coal")&&(!c.world.fuelAccessible||!c.world.coalAccessible))
                        ||(id.startsWith("oak_")&&(!c.world.fuelAccessible||!c.world.woodAccessible)))return null;
                int missing=t.count-c.storage.getItemCount(t);
                if(missing>0){c.entity.inventory.add(id,missing);c.world.acquired+=missing;}
            }
            return null;
        }
    }
    public static final class Hunt extends Resource {
        int ticks;public Hunt(Class<?> mob,Target...targets){super(targets);}
        protected Task onResourceTick(Controller c){if(++ticks>=3&&c.world.animals>0){c.entity.inventory.add(itemTargets[0].items[0].id,1);c.world.animals--;c.world.kills++;ticks=0;}return null;}
    }
    public static final class PlaceBlockNearbyTask extends Task {
        final Block block;Pos placed;public PlaceBlockNearbyTask(Block...blocks){block=blocks[0];}
        protected Task onTick(){if(!controller.world.placementBlocked){controller.world.station=block;placed=controller.world.pos;controller.world.stationPlacements++;}return null;}
        public Pos getPlaced(){return placed;}public boolean isFinished(){return placed!=null;}
    }
    public static final class Approach extends Task {public Approach(Pos pos){}public boolean isFinished(){return true;}}
    public static final class Wander extends Task {public Wander(float n){}public static Wander bounded(long ms){return new Wander(1);}public boolean isFinished(){return true;}}
    public static final class Catalogue {
        public static Item[] getItemMatches(String id){return new Item[]{item(id.equals("planks")?"oak_planks":id.equals("log")?"oak_log":id)};}
        public static boolean taskExists(Item i){return true;}public static boolean taskExists(String i){return true;}
        public static Resource getItemTask(Item item,int count){return new Acquire(new Target(item,count));}
        public static Resource getItemTask(String item,int count){return new Acquire(new Target(getItemMatches(item),count));}
        public static Resource getItemTask(Target target){return new Acquire(target);}
    }
    public static final class Timer {
        public Timer(double seconds){}public void reset(){}public boolean elapsed(){return true;}public void setInterval(double seconds){}
    }
    public static final class Debug {public static void logWarning(String message){}public static void logMessage(String message){}}
    public static final class Telemetry {public static void event(String name,String detail){if(name.contains("BLOCKED"))System.err.println(name+": "+detail);}public static void set(String name,Object value){}}
    public static final class UnusedTask extends Task {protected Task onTick(){throw new AssertionError("Unmodelled game action reached in food scenario");}}
    public static final class UnusedRecipe {}
    public static final class FoodChain {
        public Task mainTask;public Controller controller;private Object ellieFoodRecovery;
        FoodChain(Controller c,Task task){controller=c;mainTask=task;}
        public void setTask(Task task){if(mainTask!=null)mainTask.stop(task);mainTask=task;}
    }
    public static final class SharedRecovery {static String failure;public static void block(Object root,String reason){failure=reason;((Task)root).stop();}}

    static final class Loader extends ClassLoader implements AutoCloseable {
        final ZipFile jar;final Map<String,String> types=new HashMap<>(),members=new HashMap<>();
        Loader(String path,String symbols)throws Exception {
            super(PackagedFoodLoopTest.class.getClassLoader());jar=new ZipFile(path);
            Map<String,Class<?>> named=new HashMap<>();
            named.put("net/minecraft/world/item/Item",Item.class);named.put("net/minecraft/world/item/Items",Items.class);named.put("net/minecraft/world/item/ItemStack",Stack.class);
            named.put("net/minecraft/world/level/ItemLike",ItemLike.class);
            named.put("net/minecraft/core/BlockPos",Pos.class);named.put("net/minecraft/core/Vec3i",VecPos.class);named.put("net/minecraft/world/phys/Vec3",Vec.class);
            named.put("net/minecraft/world/entity/Entity",Entity.class);named.put("net/minecraft/world/entity/LivingEntity",Living.class);named.put("net/minecraft/world/entity/EntityType",EntityType.class);
            named.put("net/minecraft/world/entity/animal/Chicken",Chicken.class);named.put("net/minecraft/world/entity/animal/Cow",Cow.class);named.put("net/minecraft/world/entity/animal/Pig",Pig.class);named.put("net/minecraft/world/entity/animal/Sheep",Sheep.class);named.put("net/minecraft/world/entity/animal/Rabbit",Rabbit.class);
            named.put("net/minecraft/world/entity/item/ItemEntity",DroppedItem.class);named.put("net/minecraft/world/level/block/Block",Block.class);named.put("net/minecraft/world/level/block/Blocks",Blocks.class);named.put("net/minecraft/world/level/block/CropBlock",Crop.class);
            named.put("net/minecraft/world/level/block/state/BlockState",BlockState.class);named.put("net/minecraft/world/level/block/entity/BlockEntity",BlockEntity.class);named.put("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity",Furnace.class);
            named.put("net/minecraft/server/level/ServerLevel",World.class);named.put("net/minecraft/network/chat/Component",Component.class);named.put("net/minecraft/network/chat/MutableComponent",Text.class);
            Properties properties=new Properties();try(var in=Files.newInputStream(Path.of(symbols))){properties.load(in);}
            for(String key:properties.stringPropertyNames()){
                if(key.startsWith("member."))members.put(key.substring(7),properties.getProperty(key));
                else if(key.startsWith("class.")&&named.containsKey(properties.getProperty(key)))types.put(key.substring(6),Type.getInternalName(named.get(properties.getProperty(key))));
            }
            bind("PlayerEngineController",Controller.class);bind("PlayerEngineSettings",Settings.class);bind("BotBehaviour",Behaviour.class);
            bind("BotBehaviour$State",BehaviourState.class);
            bind("TaskCatalogue",Catalogue.class);
            bind("commands/base/CommandException",CommandException.class);
            bind("tasks/base/Task",Task.class);bind("tasks/base/TrackedTaskOutcomeProvider",Outcome.class);bind("tasks/ResourceTask",Resource.class);
            bind("tasks/base/SurvivalInterruptTask",SurvivalInterrupt.class);
            bind("trackers/storage/ItemStorageTracker",Storage.class);bind("commands/BlockScanner",Scanner.class);bind("trackers/EntityTracker",Tracker.class);
            bind("automaton/api/entity/IInventoryProvider",InventoryProvider.class);bind("automaton/api/entity/LivingEntityInventory",Inventory.class);
            bind("util/ItemTarget",Target.class);bind("util/SmeltTarget",SmeltTarget.class);bind("util/MiningRequirement",Tier.class);bind("util/helpers/ItemHelper",ItemHelper.class);bind("util/helpers/StorageHelper",StorageHelper.class);
            bind("multiversion/item/ItemVer",ItemVer.class);bind("multiversion/FoodComponentWrapper",Food.class);bind("multiversion/item/FoodComponentWrapper",Food.class);
            bind("tasks/resources/KillAndLootTask",Hunt.class);bind("tasks/resources/CollectPlanksTask",Acquire.class);bind("tasks/construction/PlaceBlockNearbyTask",PlaceBlockNearbyTask.class);bind("tasks/movement/GetCloseToBlockTask",Approach.class);bind("tasks/movement/TimeoutWanderTask",Wander.class);
            for(String boundary:List.of("tasks/movement/PickupDroppedItemTask","tasks/CraftInInventoryTask","tasks/container/CraftInTableTask","tasks/resources/CollectCropTask","tasks/resources/MineAndCollectTask"))bind(boundary,UnusedTask.class);
            bind("util/RecipeTarget",UnusedRecipe.class);bind("util/CraftingRecipe",UnusedRecipe.class);
            bind("util/time/TimerGame",Timer.class);bind("util/Debug",Debug.class);bind("observatory/ObservatoryTelemetry",Telemetry.class);bind("agentic/MaterialReservationService",Reservations.class);
            bind("reliability/SharedTargetRecovery",SharedRecovery.class);
        }
        void bind(String name,Class<?> type){types.put(P+name,Type.getInternalName(type));}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            String internal=name.replace('.','/');
            boolean nativeType=internal.startsWith(P+"tasks/resources/CollectFoodTask")||internal.equals(P+"tasks/resources/CollectMeatTask")
                    ||internal.startsWith(P+"tasks/container/SmeltInSmokerTask")||internal.startsWith(P+"tasks/container/SmeltInFurnaceTask")
                    ||internal.startsWith(P+"tasks/cooking/FuelPlanner")||internal.startsWith(P+"reliability/FoodAcquisition")
                    ||internal.startsWith(P+"reliability/FuelRecovery")||internal.startsWith(P+"reliability/FoodSurvival")
                    ||internal.startsWith(P+"agentic/InventorySpace")
                    ||internal.equals(P+"reliability/ProductionHandoff")||internal.equals(P+"nativecommands/NativeAccess");
            if(!nativeType)return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){
                Class<?> loaded=findLoadedClass(name);if(loaded!=null)return loaded;
                try{
                    var entry=jar.getEntry(internal+".class");if(entry==null)throw new ClassNotFoundException(name);
                    ClassWriter writer=new ClassWriter(0);
                    Remapper remapper=new Remapper(){
                        public String map(String n){return types.getOrDefault(n,n);}
                        public String mapMethodName(String owner,String n,String desc){return members.getOrDefault(n,n);}
                        public String mapFieldName(String owner,String n,String desc){return members.getOrDefault(n,n);}
                    };
                    ClassVisitor strings=new ClassVisitor(Opcodes.ASM8,new ClassRemapper(writer,remapper)){
                        public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM8,super.visitMethod(a,n,d,s,e)){
                            public void visitLdcInsn(Object value){if(value instanceof String text){String mapped=types.get(text.replace('.','/'));if(mapped!=null)value=mapped.replace('/','.');else value=members.getOrDefault(text,text);}super.visitLdcInsn(value);}
                        };}
                    };
                    new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(strings,0);
                    byte[] bytes=writer.toByteArray();Class<?> type=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(type);return type;
                }catch(IOException failure){throw new ClassNotFoundException(name,failure);}
            }
        }
        public void close()throws IOException{jar.close();}
    }

    static Task task(Loader loader,String name,Controller controller,double goal)throws Exception {
        Task task=(Task)loader.loadClass("com.player2.playerengine.tasks.resources."+name).getConstructor(double.class).newInstance(goal);
        task.controller=controller;return task;
    }
    static void run(Task task,int ticks){for(int i=0;i<ticks&&!task.isFinished()&&!task.stopped();i++){task.tick();task.controller.world.tick();}}
    public static void main(String[] args)throws Exception {
        try(Loader loader=new Loader(args[0],args[1])) {
            Controller c=new Controller();c.world.station=Blocks.SMOKER;c.entity.inventory.add("coal",12);
            Task meat=task(loader,"CollectMeatTask",c,60);run(meat,4000);
            check(c.world.kills>=10,"meat goal must hunt beyond the first animal");
            check(c.entity.inventory.count(item("cooked_chicken"))>=10,"meat goal must finish with the requested cooked food");
            check(meat.isFinished(),"full meat chain must finish");
            meat.stop();check(c.behaviour.depth==0,"completed workers must balance behavior scopes");

            Controller fuel=new Controller();fuel.world.station=Blocks.SMOKER;
            Task food=task(loader,"CollectFoodTask",fuel,30);run(food,4000);
            check(food.isFinished()&&((Outcome)food).isSuccessful(),"food must complete across repeated kills and multi-tick fuel acquisition");
            check(fuel.world.kills>=5&&fuel.world.acquired>0&&fuel.world.cooked>=5,"food chain must actually hunt, acquire fuel and cook");
            food.stop();check(fuel.behaviour.depth==0,"food success releases all behavior scopes");

            Controller station=new Controller();station.entity.inventory.add("chicken",2);
            Task fresh=task(loader,"CollectFoodTask",station,12);run(fresh,3000);
            check(fresh.isFinished()&&((Outcome)fresh).isSuccessful(),"food must acquire and place its own missing cooking station");
            check(station.world.stationPlacements==1&&station.world.cooked>=2,"missing station must be placed exactly once and used");fresh.stop();

            Controller existing=new Controller();existing.world.station=Blocks.FURNACE;existing.entity.inventory.add("coal",12);
            existing.entity.inventory.add("chicken",1);existing.entity.inventory.add("cooked_chicken",4);
            Task partial=task(loader,"CollectFoodTask",existing,48);run(partial,4000);
            check(partial.isFinished()&&((Outcome)partial).isSuccessful(),"existing cooked food must not satisfy a new batch before its raw food cooks");
            check(existing.world.cooked>=4&&existing.world.stationPlacements==0,"existing furnace must be reused while new cooked output is verified");partial.stop();

            Controller alternate=new Controller();alternate.world.station=Blocks.SMOKER;alternate.world.coalAccessible=false;
            alternate.entity.inventory.add("chicken",3);Task wood=task(loader,"CollectFoodTask",alternate,30);run(wood,4000);
            check(wood.isFinished()&&((Outcome)wood).isSuccessful(),"inaccessible coal must switch to a usable wood strategy");
            check(alternate.world.cooked>=5&&alternate.entity.inventory.count(Items.COAL)==0,"wood fallback must produce actual food without phantom coal");
            check(alternate.world.ticks<2200,"later food batches must remember a failed coal strategy instead of timing it out again");wood.stop();

            Controller blocked=new Controller();blocked.world.station=Blocks.SMOKER;blocked.world.fuelAccessible=false;
            blocked.entity.inventory.add("chicken",1);Task noFuel=task(loader,"CollectFoodTask",blocked,30);run(noFuel,4000);
            check(noFuel.isFinished()&&!((Outcome)noFuel).isSuccessful(),"exhausted fuel strategies must terminate BLOCKED, never claim completion");
            check(((Outcome)noFuel).controlledReason().contains("BLOCKED_FUEL")&&blocked.world.cooked==0,"failed fuel must propagate its specific reason without inventing output");
            check(blocked.behaviour.depth==0,"blocked food must balance behavior scopes");

            Controller obstruction=new Controller();obstruction.world.placementBlocked=true;obstruction.entity.inventory.add("coal",4);
            obstruction.entity.inventory.add("chicken",1);Task noStation=task(loader,"CollectFoodTask",obstruction,30);run(noStation,4000);
            check(noStation.isFinished()&&!((Outcome)noStation).isSuccessful(),"obstructed station placement must terminate rather than loop");
            check(obstruction.world.stationPlacements==0&&obstruction.world.cooked==0,"blocked placement must not count as a placed or cooking station");
            check(((Outcome)noStation).controlledReason().contains("STATION_PLACEMENT"),"obstructed placement must identify the actual blocked dependency");

            Controller empty=new Controller();empty.world.sourceAnimals=false;Task search=task(loader,"CollectFoodTask",empty,30);run(search,4000);
            check(search.isFinished()&&!((Outcome)search).isSuccessful(),"no animals/resources must produce a bounded food search failure");
            check(((Outcome)search).controlledReason().contains("NO_PROGRESS"),"empty search must report the missing-progress reason");

            Class<?> survival=loader.loadClass("com.player2.playerengine.reliability.FoodSurvival");
            var before=survival.getMethod("before",Object.class);
            var priority=survival.getMethod("priority",Object.class,float.class);
            FoodChain chain=new FoodChain(blocked,noFuel);blocked.user.mainTask=new Task();
            before.invoke(null,chain);
            for(int i=0;i<20;i++){
                chain.mainTask=task(loader,"CollectFoodTask",blocked,30);
                check((float)priority.invoke(null,chain,55f)==Float.NEGATIVE_INFINITY,"unchanged failed automatic food work must stay suppressed");
            }
            check(!blocked.user.mainTask.stopped(),"noncritical hunger must leave owner work available");
            blocked.entity.inventory.add("iron_ingot",1);chain.mainTask=task(loader,"CollectFoodTask",blocked,30);
            check((float)priority.invoke(null,chain,55f)==Float.NEGATIVE_INFINITY,"unrelated mining output must not rearm the same failed food strategy");
            blocked.entity.inventory.add("coal",1);chain.mainTask=task(loader,"CollectFoodTask",blocked,30);
            check((float)priority.invoke(null,chain,55f)==55f,"new usable fuel must permit a new bounded automatic attempt");

            FoodChain hungry=new FoodChain(empty,search);empty.user.mainTask=new Task();empty.context.hunger.level=0;
            before.invoke(null,hungry);hungry.mainTask=task(loader,"CollectFoodTask",empty,30);
            check((float)priority.invoke(null,hungry,55f)==Float.NEGATIVE_INFINITY,"failed critical hunger work must not loop");
            check(empty.user.mainTask.stopped()&&SharedRecovery.failure.contains("BLOCKED_SURVIVAL_FOOD"),"critical hunger must report owner job BLOCKED rather than silently continue unsafely");
            Task eating=new Task();hungry.mainTask=eating;
            check((float)priority.invoke(null,hungry,55f)==55f&&hungry.mainTask==eating,"emergency eating priority must remain unchanged");

            Controller manual=new Controller();manual.user.mainTask=task(loader,"CollectFoodTask",manual,30);
            FoodChain auto=new FoodChain(manual,task(loader,"CollectFoodTask",manual,30));
            check((float)priority.invoke(null,auto,55f)==Float.NEGATIVE_INFINITY,"automatic food collector must yield to the same explicit owner job");
            check(!manual.user.mainTask.stopped(),"yielding automatic collection must not cancel the explicit food job");

            Class<?> acquisition=loader.loadClass("com.player2.playerengine.reliability.FoodAcquisition");
            Controller owned=new Controller();owned.entity.inventory.add("bread",20);owned.entity.inventory.add("cooked_chicken",1);
            check((int)acquisition.getMethod("startingScore",Object.class,boolean.class).invoke(null,owned,true)==6,"bread must not inflate a cooked-meat acquisition goal");
            check((int)acquisition.getMethod("startingScore",Object.class,boolean.class).invoke(null,owned,false)==106,"general food starting score must retain hunger-point units");
            for(int invalid:new int[]{0,-1,Integer.MAX_VALUE}){
                boolean rejected=false;
                try{acquisition.getMethod("validateRequested",int.class).invoke(null,invalid);}catch(java.lang.reflect.InvocationTargetException failure){rejected=failure.getCause() instanceof CommandException;}
                check(rejected,"invalid food quantities must reject before any action");
            }

            System.out.println("PackagedFoodLoopTest: "+checks+" full-chain checks passed; packaged worker bodies, substituted world/combat/acquisition/navigation");
        }
    }
}
