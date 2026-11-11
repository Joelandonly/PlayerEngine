package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.retrieval.RetrievalHit;
import java.util.List;

public interface WaypointIndexBackend {
   boolean isHealthyFor(String var1);

   WaypointIndexUpdateStatus synchronize(EllieGPSStore var1);

   List<RetrievalHit> query(String var1, int var2) throws Exception;
}
