import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Shared feet/support boundaries only; native movement/input/bridge bodies remain intact. */
public final class PatchPartialBlockNavigation implements Opcodes {
    static final String P="com/player2/playerengine/automaton/",H="com/player2/playerengine/reliability/PartialBlockSupport";
    static ClassNode read(ZipFile jar,Path out,String name)throws Exception {
        Path path=out.resolve(name+".class");
        ClassNode c=new ClassNode();new ClassReader(Files.exists(path)?Files.readAllBytes(path):jar.getInputStream(Objects.requireNonNull(jar.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;
    }
    static void write(ClassNode c,Path out)throws Exception {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);try(ZipFile jar=new ZipFile(args[0])) {
            ClassNode ctx=read(jar,out,P+"utils/player/EntityContext");
            MethodNode feet=ctx.methods.stream().filter(m->m.name.equals("feetPos")).findFirst().orElseThrow();
            feet.instructions.clear();feet.tryCatchBlocks.clear();feet.localVariables=null;
            feet.visibleLocalVariableAnnotations=null;feet.invisibleLocalVariableAnnotations=null;
            InsnList i=feet.instructions;
            for(String getter:List.of("method_23317","method_23318","method_23321")) {
                i.add(new VarInsnNode(ALOAD,0));i.add(new MethodInsnNode(INVOKEVIRTUAL,ctx.name,"entity","()Lnet/minecraft/class_1309;",false));
                i.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/class_1309",getter,"()D",false));
                i.add(new VarInsnNode(DSTORE,1+List.of("method_23317","method_23318","method_23321").indexOf(getter)*2));
            }
            i.add(new TypeInsnNode(NEW,P+"api/utils/BetterBlockPos"));i.add(new InsnNode(DUP));i.add(new VarInsnNode(DLOAD,1));
            i.add(new VarInsnNode(ALOAD,0));for(int n:new int[]{1,3,5})i.add(new VarInsnNode(DLOAD,n));
            i.add(new MethodInsnNode(INVOKESTATIC,H,"feetY","(Ljava/lang/Object;DDD)I",false));i.add(new InsnNode(I2D));i.add(new VarInsnNode(DLOAD,5));
            i.add(new MethodInsnNode(INVOKESPECIAL,P+"api/utils/BetterBlockPos","<init>","(DDD)V",false));i.add(new InsnNode(ARETURN));
            write(ctx,out);

            ClassNode helper=read(jar,out,P+"pathing/movement/MovementHelper");int hooks=0;
            for(MethodNode m:helper.methods)if(m.name.equals("canWalkOn")&&m.desc.contains("Lnet/minecraft/class_2680;")) {
                if(!m.desc.startsWith("(L"+P+"utils/BlockStateInterface;III"))continue;
                for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof TypeInsnNode check&&check.getOpcode()==INSTANCEOF&&check.desc.equals("net/minecraft/class_2510")) {
                    LabelNode nativeResult=new LabelNode();InsnList fallback=new InsnList();fallback.add(new InsnNode(DUP));fallback.add(new JumpInsnNode(IFNE,nativeResult));fallback.add(new InsnNode(POP));
                    fallback.add(new VarInsnNode(ALOAD,0));for(int v:new int[]{1,2,3})fallback.add(new VarInsnNode(ILOAD,v));
                    fallback.add(new VarInsnNode(ALOAD,4));fallback.add(new VarInsnNode(ALOAD,5));
                    fallback.add(new MethodInsnNode(INVOKESTATIC,H,"extraSupport","(Ljava/lang/Object;IIILjava/lang/Object;Ljava/lang/Object;)Z",false));
                    fallback.add(nativeResult);fallback.add(new FrameNode(F_SAME1,0,null,1,new Object[]{INTEGER}));m.instructions.insert(n,fallback);hooks++;
                }
            }
            if(hooks!=1)throw new AssertionError("Expected one unsupported-floor boundary, got "+hooks);
            int through=0;
            for(MethodNode m:helper.methods)if(m.name.equals("canWalkThrough")&&m.desc.startsWith("(L"+P+"utils/BlockStateInterface;III")&&m.desc.contains("Lnet/minecraft/class_2680;")){
                LabelNode nativeThrough=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));for(int v=1;v<=3;v++)gate.add(new VarInsnNode(ILOAD,v));gate.add(new VarInsnNode(ALOAD,4));
                gate.add(new MethodInsnNode(INVOKESTATIC,H,"blocksWaterloggedBody","(Ljava/lang/Object;IIILjava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFEQ,nativeThrough));gate.add(new InsnNode(ICONST_0));gate.add(new InsnNode(IRETURN));
                gate.add(nativeThrough);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);through++;
            }
            if(through!=1)throw new AssertionError("Expected one waterlogged body boundary");write(helper,out);

            ClassNode ascend=read(jar,out,P+"pathing/movement/movements/MovementAscend");int gates=0;
            for(MethodNode m:ascend.methods)if(m.name.equals("cost")&&(m.access&ACC_STATIC)!=0) {
                LabelNode nativeCost=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));for(int v=1;v<=5;v++)gate.add(new VarInsnNode(ILOAD,v));
                gate.add(new MethodInsnNode(INVOKESTATIC,H,"allowAscent","(Ljava/lang/Object;IIIII)Z",false));gate.add(new JumpInsnNode(IFNE,nativeCost));gate.add(new InsnNode(RETURN));
                gate.add(nativeCost);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);gates++;
            }
            if(gates!=1)throw new AssertionError("Expected one ascend-cost boundary");write(ascend,out);
            ClassNode movement=read(jar,out,P+"pathing/movement/Movement");int inputs=0;
            for(MethodNode m:movement.methods)if(m.name.equals("update"))for(AbstractInsnNode n:m.instructions.toArray())
                if(n instanceof MethodInsnNode call&&call.name.equals("updateState")&&n.getNext() instanceof FieldInsnNode store&&store.name.equals("currentState")) {
                    InsnList step=new InsnList();step.add(new VarInsnNode(ALOAD,0));step.add(new VarInsnNode(ALOAD,0));
                    step.add(new FieldInsnNode(GETFIELD,movement.name,"currentState","L"+P+"pathing/movement/MovementState;"));
                    step.add(new MethodInsnNode(INVOKESTATIC,H,"adjustStepInput","(Ljava/lang/Object;Ljava/lang/Object;)V",false));m.instructions.insert(store,step);inputs++;
                }
            if(inputs!=1)throw new AssertionError("Expected one post-native-state input boundary");write(movement,out);
        }
        System.out.println("Partial-block navigation: collision-supported feet, safe partial floors and rise check; bridge bodies unchanged");
    }
}
