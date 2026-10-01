package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.*;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.BaseCommandBlock;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;

/** Only packaged into the disposable test server. All identities and credentials are synthetic. */
@Mod("muxi_op_smoke")
public final class OpSyncSmoke {
    private final List<String> passed=new ArrayList<>();
    private final GameOpSync sync;
    private final HttpClient http=HttpClient.newHttpClient();
    private final int phase=Integer.getInteger("muxi.opTestPhase",1);
    private int ticks,stage,stageTick;
    private final Map<Integer,ServerPlayer> actors=new HashMap<>();
    private ServerPlayer target;
    public OpSyncSmoke() {
        sync=new GameOpSync(http,new CoreConfig.OpSync(true,System.getProperty("muxi.opTestEndpoint"),"synthetic-op-service-credential-00000000"));
        NeoForge.EVENT_BUS.addListener(sync::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(String name,boolean ok) {if(!ok)throw new AssertionError(name);passed.add(name);}
    private OpSyncJournal.Desired desired(String uid,int level) {return new OpSyncJournal.Desired(uid,IdentityRules.offlineUuid(uid),1,level);}
    private int level(MinecraftServer server,String uid) {return GameOpSync.nativeTarget(server).level(desired(uid,0));}
    private ServerPlayer player(MinecraftServer server,String uid) {
        var profile=new GameProfile(IdentityRules.offlineUuid(uid),uid);
        server.getProfileCache().add(profile);
        ServerPlayer p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        Connection transport=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(transport);
        p.connection=new ServerGamePacketListenerImpl(server,transport,p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) {}
        };
        return p;
    }
    private void command(MinecraftServer server,CommandSourceStack source,String command) {
        server.getCommands().performPrefixedCommand(source,command);
    }
    private void denied(MinecraftServer server,CommandSourceStack source,String cmd) throws Exception {
        GameOpSync.nativeTarget(server).setLevel(desired("10091",cmd.contains("deop")?2:0));
        int before=level(server,"10091");command(server,source,cmd);
        check("denied: "+cmd+" source="+source.getTextName(),level(server,"10091")==before);
    }
    private void boundaries(MinecraftServer server) throws Exception {
        check("required source mixin applied",server.createCommandSourceStack() instanceof OpCommandOrigin);
        var dispatcher=server.getCommands().getDispatcher();
        // Real Brigadier redirects exercise aliases that bypass root command predicates.
        for(String name:List.of("minecraft:op","test_op_alias")) dispatcher.register(Commands.literal(name).redirect(dispatcher.getRoot().getChild("op")));
        for(String name:List.of("minecraft:deop","test_deop_alias")) dispatcher.register(Commands.literal(name).redirect(dispatcher.getRoot().getChild("deop")));
        for(int op=0;op<=4;op++) {
            String uid=String.valueOf(10093+op);ServerPlayer p=player(server,uid);actors.put(op,p);
            GameOpSync.nativeTarget(server).setLevel(desired(uid,op));
            if(op==4)continue;
            var source=p.createCommandSourceStack();
            for(String cmd:List.of("op 10091","deop 10091","muxiop 10091 4","minecraft:op 10091","minecraft:deop 10091","test_op_alias 10091","test_deop_alias 10091","execute run op 10091","execute run deop 10091")) denied(server,source,cmd);
            // Elevated effective permission must never manufacture originating authority.
            denied(server,source.withPermission(4),"op 10091");
            denied(server,source.withMaximumPermission(4).withSource(server),"deop 10091");
        }
        var owner=actors.get(4);var low=actors.get(3);
        denied(server,low.createCommandSourceStack().withPermission(4).withEntity(owner),"op 10091");
        denied(server,low.createCommandSourceStack().withPermission(4).withEntity(owner).withPosition(Vec3.ZERO).withRotation(Vec2.ZERO)
            .withLevel(server.overworld()).withSuppressedOutput().withCallback(CommandResultCallback.EMPTY),"muxiop 10091 4");
        var commandBlock=new BaseCommandBlock() {
            public ServerLevel getLevel(){return server.overworld();}
            public Vec3 getPosition(){return Vec3.ZERO;}
            public void onUpdated(){}
            public boolean isValid(){return true;}
            public CommandSourceStack createCommandSourceStack(){
                return new CommandSourceStack(this,Vec3.ZERO,Vec2.ZERO,server.overworld(),2,"synthetic-command-block",Component.literal("block"),server,null);
            }
        };
        var block=commandBlock.createCommandSourceStack().withPermission(4);
        denied(server,block.withEntity(owner).withSource(server),"op 10091");
        var gameLoop=server.getFunctions().getGameLoopSender().withPermission(4);
        denied(server,gameLoop,"deop 10091");
        var function=CommandFunction.fromLines(ResourceLocation.parse("muxi_op_smoke:indirect"),dispatcher,server.createCommandSourceStack(),List.of("op 10091"));
        GameOpSync.nativeTarget(server).setLevel(desired("10091",0));
        server.getFunctions().execute(function,low.createCommandSourceStack().withPermission(4).withSource(CommandSource.NULL));
        check("compiled function cannot elevate OP3",level(server,"10091")==0);
        server.getFunctions().execute(function,block);
        check("compiled function cannot elevate indirect non-player source",level(server,"10091")==0);
        command(server,owner.createCommandSourceStack(),"minecraft:op 10091");
        check("OP4 redirected native grant succeeds",level(server,"10091")==4);
        command(server,owner.createCommandSourceStack(),"execute run deop 10091");
        check("OP4 execute native revoke succeeds",level(server,"10091")==0);
        command(server,owner.createCommandSourceStack(),"muxiop 10091 2");
        check("OP4 local level adjustment succeeds",level(server,"10091")==2);
        var reload=new ServerOpList(Path.of("ops.json").toFile());reload.load();
        check("native ops file persists local level",reload.get(target.getGameProfile()).getLevel()==2);
        // Demotion invalidates a previously created source as well as newly created sources.
        var oldOwner=owner.createCommandSourceStack();GameOpSync.nativeTarget(server).setLevel(desired("10097",3));
        denied(server,oldOwner,"op 10091");
        GameOpSync.nativeTarget(server).setLevel(desired("10091",2));
    }
    private void finish(MinecraftServer server,Throwable error) {
        try {
            Map<String,Object> out=new LinkedHashMap<>();out.put("success",error==null);out.put("phase",phase);out.put("passed",passed);
            if(error!=null){out.put("error",error.toString());error.printStackTrace();}
            Files.writeString(Path.of("op-smoke-phase-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
        }catch(Exception error2){error2.printStackTrace();}
        sync.close();http.shutdownNow();server.halt(false);
    }
    private void tick(ServerTickEvent.Post event) {
        var server=event.getServer();ticks++;
        try {
            sync.tick(server,ticks%20==0);
            if(ticks>2400)throw new AssertionError("HTTP sync timeout stage="+stage);
            if(stage==0 && ticks==20) {
                target=player(server,"10091");
                if(phase==2)check("fresh JVM reloads persisted local level",level(server,"10091")==2);
                stage=1;
            } else if(stage==1 && phase==1 && level(server,"10091")==4) {
                check("real HTTP website event grants offline UID in native ops",server.getPlayerList().getPlayer(target.getUUID())==null);
                boundaries(server);stage=2;stageTick=ticks;
            } else if(stage==1 && phase==2 && ticks>100) {
                check("same website event after JVM restart preserves local OP2",level(server,"10091")==2);
                Files.move(Path.of("ops.json"),Path.of("ops-before-failure.json"));
                Files.createDirectory(Path.of("ops.json"));
                Files.writeString(Path.of("ops.json/synthetic-disk-failure"),"nonempty directory prevents atomic replacement");
                Files.writeString(Path.of("request-next-event"),"ready");stage=3;
            } else if(stage==2 && ticks-stageTick>80) {
                check("repeated real HTTP snapshots preserve local OP2",level(server,"10091")==2);
                finish(server,null);stage=99;
            } else if(stage==3) {
                var wal=JsonParser.parseString(Files.readString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("muxi-op-sync-applied.json"))).getAsJsonObject();
                if(!wal.getAsJsonArray("pending").isEmpty()) {
                    check("real native save failure rolls back in-memory grant",level(server,"10091")==2);
                    boolean blocked=false;
                    try {OpCommandOrigin.require(server.createCommandSourceStack());}
                    catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) {blocked=true;}
                    check("local mutations are blocked while durable recovery is pending",blocked);
                    Files.delete(Path.of("ops.json/synthetic-disk-failure"));Files.delete(Path.of("ops.json"));
                    Files.move(Path.of("ops-before-failure.json"),Path.of("ops.json"));
                    stage=5;
                }
            } else if(stage==5 && level(server,"10091")==0) {
                check("new explicit website version revokes offline native OP",true);stage=4;stageTick=ticks;
            } else if(stage==4 && ticks-stageTick>40) {finish(server,null);stage=99;}
        }catch(Throwable error){finish(server,error);stage=99;}
    }
}
