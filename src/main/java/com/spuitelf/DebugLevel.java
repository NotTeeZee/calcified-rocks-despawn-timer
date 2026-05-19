package com.spuitelf;

public enum DebugLevel {
    NONE(0),
    BASIC(1);
    private final int value;

    private DebugLevel(int value) {
        this.value = value;
    }

    public boolean shouldShow(DebugLevel userLevel) {
        return userLevel.value >= this.value;
    }
}

