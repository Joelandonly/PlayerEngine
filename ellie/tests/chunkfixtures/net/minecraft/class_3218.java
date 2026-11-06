package net.minecraft;
import java.util.*;
/** Substituted vanilla boundary: persistent forced flags differ from effective entity-ticking tickets. */
public class class_3218 {
 public static class Forced {public final Set<Long> values=new HashSet<>();public boolean contains(long k){return values.contains(k);}}
 public static class Manager {public final Set<Long> ticking=new HashSet<>();public int reassertions;public void method_12124(class_1923 p,boolean enabled){long k=class_1923.method_8331(p.field_9181,p.field_9180);reassertions++;if(enabled)ticking.add(k);else ticking.remove(k);}}
 public final Forced forced=new Forced();public final Manager manager=new Manager();public boolean refuse;public int generates;public final Set<Long> generated=new HashSet<>();
 public Forced method_17984(){return forced;}public Manager method_14178(){return manager;}
 public boolean method_17988(int x,int z,boolean on){if(refuse)return false;long p=class_1923.method_8331(x,z);boolean changed=on?forced.values.add(p):forced.values.remove(p);if(changed){if(on&&generated.add(p))generates++;manager.method_12124(new class_1923(x,z),on);}return changed;}
}
