package org.slf4j;
public final class LoggerFactory {private static final Logger NOP=new Logger(){};public static Logger getLogger(Class<?> type){return NOP;}}
