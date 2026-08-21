package com.mkt.signin.command;

public record SigninPublishCommand(Boolean confirm, Boolean early) {

    public boolean confirmTrue() {
        return Boolean.TRUE.equals(confirm);
    }

    public boolean earlyTrue() {
        return Boolean.TRUE.equals(early);
    }
}
