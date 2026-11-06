import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Checks actual shipped action/receipt hooks; the behavioral harness executes those worker bodies. */
public final class VerifyFoodReliability {
    static final String P="com/player2/playerengine/",FOOD=P+"reliability/FoodAcquisition",FUEL=P+"reliability/FuelRecovery",COMPLETION=P+"supervisor/CommandCompletion";
    static int checks;
    static void require(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    static ClassNode read(ZipFile jar,String name)throws Exception{ClassNode type=new ClassNode();new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getEntry(P+name+".class"))).readAllBytes()).accept(type,0);return type;}
    static int calls(ClassNode type,String method,String owner,String name){int n=0;for(MethodNode m:type.methods)if(m.name.equals(method))for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))n++;return n;}
    public static void main(String[] args)throws Exception{
        try(ZipFile jar=new ZipFile(args[0])){
            for(String kind:List.of("CollectFoodTask","CollectMeatTask")){
                ClassNode food=read(jar,"tasks/resources/"+kind);
                require(food.interfaces.contains(P+"tasks/base/TrackedTaskOutcomeProvider"),kind+" must expose a typed terminal outcome");
                require(calls(food,"onTick",FOOD,"before")==1,"missing bounded food progress/child-failure gate");
                require(calls(food,"onTick",P+"reliability/ProductionHandoff","pending")==1,"in-flight cooking must remain owned until output is collected");
                require(calls(food,"onTick",FOOD,"nextRawCount")==1,"hunt must target inventory gain");
                require(calls(food,"onTick",FOOD,"cookingTask")==1,"food must use verified station selection");
                require(calls(food,"getBestSmeltTarget",FOOD,"bestSmeltTarget")==1,"cooking target must include existing cooked inventory");
                require(calls(food,"isFinished",FOOD,"terminal")==1,"framework completion must use verified food state");
            }
            for(String kind:List.of("SmeltInSmokerTask","SmeltInFurnaceTask")){
                ClassNode cooking=read(jar,"tasks/container/"+kind);
                require(calls(cooking,"onResourceTick",FUEL,"before")==1,"cooking must retain fuel children");
                require(calls(cooking,"onResourceTick",FUEL,"issued")==1,"fuel dispatch must be observed at its actual native boundary");
                require(calls(cooking,"onResourceTick",FOOD,"remainingBatch")==1,"fuel must be sized from remaining output");
                require(calls(cooking,"onResourceStop",FUEL,"stopped")==1,"fuel ownership must be cleaned up");
                require(cooking.fields.stream().anyMatch(f->f.name.equals("ellieFoodOwner")),"per-food-job failed fuel strategies must survive new cooking batches");
            }
            ClassNode chain=read(jar,"chains/FoodChain");
            require(calls(chain,"getPriority",P+"reliability/FoodSurvival","before")==1,"failed automatic work must be observed before replacement");
            int exits=0;for(MethodNode m:chain.methods)if(m.name.equals("getPriority"))for(AbstractInsnNode i:m.instructions)if(i.getOpcode()==Opcodes.FRETURN){exits++;require(i.getPrevious() instanceof MethodInsnNode c&&c.owner.equals(P+"reliability/FoodSurvival")&&c.name.equals("priority"),"priority return must retain eating guards and explicit-job ownership");}
            require(exits>0,"missing FoodChain returns");
            ClassNode command=read(jar,"commands/base/Command");
            require(calls(command,"run",COMPLETION,"invoke")==1,"command must scope its own submission");
            require(calls(command,"finish",COMPLETION,"beforeFinish")==1,"plain finish must check/defer its receipt");
            require(calls(command,"finishWithNote",COMPLETION,"beforeFinish")==1,"notes must not hide failure");
            require(calls(read(jar,"executor/TaskStepExecutorAdapter"),"mapTerminalOutcome",COMPLETION,"taskOutcome")==1,"native worker outcomes must reach executor state");
            require(read(jar,"tasks/construction/PlaceSignTask").fields.stream().anyMatch(f->f.name.equals("ellieSignFailure")),"callback-based sign failures must retain a machine-readable outcome");
            require(read(jar,"tasks/entity/GiveItemToPlayerTask").methods.stream().anyMatch(m->m.name.equals("isFinished")),"successful giving must not self-stop as a failure");
            require(jar.stream().noneMatch(e->e.getName().startsWith("PackagedFoodLoopTest")||e.getName().startsWith("net/minecraft/")),"test boundaries must never ship");
            System.out.println("VerifyFoodReliability: "+checks+" packaged worker/receipt/ownership checks passed");
        }
    }
}
