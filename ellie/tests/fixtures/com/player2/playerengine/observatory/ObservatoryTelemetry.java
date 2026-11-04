package com.player2.playerengine.observatory;
/** Prevent local regression tests from writing game telemetry files. Never packaged. */
public final class ObservatoryTelemetry {
    public static void goalStart(Object goal) {}
    public static final java.util.Map<String,Object> fields=new java.util.HashMap<>();
    public static final java.util.List<String> events=new java.util.ArrayList<>();
    public static void set(String name,Object value){fields.put(name,value);}
    public static void taskState(Object task,Object state){}
    public static void taskFailure(Object task,Object detail){}
    public static void event(String name,String detail){events.add(name+": "+detail);}
}
