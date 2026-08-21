package com.mkt.activity.command;

public record ActivityPublishCommand(Boolean confirm, Boolean early) {

    public boolean confirmTrue() {
        return Boolean.TRUE.equals(confirm);
    }

    public boolean earlyTrue() {
        return Boolean.TRUE.equals(early);
    }
}
