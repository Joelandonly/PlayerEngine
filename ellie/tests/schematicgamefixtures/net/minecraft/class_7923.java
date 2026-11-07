package net.minecraft;
import java.util.*;
public class class_7923 {
 public record Block(String id){public class_2680 method_9564(){return id.endsWith("_trapdoor")?class_2680.state(this,"open","false","waterlogged","false"):class_2680.state(this);}public Object method_8389(){return field_41178.method_10223(new class_2960(id));}public String toString(){return "Block{"+id+"}";}}
 public static class Registry {
  final boolean blocks;final Map<String,Object> entries=new HashMap<>();Registry(boolean blocks){this.blocks=blocks;}
  public Object method_10223(class_2960 id){return entries.computeIfAbsent(id.id(),k->blocks?new Block(k):new class_1747(k));}
  public Object method_10221(Object value){return value instanceof Block b?b.id():((class_1747)value).id;}
 }
 public static final Registry field_41175=new Registry(true),field_41178=new Registry(false);
}
