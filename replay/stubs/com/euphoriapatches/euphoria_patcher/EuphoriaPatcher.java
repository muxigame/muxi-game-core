package com.euphoriapatches.euphoria_patcher;
public final class EuphoriaPatcher {
    public static void log(int category, int level, String message) { throw new AssertionError(message); }
}
