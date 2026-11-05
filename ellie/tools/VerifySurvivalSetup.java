import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;

/** Static packaged-entry/ABI checks complement substituted-world setup scenarios. */
public final class VerifySurvivalSetup {
    static final String P="com/player2/playerengine/";
    static ClassNode read(ZipFile z,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;}
    static void method(ZipFile z,String owner,String name,String desc)throws Exception{
        if(read(z,owner).methods.stream().noneMatch(m->m.name.equals(name)&&m.desc.equals(desc)))throw new AssertionError("Missing native ABI: "+owner+"."+name+desc);
    }
    static int calls(ClassNode c,String method,String owner,String name){int count=0;for(MethodNode m:c.methods)if(m.name.equals(method))for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))count++;return count;}
    public static void main(String[] args)throws Exception{
        try(ZipFile z=new ZipFile(args[0])){
            ClassNode setup=read(z,P+"tasks/setup/EllieSetupTask");
            if(calls(setup,"onStart",P+"tasks/setup/SetupSurvival","<init>")!=1||calls(setup,"onTick",P+"tasks/setup/SetupSurvival","tick")!=2)throw new AssertionError("Survival stages missing from actual setup entry or final verification");
            ClassNode world=read(z,P+"tasks/setup/SetupSurvivalWorld");
            if(calls(world,"task",P+"nativecommands/NativeBuildSupport","blueprint")!=1||calls(world,"task",P+"nativecommands/NativeFood","task")!=1)throw new AssertionError("Existing native builder/food routes bypassed");
            method(z,P+"tasks/resources/CollectFoodTask","<init>","(D)V");
            method(z,P+"tasks/misc/EquipWeaponTask","<init>","([Lnet/minecraft/class_1792;)V");
            method(z,P+"tasks/movement/GetToBlockTask","<init>","(Lnet/minecraft/class_2338;)V");
            method(z,P+"tasks/InteractWithBlockTask","<init>","(Lnet/minecraft/class_2338;)V");
            method(z,P+"tasks/construction/build_structure/BuildStructureTask","<init>","(Lnet/minecraft/class_2338;Ljava/lang/String;Ljava/lang/String;L"+P+"PlayerEngineController;)V");
            method(z,P+"control/SlotHandler","forceEquipItemToOffhand","(Lnet/minecraft/class_1792;)V");
            method(z,P+"trackers/EntityTracker","getHostiles","()Ljava/util/List;");
            ClassNode base=read(z,P+"tasks/setup/StarterBaseTask");
            if(calls(base,"onTick",P+"tasks/setup/StarterBaseTask$Environment","surveyLighting")!=0)throw new AssertionError("Lighting cannot gate setup");
            ClassNode terrain=read(z,P+"tasks/setup/StarterBaseWorld");
            if(calls(terrain,"prepareTerrain",P+"reliability/StructuralMaterials","carried")!=1||calls(terrain,"count",P+"reliability/StructuralMaterials","carried")!=1||calls(terrain,"acquire",P+"reliability/StructuralMaterials","available")!=1)throw new AssertionError("Workshop foundations must use the shared flexible block policy");
            System.out.println("Packaged survival setup entry, final gate and seven native ABI boundaries verified; no lighting gate");
        }
    }
}
