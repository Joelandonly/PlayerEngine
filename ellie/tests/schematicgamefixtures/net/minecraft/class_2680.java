package net.minecraft;
import java.util.*;
/** Immutable block/property boundary for the real imported-layer proxy. */
public record class_2680(Object block,Map<Property,String> values) {
 public record Property(String name){public java.util.Optional<String> method_11900(String value){return java.util.Optional.of(value);}public String method_11899(){return name;}public String method_11901(String value){return value;}}
 public boolean method_26215(){return String.valueOf(block).contains(":air}");}public boolean method_26234(Object world,Object pos){return !method_26215();}
 public class_2680 method_11657(Property p,String value){Map<Property,String> m=new HashMap<>(values);m.put(p,value);return new class_2680(block,Map.copyOf(m));}
 public Object method_26204(){return block;}public Collection<Property> method_28501(){return values.keySet();}public String method_11654(Property p){return values.get(p);}
 public static class_2680 state(Object block,String... pairs){Map<Property,String> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(new Property(pairs[i]),pairs[i+1]);return new class_2680(block,Map.copyOf(m));}
}
