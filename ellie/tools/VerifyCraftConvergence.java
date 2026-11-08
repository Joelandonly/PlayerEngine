import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import ellie.verification.NativeMethodShape;

/** Freeze every previous native macro method after removing the one reviewed handoff hook. */
public final class VerifyCraftConvergence implements Opcodes {
 static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 public static void main(String[] args)throws Exception{
  ClassNode type=new ClassNode();try(ZipFile z=new ZipFile(args[0])){new ClassReader(z.getInputStream(z.getEntry("com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.class")).readAllBytes()).accept(type,0);}
  if(!args[1].equals("--record")){
   if(type.fields.stream().filter(f->f.name.startsWith("ellieCollectHandoff")).count()!=2)throw new AssertionError("Missing bounded handoff state");
   int hooks=0;for(MethodNode method:type.methods)if(method.name.equals("resolveNow"))for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call&&call.name.equals("ellieCollectionHandoff")){
    AbstractInsnNode before=call.getPrevious(),branch=call.getNext(),zero=branch.getNext(),ret=zero.getNext();
    if(!(before instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==0)||branch.getOpcode()!=IFEQ||zero.getOpcode()!=ICONST_0||ret.getOpcode()!=IRETURN)throw new AssertionError("Unknown handoff hook");
    for(AbstractInsnNode n:List.of(before,call,branch,zero,ret))method.instructions.remove(n);hooks++;
   }
   if(hooks!=1||type.methods.stream().filter(m->m.name.equals("ellieCollectionHandoff")).count()!=1)throw new AssertionError("Handoff hook count changed");
   type.methods.removeIf(m->m.name.equals("ellieCollectionHandoff"));type.fields.removeIf(f->f.name.startsWith("ellieCollectHandoff"));
  }
  TreeMap<String,String> actual=new TreeMap<>();
  for(MethodNode method:type.methods)actual.put("method."+method.name+method.desc,hash(NativeMethodShape.method(method)));
  StringBuilder fields=new StringBuilder();for(FieldNode f:type.fields)fields.append(f.access).append(' ').append(f.name).append(f.desc).append(' ').append(f.value).append('\n');actual.put("fields",hash(fields.toString()));
  if(args[1].equals("--record")){for(var e:actual.entrySet())System.out.println(e.getKey()+"="+e.getValue());return;}
  TreeMap<String,String> expected=new TreeMap<>();for(String line:Files.readAllLines(Path.of(args[1]))){if(!line.isBlank()&&!line.startsWith("#")){int split=line.indexOf('=');expected.put(line.substring(0,split),line.substring(split+1));}}
  if(!actual.equals(expected))throw new AssertionError("Unrelated native macro method or field changed");
  System.out.println("VerifyCraftConvergence: all "+(actual.size()-1)+" original macro methods and fields match delivered v18.2 after removing the single handoff hook");
 }
}
