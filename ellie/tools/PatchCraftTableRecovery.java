import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Review-exact ASM hooks; no changes to Player2 AI, mining or base construction. */
public final class PatchCraftTableRecovery implements Opcodes {
  static final String HOOK="com/player2/playerengine/tasks/crafting/CraftTableApproachRecovery";
  static final String PREFIX="com/player2/playerengine/tasks/";
  public static void main(String[] args)throws Exception{
    if(args.length!=2)throw new IllegalArgumentException("baseline output-dir");
    try(ZipFile z=new ZipFile(args[0])){
      patchTask(z,args[1]);
      patchLocator(z,args[1]);
    }
  }
  static ClassNode read(ZipFile z,String name)throws Exception{
    ClassNode node=new ClassNode();
    new ClassReader(z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes()).accept(node,0);
    return node;
  }
  static void save(ClassNode n,String out)throws Exception{
    ClassWriter w=new ClassWriter(0);n.accept(w);
    Path p=Path.of(out,n.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
  }
  static void patchTask(ZipFile z,String out)throws Exception{
    ClassNode n=read(z,PREFIX+"container/CraftInTableTask");
    int match=0;
    for(MethodNode m:n.methods)if(m.name.equals("onResourceTick")&&m.desc.equals("(Lcom/player2/playerengine/PlayerEngineController;)Lcom/player2/playerengine/tasks/base/Task;")){
      InsnList i=new InsnList();
      i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,1));
      i.add(new MethodInsnNode(INVOKESTATIC,HOOK,"craftTick","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
      m.instructions.insert(i);m.maxStack=Math.max(m.maxStack,2);match++;
    }
    if(match!=1)throw new IllegalStateException("Unreviewed CraftInTableTask signature: "+match);
    save(n,out);
  }
  static void patchLocator(ZipFile z,String out)throws Exception{
    ClassNode n=read(z,PREFIX+"crafting/CraftingTableLocator");
    Map<String,Integer> counts=new HashMap<>();
    for(MethodNode m:n.methods){
      if(!m.name.equals("findReachable")&&!m.name.equals("findReusableNearby"))continue;
      String helper=m.name.equals("findReachable")?"filterReachable":"filterReusable";
      int total=0;
      for(AbstractInsnNode insn=m.instructions.getFirst();insn!=null;){
        AbstractInsnNode next=insn.getNext();
        if(insn.getOpcode()==ARETURN){
          InsnList inject=new InsnList();
          inject.add(new VarInsnNode(ALOAD,0));inject.add(new InsnNode(SWAP));
          inject.add(new MethodInsnNode(INVOKESTATIC,HOOK,helper,"(Ljava/lang/Object;Ljava/util/Optional;)Ljava/util/Optional;",false));
          m.instructions.insertBefore(insn,inject);total++;
        }
        insn=next;
      }
      m.maxStack=Math.max(m.maxStack,2);
      counts.put(m.name,total);
    }
    if(counts.size()!=2||counts.get("findReachable")<1||counts.get("findReusableNearby")<1)
       throw new IllegalStateException("Unexpected locator structure "+counts);
    save(n,out);System.out.println("Crafting table recovery hooks installed: "+counts);
  }
}
