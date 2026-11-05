import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Fix the upstream false-full inventory check without touching the item counters.
 * Empty ItemStack has itemCount==0, so looking up AIR in itemCountsPlayer always returns zero.
 * itemToSlotPlayer records main-inventory indexes even for zero-count empty stacks.
 */
public final class PatchInventoryTracker implements Opcodes {
 public static void main(String[] args) throws Exception {
  String path="com/player2/playerengine/trackers/storage/InventorySubTracker";
  try(ZipFile jar=new ZipFile(args[0])) {
   ClassNode cls=new ClassNode();new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getEntry(path+".class"))).readAllBytes()).accept(cls,0);
   MethodNode method=cls.methods.stream().filter(m->m.name.equals("hasEmptySlot")&&m.desc.equals("()Z")).findFirst().orElseThrow();
   // Fail closed if upstream changes its internal tracker representation.
   if(cls.fields.stream().noneMatch(f->f.name.equals("itemToSlotPlayer")&&f.desc.equals("Ljava/util/Map;")))throw new AssertionError("Item-to-main-slot map missing");
   method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;
   InsnList i=method.instructions;
   i.add(new VarInsnNode(ALOAD,0));
   i.add(new FieldInsnNode(GETFIELD,path,"mod","Lcom/player2/playerengine/PlayerEngineController;"));
   i.add(new MethodInsnNode(INVOKESTATIC,"com/player2/playerengine/agentic/InventorySpace","hasEmptyMainSlot","(Ljava/lang/Object;)Z",false));
   i.add(new InsnNode(IRETURN));
   method.maxLocals=2;
   ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);cls.accept(w);
   Path out=Path.of(args[1],path+".class");Files.createDirectories(out.getParent());Files.write(out,w.toByteArray());
   System.out.println("Inventory: patched false-full slot test; actual 36 main/hotbar stacks; counts and slot maps preserved");
  }
 }
}
