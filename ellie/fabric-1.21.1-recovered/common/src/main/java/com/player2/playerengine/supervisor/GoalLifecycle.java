/*
 * Recovered from the pinned Ellie baseline with CFR 0.152; lifecycle validation hardened.
 */
package com.player2.playerengine.supervisor;

public final class GoalLifecycle {
    private long nextGeneration;
    private Snapshot current;

    public synchronized Snapshot begin(String string, String string2) {
        ++this.nextGeneration;
        this.current = new Snapshot(this.nextGeneration, GoalLifecycle.safe(string), GoalLifecycle.safe(string2), Status.RUNNING, "submitted to PlayerEngine");
        return this.current;
    }

    public synchronized Snapshot finish(long l, Status status, String string) {
        java.util.Objects.requireNonNull(status, "Terminal status required");
        if (status == Status.RUNNING || status == Status.SUSPENDED) {
            throw new IllegalArgumentException("Terminal status required");
        }
        if (this.current == null || this.current.generation() != l || this.current.status() == Status.SUCCEEDED || this.current.status() == Status.BLOCKED || this.current.status() == Status.FAILED || this.current.status() == Status.CANCELLED || this.current.status() == Status.SUPERSEDED) {
            return null;
        }
        this.current = new Snapshot(this.current.generation(), this.current.commandId(), this.current.kind(), status, GoalLifecycle.safe(string));
        return this.current;
    }

    public synchronized Snapshot suspend(long l, String string) {
        if (this.current == null || this.current.generation() != l || this.current.status() != Status.RUNNING) {
            return null;
        }
        this.current = new Snapshot(this.current.generation(), this.current.commandId(), this.current.kind(), Status.SUSPENDED, GoalLifecycle.safe(string));
        return this.current;
    }

    public synchronized Snapshot resume(long l) {
        if (this.current == null || this.current.generation() != l || this.current.status() != Status.SUSPENDED) {
            return null;
        }
        this.current = new Snapshot(this.current.generation(), this.current.commandId(), this.current.kind(), Status.RUNNING, "resumed by native task chain");
        return this.current;
    }

    public synchronized Snapshot current() {
        return this.current;
    }

    private static String safe(String string) {
        if (string == null) {
            return "";
        }
        String string2 = string.replace('\n', ' ').replace('\r', ' ').replace('\u0000', ' ');
        return string2.substring(0, Math.min(string2.length(), 240));
    }

    public record Snapshot(long generation, String commandId, String kind, Status status, String detail) {
    }

    public static enum Status {
        RUNNING,
        SUSPENDED,
        SUCCEEDED,
        BLOCKED,
        FAILED,
        SUPERSEDED,
        CANCELLED;

    }
}
