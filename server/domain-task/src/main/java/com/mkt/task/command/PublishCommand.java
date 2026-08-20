package com.mkt.task.command;

/** Publish body (design §4.4). {@code confirm} required only when pending_revision=1. */
public record PublishCommand(Boolean confirm, Boolean early) {

    public boolean confirmTrue() {
        return Boolean.TRUE.equals(confirm);
    }

    public boolean earlyTrue() {
        return Boolean.TRUE.equals(early);
    }
}
