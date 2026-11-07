import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.commons.*;
import com.player2.playerengine.reliability.PartialBlockSupport;
import com.player2.playerengine.reliability.PartialBlockSupport.Box;

/** Actual packaged feet/support methods and helper, with entity/chunk/settings boundaries
 * substituted. Geometry comes from vanilla 1.21.1; no physics or NPC controller is simulated. */
public final class PartialBlockNavigationTest implements Opcodes {
    static final String P="com/player2/playerengine/automaton/",H="com/player2/playerengine/reliability/PartialBlockSupport";
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public record Property(String name) {}
    public enum SlabType {BOTTOM,TOP,DOUBLE}
    public enum PathType {LAND}
    public enum Input {MOVE_FORWARD,MOVE_BACK,SNEAK,JUMP}
    public static class Block {final String id;Block(String id){this.id=id;}public String toString(){return "Block{minecraft:"+id+"}";}}
    public static class Air extends Block {Air(){super("air");}}
    public static class Slab extends Block {public static final Property TYPE=new Property("type");Slab(){super("oak_slab");}}
    public static class Stair extends Block {Stair(){super("oak_stairs");}}
    public static class Snow extends Block {public static final Property LAYERS=new Property("layers");Snow(){super("snow");}}
    public static class Carpet extends Block {Carpet(String id){super(id);}}
    public static class Door extends Block {Door(){super("oak_door");}public static boolean isWoodenDoor(State s){return true;}}
    public static class Gate extends Block {Gate(){super("oak_fence_gate");}}
    public static class Unused extends Block {Unused(){super("unused");}}
    public static class Trapdoor extends Block {Trapdoor(String id){super(id);}}
    public static class Glass extends Block {Glass(){super("glass");}}
    public static class FluidType {}
    public static class FlowingFluid extends FluidType {}
    public static class WaterFluid extends FlowingFluid {}
    public static class Fluid {final boolean water;Fluid(boolean water){this.water=water;}public boolean isEmpty(){return !water;}public FluidType getType(){return water?new WaterFluid():new FluidType();}}
    public static class Fluids {public static final FlowingFluid FLOWING_WATER=new FlowingFluid();}
    public static class Tag {}
    public static class Tags {public static final Tag CLIMBABLE=new Tag();}
    public static class Setting {final Object value;Setting(Object v){value=v;}public Object get(){return value;}}
    public static class Settings {
        public Setting blocksToAvoid=new Setting(new ArrayList<Block>()),allowWalkOnBottomSlab=new Setting(true),
                assumeWalkOnWater=new Setting(false),assumeWalkOnLava=new Setting(false),allowSwimming=new Setting(true);
    }
    public static class RawBox {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        RawBox(Box b){minX=b.minX();minY=b.minY();minZ=b.minZ();maxX=b.maxX();maxY=b.maxY();maxZ=b.maxZ();}
    }
    public static class Shape {final List<RawBox> boxes;Shape(List<Box> boxes){this.boxes=boxes.stream().map(RawBox::new).toList();}public List<RawBox> toAabbs(){return boxes;}}
    public static class Pos {public final int x,y,z;public Pos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}}
    public static class BetterPos extends Pos {
        public BetterPos(double x,double y,double z){super((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}
        public BetterPos up(){return new BetterPos(x,y+1,z);}
    }
    public static class State {
        final String description;final Block block;final List<Box> boxes;final Map<String,String> values=new HashMap<>();boolean land,full;
        State(String description,Block block,List<Box> boxes){this.description=description;this.block=block;this.boxes=boxes;
            if(description.contains("["))for(String pair:description.substring(description.indexOf('[')+1,description.length()-1).split(",")){
                String[] kv=pair.split("=");values.put(kv[0],kv[1]);}
            land=block.id.equals("air");full=block.id.equals("stone");
        }
        public Block getBlock(){return block;}public Fluid getFluidState(){return new Fluid(block==Blocks.WATER||values.getOrDefault("waterlogged","false").equals("true"));}
        public Shape getCollisionShape(Access access,Pos pos){access.neighbor(pos.x+1,pos.z);return new Shape(boxes);}
        public boolean is(Tag tag){return tag==Tags.CLIMBABLE&&(block.id.equals("scaffolding")||block.id.equals("ladder"));}
        public Comparable<?> getValue(Property p){String s=values.get(p.name);if(p==Slab.TYPE)return SlabType.valueOf(s.toUpperCase());return Integer.valueOf(s);}
        public boolean isPathfindable(PathType path){return land;}
    }
    public static class Blocks {
        public static final Block AIR=new Air(),MAGMA_BLOCK=new Block("magma_block"),BUBBLE_COLUMN=new Block("bubble_column"),HONEY_BLOCK=new Block("honey_block"),
                FARMLAND=new Block("farmland"),DIRT_PATH=new Block("dirt_path"),ENDER_CHEST=new Block("ender_chest"),CHEST=new Block("chest"),TRAPPED_CHEST=new Block("trapped_chest"),
                GLASS=new Glass(),WATER=new Block("water"),LAVA=new Block("lava"),LILY_PAD=new Block("lily_pad"),TRIPWIRE=new Block("tripwire"),COBWEB=new Block("cobweb"),
                END_PORTAL=new Block("end_portal"),COCOA=new Block("cocoa"),AZALEA=new Block("azalea"),FLOWERING_AZALEA=new Block("flowering_azalea"),GLOW_LICHEN=new Block("glow_lichen"),
                CAVE_VINES=new Block("cave_vines"),CAVE_VINES_PLANT=new Block("cave_vines_plant"),END_ROD=new Block("end_rod"),VINE=new Block("vine");
    }
    static final Map<String,Block> blocks=new HashMap<>();
    static Block block(String id){return blocks.computeIfAbsent(id,s->switch(s){case "air"->Blocks.AIR;case "oak_slab"->new Slab();case "oak_stairs"->new Stair();case "snow"->new Snow();
        case "white_carpet","moss_carpet"->new Carpet(s);case "oak_trapdoor","iron_trapdoor"->new Trapdoor(s);case "oak_door"->new Door();case "oak_fence_gate"->new Gate();
        default->{try{yield (Block)Blocks.class.getField(s.toUpperCase()).get(null);}catch(Exception missing){yield new Block(s);}}});}
    static State air=new State("Block{minecraft:air}",Blocks.AIR,List.of());
    public static class World {
        final Map<String,State> cells=new HashMap<>();final Set<String> loaded=new HashSet<>();final List<String> queries=new ArrayList<>();int reads,liveReads;
        void put(int x,int y,int z,State s){cells.put(x+","+y+","+z,s);loaded.add((x>>4)+","+(z>>4));}
        public Chunk getChunkSource(){return new Chunk(this);}
        public State getBlockState(Pos p){liveReads++;throw new AssertionError("Live world read may synchronously load a chunk");}
    }
    public static class Chunk implements ChunkAccessor {
        final World world;Chunk(World w){world=w;}
        public Chunk automatone$getChunkNow(int x,int z){world.queries.add(x+","+z);return world.loaded.contains(x+","+z)?this:null;}
        public State getBlockState(Pos pos){world.reads++;return world.cells.getOrDefault(pos.x+","+pos.y+","+pos.z,air);}
    }
    public interface ChunkAccessor {Chunk automatone$getChunkNow(int x,int z);}
    public static class Access {final View view;Access(View v){view=v;}void neighbor(int x,int z){view.isLoaded(x,z);}}
    public static class View {
        final World world;public final Access access=new Access(this);public View(World w){world=w;}
        public boolean isLoaded(int x,int z){return world.getChunkSource().automatone$getChunkNow(x>>4,z>>4)!=null;}
        public boolean worldContainsLoadedChunk(int x,int z){return isLoaded(x,z);}
        public State get0(int x,int y,int z){return isLoaded(x,z)?world.getChunkSource().getBlockState(new Pos(x,y,z)):air;}
    }
    public static class Entity {
        double x,y,z;boolean grounded=true;public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
        public boolean onGround(){return grounded;}public float getBbWidth(){return .6f;}public float maxUpStep(){return .6f;}
    }
    public static class Baritone {public final Settings settings=new Settings();public Settings settings(){return settings;}}
    public static class Context {
        final Entity entity=new Entity();World world=new World();final Baritone baritone=new Baritone();
        public Entity entity(){return entity;}public World world(){return world;}public Baritone baritone(){return baritone;}
    }
    public static class Calc {public final View bsi;Calc(World w){bsi=new View(w);}}
    public static class MoveState {
        String status="RUNNING";final Map<Input,Boolean> inputs=new HashMap<>();public String getStatus(){return status;}
        public Map<Input,Boolean> getInputStates(){return inputs;}public MoveState setInput(Input input,boolean value){inputs.put(input,value);return this;}
    }
    public static class MovementTraverse {public Pos src=new Pos(0,65,0),dest=new Pos(1,65,0);public Context ctx;MovementTraverse(Context c){ctx=c;}}
    public static class MovementDiagonal extends MovementTraverse {MovementDiagonal(Context c){super(c);}}

    public static class NativeBoundaries {
        static Method on,through;
        public static boolean isBlockNormalCube(State s){return s.full;}
        public static boolean isWater(State s){return s.block==Blocks.WATER;}
        public static boolean isLava(State s){return s.block==Blocks.LAVA;}
        public static boolean isFlowing(int x,int y,int z,State s,View v){return false;}
        public static boolean avoidWalkingInto(State s){return Set.of("magma_block","cactus","fire","soul_fire","lava").contains(s.block.id)||!s.getFluidState().isEmpty();}
        public static boolean canWalkOn(View v,int x,int y,int z,Settings s){try{return (boolean)on.invoke(null,v,x,y,z,v.get0(x,y,z),s);}catch(Exception e){throw new RuntimeException(e);}}
    }

    static final class Loader extends ClassLoader implements AutoCloseable {
        final ZipFile jar;final Properties symbols=new Properties();final Map<String,String> map=new HashMap<>();boolean baselineProbe;
        Loader(String jarPath,String symbolPath)throws Exception {
            super(PartialBlockNavigationTest.class.getClassLoader());jar=new ZipFile(jarPath);try(var in=Files.newInputStream(Path.of(symbolPath))){symbols.load(in);}
            Map<String,Class<?>> names=Map.ofEntries(Map.entry("Block",Block.class),Map.entry("Blocks",Blocks.class),Map.entry("BlockState",State.class),Map.entry("BlockPos",Pos.class),
                Map.entry("LivingEntity",Entity.class),Map.entry("ServerLevel",World.class),Map.entry("Level",World.class),Map.entry("ServerChunkCache",Chunk.class),Map.entry("LevelChunk",Chunk.class),
                Map.entry("SlabBlock",Slab.class),Map.entry("SlabType",SlabType.class),Map.entry("StairBlock",Stair.class),Map.entry("SnowLayerBlock",Snow.class),Map.entry("CarpetBlock",Carpet.class),
                Map.entry("AirBlock",Air.class),Map.entry("DoorBlock",Door.class),Map.entry("FenceGateBlock",Gate.class),Map.entry("TrapDoorBlock",Trapdoor.class),Map.entry("StainedGlassBlock",Glass.class),
                Map.entry("BlockTags",Tags.class),Map.entry("TagKey",Tag.class),Map.entry("FluidState",Fluid.class),Map.entry("WaterFluid",WaterFluid.class),Map.entry("Fluids",Fluids.class),Map.entry("Fluid",FluidType.class),Map.entry("FlowingFluid",FlowingFluid.class),
                Map.entry("PathComputationType",PathType.class),Map.entry("BlockGetter",Access.class),Map.entry("VoxelShape",Shape.class),Map.entry("AABB",RawBox.class));
            for(String k:symbols.stringPropertyNames())if(k.startsWith("class.")){
                String official=symbols.getProperty(k),simple=official.substring(official.lastIndexOf('/')+1);
                Class<?> boundary=names.get(simple);
                if(boundary==null)boundary=simple.endsWith("Property")?Property.class:Unused.class;
                map.put(k.substring(6),Type.getInternalName(boundary));
            }
            map.put(P+"utils/BlockStateInterface",Type.getInternalName(View.class));map.put(P+"utils/accessor/ServerChunkManagerAccessor",Type.getInternalName(ChunkAccessor.class));
            map.put(P+"api/utils/BetterBlockPos",Type.getInternalName(BetterPos.class));map.put(P+"api/utils/input/Input",Type.getInternalName(Input.class));
            map.put(P+"api/Settings",Type.getInternalName(Settings.class));map.put(P+"api/Settings$Setting",Type.getInternalName(Setting.class));
            map.put(P+"pathing/movement/MovementHelper","PartialNativeHelper");map.put(P+"utils/player/EntityContext","PartialNativeContext");
        }
        final Remapper remap=new Remapper(){
            public String map(String n){return map.getOrDefault(n,n);}
            public String mapMethodName(String owner,String n,String desc){return symbols.getProperty("member."+n,n);}
            public String mapFieldName(String owner,String n,String desc){return symbols.getProperty("member."+n,n);}
        };
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals("PartialNativeHelper")&&!name.equals("PartialNativeContext")&&!name.startsWith(H.replace('/','.'))
                    &&!name.equals("com.player2.playerengine.nativecommands.NativeAccess"))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){
                Class<?> loaded=findLoadedClass(name);if(loaded!=null)return loaded;
                try {
                    boolean helper=name.equals("PartialNativeHelper"),context=name.equals("PartialNativeContext");
                    String source=helper?P+"pathing/movement/MovementHelper":context?P+"utils/player/EntityContext":name.replace('.','/');
                    var entry=jar.getEntry(source+".class");
                    byte[] original=entry!=null?jar.getInputStream(entry).readAllBytes():baselineProbe&&source.startsWith(H)?getParent().getResourceAsStream(source+".class").readAllBytes():null;
                    if(original==null)throw new ClassNotFoundException("Missing packaged class: "+source);
                    ClassNode node=new ClassNode();new ClassReader(original).accept(node,0);
                    if(name.equals(H.replace('/','.'))&&Boolean.getBoolean("ellie.partial.debug"))for(MethodNode m:node.methods)
                        for(TryCatchBlockNode c:m.tryCatchBlocks){AbstractInsnNode at=c.handler.getNext();while(at instanceof FrameNode||at instanceof LineNumberNode)at=at.getNext();
                            if(at instanceof VarInsnNode v&&v.getOpcode()==ASTORE){InsnList log=new InsnList();log.add(new InsnNode(DUP));log.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/Throwable","printStackTrace","()V",false));m.instructions.insertBefore(at,log);}}
                    if(helper||context){
                        node.superName=Type.getInternalName(helper?NativeBoundaries.class:Context.class);node.interfaces.clear();node.fields.clear();node.access=ACC_PUBLIC;
                        node.methods.removeIf(m->context?!m.name.equals("feetPos"):!(Set.of("canWalkOn","canWalkThrough").contains(m.name)&&m.desc.startsWith("(L"+P+"utils/BlockStateInterface;III")&&m.desc.contains("Lnet/minecraft/class_2680;")));
                        MethodNode ctor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(ALOAD,0));ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,node.superName,"<init>","()V",false));ctor.instructions.add(new InsnNode(RETURN));node.methods.add(ctor);
                    }
                    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(new ClassRemapper(writer,remap){
                        protected MethodVisitor createMethodRemapper(MethodVisitor target){return new MethodRemapper(target,remap){
                            public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){super.visitMethodInsn(op,owner,method,desc,owner.equals(P+"pathing/movement/MovementHelper")?false:itf);}
                            public void visitLdcInsn(Object value){
                                if(value instanceof String s){String replacement=map.get(s.replace('.','/'));value=replacement==null?symbols.getProperty("member."+s,s):replacement.replace('/','.');}
                                super.visitLdcInsn(value);
                            }
                        };}
                    });
                    byte[] bytes=writer.toByteArray();Class<?> result=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(result);return result;
                }catch(Exception e){throw new ClassNotFoundException(name,e);}
            }
        }
        public void close()throws Exception{jar.close();}
    }
    static List<State> loadStates(String path)throws Exception {
        List<State> states=new ArrayList<>();for(String line:Files.readAllLines(Path.of(path))){
            if(line.startsWith("#")||line.startsWith("state\t"))continue;String[] cells=line.split("\t",-1);List<Box> boxes=new ArrayList<>();
            if(!cells[1].isEmpty())for(String raw:cells[1].split(";")){double[] b=Arrays.stream(raw.split(",")).mapToDouble(Double::parseDouble).toArray();boxes.add(new Box(b[0],b[1],b[2],b[3],b[4],b[5]));}
            String id=cells[0].substring(cells[0].indexOf(':')+1,cells[0].indexOf('}'));State s=new State(cells[0],block(id),boxes);
            s.land=Boolean.parseBoolean(cells[2]);s.full=Boolean.parseBoolean(cells[3]);states.add(s);
        }return states;
    }
    static boolean on(View view,State s,Settings settings)throws Exception {return (boolean)NativeBoundaries.on.invoke(null,view,0,64,0,s,settings);}
    static boolean through(View view,State s,Settings settings)throws Exception {return (boolean)NativeBoundaries.through.invoke(null,view,0,64,0,s,settings);}
    public static void main(String[] args)throws Exception {
        List<State> states=loadStates(args[2]);
        try(Loader loader=new Loader(args[0],args[1])) {
            loader.baselineProbe=args.length>3&&args[3].equals("--baseline-probe");
            Class<?> helper=loader.loadClass("PartialNativeHelper");
            NativeBoundaries.on=helper.getMethod("canWalkOn",View.class,int.class,int.class,int.class,State.class,Settings.class);
            NativeBoundaries.through=helper.getMethod("canWalkThrough",View.class,int.class,int.class,int.class,State.class,Settings.class);
            Class<?> nativeContext=loader.loadClass("PartialNativeContext"),support=loader.loadClass(H.replace('/','.'));
            Method feet=nativeContext.getMethod("feetPos"),adjust=support.getMethod("adjustStepInput",Object.class,Object.class),ascent=support.getMethod("allowAscent",Object.class,int.class,int.class,int.class,int.class,int.class);
            Context ctx=(Context)nativeContext.getConstructor().newInstance();ctx.world.put(0,63,0,new State("stone",block("stone"),List.of(new Box(0,0,0,1,1,1))));
            for(State s:states){
                ctx.world.put(0,63,0,new State("stone",block("stone"),List.of(new Box(0,0,0,1,1,1))));
                ctx.world.put(0,64,0,s);View view=new View(ctx.world);boolean walkOn=on(view,s,ctx.baritone.settings),walkThrough=through(view,s,ctx.baritone.settings);
                if(s.block instanceof Slab||s.block instanceof Stair)check(walkOn,"native slabs/stairs remain supported: "+s.description);
                if(s.block instanceof Stair)check(!walkThrough,"dry and waterlogged stairs are solid body obstacles: "+s.description);
                if(s.block.id.equals("scaffolding")&&s.values.get("waterlogged").equals("true"))check(walkThrough,"native waterlogged scaffolding climbing remains passable");
                if(s.block instanceof Carpet)check(walkThrough&&!walkOn,"thin carpets retain native walk-through representation");
                if(s.block instanceof Snow){int layers=Integer.parseInt(s.values.get("layers"));check(walkThrough==(layers<=2)&&walkOn==(layers>=3),"snow layer representation: "+s.description);}
                if(Set.of("oak_fence","cobblestone_wall","glass_pane","iron_bars","oak_door","pointed_dripstone","campfire","soul_campfire","powder_snow","cactus","magma_block","honey_block","big_dripleaf","turtle_egg").contains(s.block.id))
                    check(!walkOn,"unsafe/vertical support rejected: "+s.description);
                if(s.block instanceof Trapdoor)check(walkOn==(s.values.get("open").equals("false")&&!s.values.get("waterlogged").equals("true")),"trapdoor horizontal/vertical/fluid support: "+s.description);
                if(Set.of("white_bed","enchanting_table","stonecutter","anvil","daylight_detector").contains(s.block.id))check(walkOn,"ordinary solid partial floor recognized: "+s.description);
                if(s.block.id.equals("cake")&&s.values.get("bites").equals("0"))check(walkOn,"whole cake supports a centered landing");
                // Test every actual top face with center and off-center feet, including staircase quadrants.
                for(double lx:new double[]{.25,.75})for(double lz:new double[]{.25,.75}){
                    double top=0;for(Box b:s.boxes)if(lx>b.minX()&&lx<b.maxX()&&lz>b.minZ()&&lz<b.maxZ())top=Math.max(top,b.maxY());
                    if(top<=0||top>1)continue;
                    for(double origin:new double[]{0,160,-160,-1,15,16,-17}){
                        int bx=(int)origin,bz=(int)origin;ctx.world.cells.clear();ctx.world.loaded.clear();ctx.world.queries.clear();
                        ctx.world.put(bx,64,bz,s);ctx.world.put(bx,63,bz,new State("stone",block("stone"),List.of(new Box(0,0,0,1,1,1))));
                        ctx.entity.x=origin+lx;ctx.entity.z=origin+lz;ctx.entity.y=64+top;ctx.entity.grounded=true;
                        BetterPos pos=(BetterPos)feet.invoke(ctx);int expected=(int)Math.floor(ctx.entity.y+.1251);
                        if(top>.125)expected=65;
                        check(pos.y==expected&&pos.x==bx&&pos.z==bz,"grounded foot cell: "+s.description+" at "+ctx.entity.x+","+ctx.entity.y+","+ctx.entity.z+" got="+pos.y+" expected="+expected);
                        if(!loader.baselineProbe)check(ctx.world.queries.contains((bx>>4)+","+(bz>>4)),"correct loaded chunk coordinates: expected="+(bx>>4)+","+(bz>>4)+" queried="+ctx.world.queries);
                        check(ctx.world.liveReads==0,"feet/support must not load chunks synchronously");
                    }
                }
            }
            State slab=states.stream().filter(s->s.block instanceof Slab&&"bottom".equals(s.values.get("type"))&&"false".equals(s.values.get("waterlogged"))).findFirst().orElseThrow();
            State water=new State("Block{minecraft:water}",Blocks.WATER,List.of());ctx.world.put(0,64,0,water);
            check(through(new View(ctx.world),water,ctx.baritone.settings),"open water without solid collision retains native swimming passability");
            State ladder=new State("Block{minecraft:ladder}[waterlogged=true]",block("ladder"),List.of(new Box(0,0,0,1,1,.125)));
            ctx.world.put(0,64,0,ladder);check(through(new View(ctx.world),ladder,ctx.baritone.settings)&&on(new View(ctx.world),ladder,ctx.baritone.settings),"native waterlogged ladder climbing remains available");
            ctx.world.cells.clear();ctx.world.loaded.clear();ctx.world.put(0,64,0,slab);ctx.entity.x=.5;ctx.entity.z=.5;ctx.entity.y=64.5;
            ctx.entity.grounded=false;
            for(double hopY:new double[]{64.5,64.55,64.7,64.85,64.9,65.1}){ctx.entity.y=hopY;check(((BetterPos)feet.invoke(ctx)).y==65,"hopping over a low support must retain the native logical foot cell");}
            ctx.entity.y=64.25;check(((BetterPos)feet.invoke(ctx)).y==64,"a face above the actual feet cannot authorize a higher cell");
            ctx.entity.y=64.5;ctx.entity.grounded=true;
            ctx.world.loaded.clear();check(((BetterPos)feet.invoke(ctx)).y==64,"missing chunk retains unsnapped feet without loading");ctx.world.loaded.add("0,0");
            var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
            try{
                check(worker.submit(()->((BetterPos)feet.invoke(ctx)).y).get(2,java.util.concurrent.TimeUnit.SECONDS)==65,"worker feet query uses a non-loading view");
                World previous=ctx.world;ctx.world=new World();
                check(worker.submit(()->((BetterPos)feet.invoke(ctx)).y).get(2,java.util.concurrent.TimeUnit.SECONDS)==64,"world change cannot reuse previous support data");
                check(previous.liveReads==0&&ctx.world.liveReads==0,"worker/world switch never accesses live blocks");ctx.world=previous;
            }finally{worker.shutdownNow();}
            ctx.baritone.settings.allowWalkOnBottomSlab=new Setting(false);check(!on(new View(ctx.world),slab,ctx.baritone.settings),"bottom slab setting remains authoritative");
            check(((BetterPos)feet.invoke(ctx)).y==65,"already standing on a forbidden floor must retain physical foot height to leave it");ctx.baritone.settings.allowWalkOnBottomSlab=new Setting(true);
            for(int origin:new int[]{0,16,-16}){
                ctx.world.cells.clear();ctx.world.loaded.clear();ctx.world.put(origin,64,0,slab);ctx.world.loaded.add(((origin-1)>>4)+",0");
                ctx.entity.x=origin-.15;ctx.entity.y=64.5;ctx.entity.z=.5;
                BetterPos edge=(BetterPos)feet.invoke(ctx);check(edge.y==65&&edge.x==origin-1,"bounding-box support across chunk/partial-block edge");
            }
            ctx.world.cells.clear();ctx.world.loaded.clear();ctx.world.put(0,64,0,slab);ctx.entity.x=.5;ctx.entity.y=64.5;ctx.entity.z=.5;
            State bed=states.stream().filter(s->s.block.id.equals("white_bed")).findFirst().orElseThrow();
            ctx.baritone.settings.blocksToAvoid=new Setting(List.of(bed.block));check(!on(new View(ctx.world),bed,ctx.baritone.settings),"configured avoided partial support must remain avoided");ctx.baritone.settings.blocksToAvoid=new Setting(List.of());
            State stone=new State("stone",block("stone"),List.of(new Box(0,0,0,1,1,1)));
            for(State stair:states)if(stair.block instanceof Stair){ctx.world.put(0,64,0,stair);ctx.world.put(1,65,0,stone);
                check((boolean)ascent.invoke(null,new Calc(ctx.world),0,65,0,1,0),"native staircase ascent remains reachable: "+stair.description);}
            ctx.world.cells.clear();ctx.world.put(0,64,0,bed);ctx.world.put(1,65,0,stone);
            check(!(boolean)ascent.invoke(null,new Calc(ctx.world),0,65,0,1,0),"bed to full block 1.4375 higher is not a reachable ascend");
            ctx.world.put(1,65,0,slab);check((boolean)ascent.invoke(null,new Calc(ctx.world),0,65,0,1,0),"bed to bottom slab gradual ascend remains reachable");
            for(boolean diagonal:new boolean[]{false,true}){
                MovementTraverse move=diagonal?new MovementDiagonal(ctx):new MovementTraverse(ctx);ctx.entity.x=.8;ctx.entity.z=.5;ctx.entity.y=64.25;
                ctx.world.put(1,64,0,stone);MoveState input=new MoveState().setInput(Input.MOVE_FORWARD,true);adjust.invoke(null,move,input);
                check(Boolean.TRUE.equals(input.inputs.get(Input.JUMP)),"low support to same-node full block requires native jump input");
                for(String cause:List.of("air","sneak","back","stopped","airborne","paused","far","down")){
                    MoveState safe=new MoveState().setInput(Input.MOVE_FORWARD,true);ctx.entity.grounded=true;ctx.entity.x=.8;move.dest=new Pos(1,65,0);ctx.world.put(1,64,0,stone);
                    switch(cause){case "air"->ctx.world.put(1,64,0,air);case "sneak"->safe.setInput(Input.SNEAK,true);case "back"->safe.setInput(Input.MOVE_BACK,true);case "stopped"->safe.status="SUCCESS";
                        case "airborne"->ctx.entity.grounded=false;case "paused"->safe.setInput(Input.MOVE_FORWARD,false);case "far"->ctx.entity.x=-5;case "down"->move.dest=new Pos(1,64,0);}
                    Map<Input,Boolean> before=new HashMap<>(safe.inputs);adjust.invoke(null,move,safe);check(safe.inputs.equals(before),"native bridge/emergency/control inputs preserved for "+cause);
                }
            }
            check(!PartialBlockSupport.needsJump(.5,.6)&&PartialBlockSupport.needsJump(.75,.6)&&!PartialBlockSupport.needsJump(1.5,.6),"step/jump limits are distinct");
            System.out.println("PartialBlockNavigationTest: "+checks+" packaged geometry/feet/input checks across "+states.size()+" vanilla block states; entity/chunk/physics boundaries substituted");
        }
    }
}
