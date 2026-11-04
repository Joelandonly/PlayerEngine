package com.player2.playerengine.nativecommands;
import java.lang.reflect.*;
/** Small game boundary, with exact runtime type matching and no model dependencies. */
public final class NativeAccess {
 private NativeAccess() {}
 public static Object call(Object object,String name,Object...args)throws Exception{return invoke(object.getClass(),object,name,args);}
 public static Object callStatic(String type,String name,Object...args)throws Exception{return invoke(Class.forName(type),null,name,args);}
 private static Object invoke(Class<?> type,Object object,String name,Object[] args)throws Exception {
  for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
   Class<?>[] types=m.getParameterTypes();boolean fits=true;
   for(int i=0;i<args.length;i++)if(args[i]!=null&&!box(types[i]).isInstance(args[i])){fits=false;break;}
   if(fits){m.setAccessible(true);try{return m.invoke(object,args);}catch(InvocationTargetException e){throw new ReflectiveOperationException(name,e.getCause());}}
  }
  throw new NoSuchMethodException(type.getName()+"."+name);
 }
 private static Class<?> box(Class<?> c){if(!c.isPrimitive())return c;if(c==int.class)return Integer.class;if(c==boolean.class)return Boolean.class;if(c==double.class)return Double.class;if(c==long.class)return Long.class;if(c==float.class)return Float.class;return c;}
 public static Field field(Object object,String name)throws Exception {
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 public static Object get(Object object,String name)throws Exception{return field(object,name).get(object);}
 public static void set(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
}
