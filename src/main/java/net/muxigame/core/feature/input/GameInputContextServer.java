package net.muxigame.core.feature.input;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Publishes only context transitions after game ticks; no UI polling or business action. */
@EventBusSubscriber(modid="muxi_game_core")
public final class GameInputContextServer {
    private static final Map<MinecraftServer,Map<UUID,GameInputContextNetwork.Context>> SENT=new IdentityHashMap<>();
    private static Method equipmentContext,gameId;
    private static boolean resolved,warned;
    private GameInputContextServer() {}
    private static String activeGame(ServerPlayer player){
        if(!resolved){
            resolved=true;
            try{
                equipmentContext=Class.forName("net.muxigame.minigames.equipment.GameEquipment").getMethod("context",ServerPlayer.class);
                gameId=Class.forName("net.muxigame.minigames.equipment.EquipmentContext").getMethod("gameId");
            }catch(ClassNotFoundException|NoSuchMethodException unavailable){equipmentContext=null;}
        }
        if(equipmentContext==null)return ""; // Compatibility with pre-equipment framework.
        try{var context=equipmentContext.invoke(null,player);return context!=null&&"outbreak".equals(gameId.invoke(context))?"outbreak":"";}
        catch(ReflectiveOperationException failure){
            if(!warned){warned=true;org.slf4j.LoggerFactory.getLogger("muxi-game-core/input").warn("Shared equipment input context unavailable",failure);}
            return "";
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();var sent=SENT.computeIfAbsent(server,s->new HashMap<>());
        for(var player:server.getPlayerList().getPlayers()){
            if(player.connection==null||!NetworkRegistry.hasChannel(player.connection,GameInputContextNetwork.Context.TYPE.id()))continue;
            var context=new GameInputContextNetwork.Context(activeGame(player),player.level().dimension().location().toString());
            if(!context.equals(sent.get(player.getUUID()))){PacketDistributor.sendToPlayer(player,context);sent.put(player.getUUID(),context);}
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){var sent=SENT.get(player.server);if(sent!=null)sent.remove(player.getUUID());}}
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event){SENT.remove(event.getServer());}
}
