import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Tighten only crafting-table approach. Do not change all navigation's arrival rules. */
public final class PatchCraftApproach implements Opcodes {
    public static void main(String[] args)throws Exception{
        String entry="com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.class";
        ClassNode node=new ClassNode();
        try(ZipFile jar=new ZipFile(args[0])){new ClassReader(jar.getInputStream(jar.getEntry(entry)).readAllBytes()).accept(node,0);}
        int matches=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof MethodInsnNode call && call.owner.equals("com/player2/playerengine/tasks/movement/GetWithinRangeOfBlockTask") && call.name.equals("<init>") && call.desc.equals("(Lnet/minecraft/class_2338;I)V")){
                AbstractInsnNode previous=call.getPrevious();
                while(previous!=null && previous.getOpcode()<0)previous=previous.getPrevious();
                if(previous==null||previous.getOpcode()!=ICONST_3)throw new IllegalStateException("Unreviewed crafting approach radius");
                method.instructions.set(previous,new InsnNode(ICONST_1));matches++;
            }
        }
        if(matches!=1)throw new IllegalStateException("Expected one crafting approach, got "+matches);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);
        Path out=Path.of(args[1],entry);Files.createDirectories(out.getParent());Files.write(out,writer.toByteArray());
        System.out.println("Crafting approach: reviewed radius 3 -> 1; native world reach check retained");
    }
}
