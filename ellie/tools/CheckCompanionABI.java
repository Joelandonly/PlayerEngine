import java.util.*;import java.util.zip.*;import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Optional static compatibility audit. Does not execute any uploaded mod code. */
public final class CheckCompanionABI {
 static Map<String,ClassNode> read(String path)throws Exception{Map<String,ClassNode> classes=new HashMap<>();try(ZipFile z=new ZipFile(path)){for(var e:Collections.list(z.entries()))if(e.getName().endsWith(".class")){ClassNode c=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(c.name,c);}}return classes;}
 static boolean member(Map<String,ClassNode> classes,String owner,String name,String desc,boolean method,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;ClassNode c=classes.get(owner);if(c==null)return false;
  if(method?c.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)):c.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc)))return true;
  if(name.equals("<init>"))return false;
  if(member(classes,c.superName,name,desc,method,seen))return true;for(String itf:c.interfaces)if(member(classes,itf,name,desc,method,seen))return true;return false;
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("baseline-engine candidate-engine companion-jar");
  var old=read(args[0]);var current=read(args[1]);var npc=read(args[2]);int checked=0;Set<String> missing=new TreeSet<>();
  for(ClassNode c:npc.values())for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions){String owner,name,desc;boolean method;
   if(n instanceof MethodInsnNode call){owner=call.owner;name=call.name;desc=call.desc;method=true;}else if(n instanceof FieldInsnNode field){owner=field.owner;name=field.name;desc=field.desc;method=false;}else continue;
   if(!owner.startsWith("com/player2/playerengine/")||!member(old,owner,name,desc,method,new HashSet<>()))continue;checked++;
   if(!member(current,owner,name,desc,method,new HashSet<>()))missing.add(owner+"."+name+desc);
  }
  if(!missing.isEmpty())throw new AssertionError("Newly missing companion references: "+missing);
  System.out.println("Companion ABI: "+checked+" previously resolved engine member references, zero newly missing (static audit only)");
 }
}
