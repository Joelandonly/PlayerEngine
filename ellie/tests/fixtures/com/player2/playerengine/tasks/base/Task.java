package com.player2.playerengine.tasks.base;
/** Test boundary: real task behavior is not claimed by this harness. */
public class Task {
    public com.player2.playerengine.PlayerEngineController controller;
    public boolean isFinished() { return false; }
    public boolean stopped() { return false; }
    public void fail(String reason) {}
    public String getTaskTree() { return "test boundary"; }
}
