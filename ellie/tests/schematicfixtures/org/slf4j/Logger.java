package org.slf4j;
/** Logging boundary only; schematic4j itself is the real bundled parser. */
public interface Logger {
 default void trace(String s,Object... a){} default void debug(String s,Object... a){} default void info(String s,Object... a){} default void warn(String s,Object... a){}
 default void trace(String s){} default void trace(String s,Object a){} default void trace(String s,Object a,Object b){}
 default void debug(String s){} default void debug(String s,Object a){} default void debug(String s,Object a,Object b){}
 default void info(String s,Object a){} default void warn(String s){} default void warn(String s,Object a){} default void warn(String s,Object a,Object b){}
}
