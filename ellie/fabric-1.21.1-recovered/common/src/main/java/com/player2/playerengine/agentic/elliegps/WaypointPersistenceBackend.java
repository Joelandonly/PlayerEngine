package com.player2.playerengine.agentic.elliegps;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface WaypointPersistenceBackend {
   void persist(Path var1, String var2) throws IOException;
}
