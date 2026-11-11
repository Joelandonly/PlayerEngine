package com.player2.playerengine.nativecommands;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Small game boundary, with exact runtime type matching and no model dependencies. */
public final class NativeAccess {
 private NativeAccess() {}
 private static final Map<Class<?>,Map<List<Object>,Method>> METHODS=new ConcurrentHashMap<>();
 private static final Map<Class<?>,Map<String,Field>> FIELDS=new ConcurrentHashMap<>();
 public static Object call(Object object,String name,Object...args)throws Exception{return invoke(object.getClass(),object,name,args);}
 public static Object callStatic(String type,String name,Object...args)throws Exception{return invoke(Class.forName(type),null,name,args);}
 private static Object invoke(Class<?> type,Object object,String name,Object[] args)throws Exception {
  List<Object> key=new ArrayList<>();key.add(name);for(Object arg:args)key.add(arg==null?Void.class:arg.getClass());
  Map<List<Object>,Method> methods=METHODS.computeIfAbsent(type,k->new ConcurrentHashMap<>());Method cached=methods.get(key);
  if(cached!=null){try{return cached.invoke(object,args);}catch(InvocationTargetException e){throw new ReflectiveOperationException(name,e.getCause());}}
  for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
   Class<?>[] types=m.getParameterTypes();boolean fits=true;
   for(int i=0;i<args.length;i++)if(args[i]!=null&&!box(types[i]).isInstance(args[i])){fits=false;break;}
   if(fits){m.setAccessible(true);methods.put(key,m);try{return m.invoke(object,args);}catch(InvocationTargetException e){throw new ReflectiveOperationException(name,e.getCause());}}
  }
  throw new NoSuchMethodException(type.getName()+"."+name);
 }
 private static Class<?> box(Class<?> c){if(!c.isPrimitive())return c;if(c==int.class)return Integer.class;if(c==boolean.class)return Boolean.class;if(c==double.class)return Double.class;if(c==long.class)return Long.class;if(c==float.class)return Float.class;return c;}
 public static Field field(Object object,String name)throws Exception {
  Map<String,Field> fields=FIELDS.computeIfAbsent(object.getClass(),k->new ConcurrentHashMap<>());Field cached=fields.get(name);if(cached!=null)return cached;
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);fields.put(name,f);return f;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 public static Object get(Object object,String name)throws Exception{return field(object,name).get(object);}
 public static void set(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
}
