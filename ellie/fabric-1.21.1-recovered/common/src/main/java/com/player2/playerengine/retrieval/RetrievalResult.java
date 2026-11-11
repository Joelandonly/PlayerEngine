package com.player2.playerengine.retrieval;

import java.util.List;

public record RetrievalResult(List<RetrievalHit> hits, RetrievalConfidence confidence) {
}
