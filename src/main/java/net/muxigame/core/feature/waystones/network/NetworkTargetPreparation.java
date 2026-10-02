package net.muxigame.core.feature.waystones.network;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Waits for native block-entity initialization without loading or modifying chunks. */
final class NetworkTargetPreparation {
    private static final int MAX_TICKS=40;
    private static final Map<Object,Wait> pending=new IdentityHashMap<>();
    private static final class Wait {
        final Object key;final MinecraftServer server;final Supplier<String> invalid;
        final BooleanSupplier ready;final CompletableFuture<String> result=new CompletableFuture<>();
        int remaining=MAX_TICKS;
        Wait(Object key,MinecraftServer server,Supplier<String> invalid,BooleanSupplier ready){
            this.key=key;this.server=server;this.invalid=invalid;this.ready=ready;
        }
    }
    static void register(){
        NeoForge.EVENT_BUS.addListener(NetworkTargetPreparation::tick);
        NeoForge.EVENT_BUS.addListener(NetworkTargetPreparation::stop);
    }
    static CompletableFuture<String> await(Object key,MinecraftServer server,Supplier<String> invalid,BooleanSupplier ready){
        return await(key,server,invalid,ready,()->{});
    }
    static CompletableFuture<String> await(Object key,MinecraftServer server,Supplier<String> invalid,BooleanSupplier ready,Runnable wake){
        var wait=new Wait(key,server,invalid,ready);
        server.execute(()->{
            cancel(key);pending.put(key,wait);check(wait,false);
            if(!wait.result.isDone())wake.run();
        });
        return wait.result;
    }
    static void cancel(Object key){
        var wait=pending.remove(key);if(wait!=null)wait.result.complete("传送已取消");
    }
    private static void tick(ServerTickEvent.Post event){
        for(var wait:new ArrayList<>(pending.values()))if(wait.server==event.getServer())check(wait,true);
    }
    private static void stop(ServerStoppingEvent event){
        for(var wait:new ArrayList<>(pending.values()))if(wait.server==event.getServer())finish(wait,"服务器正在退出，传送已取消");
    }
    private static void check(Wait wait,boolean tick){
        if(pending.get(wait.key)!=wait)return;
        String reason=wait.invalid.get();
        if(reason!=null)finish(wait,reason);
        else if(wait.ready.getAsBoolean())finish(wait,null);
        else if(tick && --wait.remaining<=0)finish(wait,"目标石碑尚未完成加载，请重试");
    }
    private static void finish(Wait wait,String reason){
        pending.remove(wait.key);wait.result.complete(reason);
    }
}
