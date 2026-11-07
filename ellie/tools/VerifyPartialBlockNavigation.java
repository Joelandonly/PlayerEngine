import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Strip only the exact reviewed injections and compare all remaining native method bodies. */
public final class VerifyPartialBlockNavigation implements Opcodes {
    static final String P="com/player2/playerengine/automaton/",H="com/player2/playerengine/reliability/PartialBlockSupport";
    static int checks;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static ClassNode read(ZipFile z,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(P+name+".class")).readAllBytes()).accept(c,0);return c;}
    static boolean same(ClassNode a,ClassNode b){return ellie.verification.NativeMethodShape.equivalent(a,b);}
    static MethodNode method(ClassNode c,String name){return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static void removeThroughFrame(MethodNode m){
        for(AbstractInsnNode n:m.instructions.toArray()){m.instructions.remove(n);if(n instanceof FrameNode)return;}
        throw new AssertionError("No injected gate frame");
    }
    public static void main(String[] args)throws Exception {
        try(ZipFile built=new ZipFile(args[0]);ZipFile original=new ZipFile(args[1])) {
            ClassNode old=read(original,"utils/player/EntityContext"),now=read(built,"utils/player/EntityContext");
            MethodNode feet=method(now,"feetPos");int footCalls=0;
            for(AbstractInsnNode n:feet.instructions)if(n instanceof MethodInsnNode c){require(!c.name.contains("getChunk"),"no live chunk fetch in feet method");if(c.owner.equals(H)&&c.name.equals("feetY"))footCalls++;}
            require(footCalls==1,"one shared geometry feet hook");old.methods.removeIf(m->m.name.equals("feetPos"));now.methods.removeIf(m->m.name.equals("feetPos"));
            require(same(old,now),"all other entity-context methods preserved");

            old=read(original,"pathing/movement/MovementHelper");now=read(built,"pathing/movement/MovementHelper");int support=0;
            int water=0;
            for(MethodNode m:now.methods)if(m.name.equals("canWalkThrough")){
                boolean guarded=false;for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode c&&c.owner.equals(H)&&c.name.equals("blocksWaterloggedBody"))guarded=true;
                if(guarded){removeThroughFrame(m);water++;}
            }
            require(water==1,"one solid-waterlogged body gate");
            for(MethodNode m:now.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode c&&c.owner.equals(H)){
                require(m.name.equals("canWalkOn")&&c.name.equals("extraSupport"),"only unsupported-floor fallback may change helper");
                AbstractInsnNode start=n;while(!(start instanceof TypeInsnNode t&&t.getOpcode()==INSTANCEOF&&t.desc.equals("net/minecraft/class_2510")))start=start.getPrevious();
                AbstractInsnNode at=start.getNext();while(at!=null){AbstractInsnNode next=at.getNext();m.instructions.remove(at);if(at instanceof FrameNode)break;at=next;}support++;
            }
            require(support==1,"exactly one fallback");require(same(old,now),"all native passability and support logic remains after fallback removal");

            old=read(original,"pathing/movement/movements/MovementAscend");now=read(built,"pathing/movement/movements/MovementAscend");
            MethodNode cost=method(now,"cost");require(java.util.stream.StreamSupport.stream(cost.instructions.spliterator(),false).anyMatch(n->n instanceof MethodInsnNode c&&c.owner.equals(H)&&c.name.equals("allowAscent")),"partial-rise cost gate exists");
            removeThroughFrame(cost);require(same(old,now),"all native ascend bodies preserved after reach gate");

            old=read(original,"pathing/movement/Movement");now=read(built,"pathing/movement/Movement");MethodNode update=method(now,"update");int input=0;
            for(AbstractInsnNode n:update.instructions.toArray())if(n instanceof MethodInsnNode c&&c.owner.equals(H)){
                require(c.name.equals("adjustStepInput"),"only reviewed step input hook");AbstractInsnNode first=n;
                for(int k=0;k<3;k++)first=first.getPrevious();
                require(first.getPrevious() instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("currentState"),"step check follows native movement decision");
                for(int k=0;k<4;k++){AbstractInsnNode next=first.getNext();update.instructions.remove(first);first=next;}input++;
            }
            require(input==1,"one post-native step hook");
            require(update.instructions.getFirst().getNext() instanceof MethodInsnNode c&&c.owner.endsWith("/SafeDescent")&&c.name.equals("rejectMovement"),"pre-input descent protection retained");
            removeThroughFrame(update);require(same(old,now),"native movement body unchanged after existing descent/step hooks");
            for(String name:List.of("MovementTraverse","MovementDiagonal","MovementDescend","MovementFall","MovementDownward","MovementPillar","MovementParkour"))
                require(Arrays.equals(built.getInputStream(built.getEntry(P+"pathing/movement/movements/"+name+".class")).readAllBytes(),
                        original.getInputStream(original.getEntry(P+"pathing/movement/movements/"+name+".class")).readAllBytes()),"native movement/bridge body unchanged: "+name);
        }
        System.out.println("VerifyPartialBlockNavigation: "+checks+" packaged hook and native-body preservation checks passed");
    }
}
