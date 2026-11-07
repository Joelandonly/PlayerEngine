package ellie.verification;
import java.util.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Compare native instructions and branch destinations, independent of debug/frame offsets. */
public final class NativeMethodShape {
 private NativeMethodShape(){}
 private static int target(LabelNode label,Map<AbstractInsnNode,Integer> positions,int end){
  for(AbstractInsnNode n=label;n!=null;n=n.getNext())if(n.getOpcode()>=0)return positions.getOrDefault(n,end);
  return end;
 }
 public static String method(MethodNode m){
  Map<AbstractInsnNode,Integer> positions=new IdentityHashMap<>();int end=0;
  for(AbstractInsnNode n:m.instructions)if(n.getOpcode()>=0)positions.put(n,end++);
  StringBuilder s=new StringBuilder(m.access+" "+m.name+m.desc+" "+m.signature+" "+m.exceptions+"\n");
  for(AbstractInsnNode n:m.instructions){
   if(n.getOpcode()<0)continue;s.append(n.getOpcode()).append(' ');
   if(n instanceof VarInsnNode v)s.append(v.var);
   else if(n instanceof FieldInsnNode f)s.append(f.owner).append('.').append(f.name).append(f.desc);
   else if(n instanceof MethodInsnNode c)s.append(c.owner).append('.').append(c.name).append(c.desc).append(c.itf);
   else if(n instanceof TypeInsnNode t)s.append(t.desc);
   else if(n instanceof LdcInsnNode l)s.append(l.cst);
   else if(n instanceof IntInsnNode i)s.append(i.operand);
   else if(n instanceof IincInsnNode i)s.append(i.var).append(':').append(i.incr);
   else if(n instanceof JumpInsnNode j)s.append(target(j.label,positions,end));
   else if(n instanceof InvokeDynamicInsnNode i)s.append(i.name).append(i.desc).append(i.bsm).append(Arrays.toString(i.bsmArgs));
   else if(n instanceof TableSwitchInsnNode t){s.append(t.min).append(':').append(t.max).append(':').append(target(t.dflt,positions,end));for(LabelNode l:t.labels)s.append(',').append(target(l,positions,end));}
   else if(n instanceof LookupSwitchInsnNode l){s.append(l.keys).append(':').append(target(l.dflt,positions,end));for(LabelNode v:l.labels)s.append(',').append(target(v,positions,end));}
   else if(n instanceof MultiANewArrayInsnNode a)s.append(a.desc).append(':').append(a.dims);
   s.append('\n');
  }
  for(TryCatchBlockNode t:m.tryCatchBlocks)s.append("catch ").append(t.type).append(':').append(target(t.start,positions,end)).append(':').append(target(t.end,positions,end)).append(':').append(target(t.handler,positions,end));
  return s.toString();
 }
 public static boolean equivalent(ClassNode a,ClassNode b){
  if(!Objects.equals(a.name,b.name)||a.access!=b.access||!Objects.equals(a.superName,b.superName)||!a.interfaces.equals(b.interfaces))return false;
  if(a.fields.size()!=b.fields.size()||a.methods.size()!=b.methods.size())return false;
  for(int i=0;i<a.fields.size();i++){FieldNode x=a.fields.get(i),y=b.fields.get(i);if(!Objects.equals(x.name,y.name)||!Objects.equals(x.desc,y.desc)||x.access!=y.access||!Objects.equals(x.value,y.value))return false;}
  for(int i=0;i<a.methods.size();i++)if(!method(a.methods.get(i)).equals(method(b.methods.get(i))))return false;
  return true;
 }
}
