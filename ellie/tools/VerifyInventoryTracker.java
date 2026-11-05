import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
public final class VerifyInventoryTracker {
 public static void main(String[] args)throws Exception{
  try(ZipFile z=new ZipFile(args[0])){
   ClassNode n=new ClassNode();new ClassReader(z.getInputStream(z.getEntry("com/player2/playerengine/trackers/storage/InventorySubTracker.class")).readAllBytes()).accept(n,0);
   MethodNode m=n.methods.stream().filter(x->x.name.equals("hasEmptySlot")&&x.desc.equals("()Z")).findFirst().orElseThrow();
   int hooks=0;for(AbstractInsnNode x:m.instructions)if(x instanceof MethodInsnNode mm&&mm.owner.equals("com/player2/playerengine/agentic/InventorySpace")&&mm.name.equals("hasEmptyMainSlot"))hooks++;
   if(hooks!=1)throw new AssertionError("hasEmptySlot must inspect actual 36 main/hotbar stacks");
   System.out.println("PASS packaged InventorySubTracker.hasEmptySlot uses actual main slots, not cached AIR counts/maps");
  }
 }
}
