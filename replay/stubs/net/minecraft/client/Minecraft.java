package net.minecraft.client;
/** Replay stub. Not shipped. No game engine or graphics context is loaded. */
public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public static Minecraft getInstance() { return INSTANCE; }
    public void execute(Runnable action) { action.run(); }
}
