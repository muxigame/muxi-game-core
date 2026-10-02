package net.muxigame.transferprobe;
import java.lang.reflect.Method;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Executes original pinned third-party callback bytecode against dependency stubs. */
public final class ReplayOriginalHandler {
    public static void main(String[] args) throws Exception {
        IrisReloadManager.guarded = args.length > 0 && args[0].equals("guarded");
        Class<?> original = Class.forName("com.euphoriapatches.euphoria_patcher.neoforge.mixin.ReloadShadersOnDimensionChangeMixin");
        Object handler = original.getConstructor().newInstance();
        Method callback = original.getDeclaredMethod("onDimensionChange", CallbackInfo.class);
        callback.setAccessible(true);
        callback.invoke(handler, new Object[]{null}); // Initial login only establishes lastDimension.
        int loginReloads = IrisReloadManager.executed;
        String previous = "minecraft:overworld";
        String[] path = {"muxi_game_core:overworld", "muxi_game_core:adventure", "minecraft:overworld",
                         "muxi_game_core:overworld", "muxi_game_core:adventure", "minecraft:overworld",
                         "minecraft:overworld", "minecraft:the_nether"};
        for (String dimension : path) {
            IrisReloadManager.source = previous;
            IrisReloadManager.target = dimension;
            ModLoaderSpecifics.current = dimension;
            callback.invoke(handler, new Object[]{null});
            previous = dimension;
        }
        int expected = IrisReloadManager.guarded ? 2 : 8;
        if (loginReloads != 0 || IrisReloadManager.requested != 8 || IrisReloadManager.executed != expected)
            throw new AssertionError("unexpected original handler replay result");
        System.out.println("{\"originalHandlerExecuted\":true,\"dependencyStubs\":true,\"minecraftStarted\":false,"
                + "\"shaderCompileMeasured\":false,\"mode\":\"" + (IrisReloadManager.guarded ? "guarded" : "baseline")
                + "\",\"requested\":" + IrisReloadManager.requested + ",\"executed\":" + IrisReloadManager.executed
                + ",\"suppressed\":" + (IrisReloadManager.requested - IrisReloadManager.executed) + "}");
    }
}
