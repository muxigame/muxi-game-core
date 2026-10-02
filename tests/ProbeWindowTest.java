package net.muxigame.transferprobe;

public final class ProbeWindowTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        ProbeWindow w = new ProbeWindow("one", "minecraft:overworld", "muxi_game_core:overworld", 10);
        check(w.take(10, "chunk", true));
        check(!w.take(11, "chunk", true));
        check(!w.take(9, "clock_backwards", false));
        check(!w.take(10 + ProbeWindow.LIMIT_NANOS, "expired", false));
        for (int i = 0; i < 255; i++) check(w.take(12, "batch", false));
        check(!w.take(12, "too_many", false));
        check(w.take(12, "screen_close", true));
        check(!w.take(12, "screen_close", true));
        check(ProbeWindow.dimensionReloadFrame("net.minecraft.client.Minecraft",
                "mdea6ddb$euphoria_patcher$lambda$onDimensionChange$0$3"));
        check(!ProbeWindow.dimensionReloadFrame("net.minecraft.client.Minecraft", "reloadResources"));
        check(!ProbeWindow.dimensionReloadFrame("other.Minecraft", "euphoria_patcher$lambda$onDimensionChange"));
        check(ProbeWindow.maySkipExtraReload(true, true, "minecraft:overworld", "muxi_game_core:adventure"));
        check(!ProbeWindow.maySkipExtraReload(false, true, "minecraft:overworld", "muxi_game_core:adventure"));
        check(!ProbeWindow.maySkipExtraReload(true, false, "minecraft:overworld", "muxi_game_core:adventure"));
        check(!ProbeWindow.maySkipExtraReload(true, true, null, "muxi_game_core:adventure"));
        check(!ProbeWindow.maySkipExtraReload(true, true, "minecraft:overworld", null));
        check(!ProbeWindow.maySkipExtraReload(true, true, "minecraft:overworld", "minecraft:overworld"));
        check(!ProbeWindow.maySkipExtraReload(true, true, "minecraft:overworld", "minecraft:the_nether"));
        System.out.println("ProbeWindowTest passed: " + checks + " checks; no Minecraft started");
    }
}
