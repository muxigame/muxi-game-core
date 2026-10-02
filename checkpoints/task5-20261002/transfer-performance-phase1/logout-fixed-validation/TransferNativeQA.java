package net.muxigame.transferqa;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

/** QA-only private world. Keeps actual receiving screen, chunk flow and shader pipeline intact. */
@Mod(value="muxi_transfer_qa",dist=Dist.CLIENT)
public final class TransferNativeQA {
    private final JsonObject report=new JsonObject();private final JsonArray samples=new JsonArray();
    private final Gson json=new GsonBuilder().setPrettyPrinting().create();
    private int ticks,waitTicks,index,moveTicks;private boolean requestedWorld,preparing,finished,disconnecting;
    private volatile boolean prepared;private volatile String failure;
    private volatile boolean serverReturned;private volatile double serverQueueMs,serverEngineMs;
    private volatile boolean serverMovementChecked,serverObservedMovement;
    private boolean serverMovementRequested,reloadChecked,viewValidating;
    private long jitBegin,gcBegin;
    private JsonObject sample;private String target;private long requestNs,lastFrame,firstFrame,readyNs,moveNs,observedUntil;
    private double startX,startZ,maxFrameGap;private int renderedFrames;
    private final Set<String> visited=new HashSet<>();
    private final ResourceKey<Level> adventure=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("muxi_game_core:adventure"));
    private final ResourceKey<Level> survival=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("muxi_game_core:overworld"));
    public TransferNativeQA(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::render);}
    private double elapsed(long time){return (time-requestNs)/1_000_000.0;}
    private boolean finalValidation(){return Boolean.getBoolean("qa.transfer.finalValidation");}
    private boolean coreDimension(String id){return Set.of("minecraft:overworld","muxi_game_core:overworld","muxi_game_core:adventure").contains(id);}
    private Object iris(String method)throws Exception{return Class.forName("net.irisshaders.iris.Iris").getMethod(method).invoke(null);}
    private JsonObject shaderState() {
        var state=new JsonObject();
        try{
            Object config=iris("getIrisConfig");state.addProperty("enabled",(Boolean)config.getClass().getMethod("areShadersEnabled").invoke(config));
            state.addProperty("pack",String.valueOf(iris("getCurrentPackName")));
            Object manager=iris("getPipelineManager");Object pipeline=manager.getClass().getMethod("getPipeline").invoke(manager);
            if(pipeline instanceof Optional<?> opt)state.addProperty("pipeline",opt.map(p->p.getClass().getName()).orElse("none"));
            state.addProperty("fallback",(Boolean)iris("isFallback"));
        }catch(Exception e){state.addProperty("error",e.toString());}
        return state;
    }
    private void write()throws Exception{report.add("samples",samples);Files.writeString(Path.of("transfer-qa-result.json"),json.toJson(report));}
    private void platform(ServerLevel level) {
        long begin=System.nanoTime();level.getChunk(0,0);
        int floor=level.dimension().equals(Level.NETHER)?100:240;
        for(int x=1;x<=14;x++)for(int z=1;z<=14;z++){
            level.setBlock(new BlockPos(x,floor,z),Blocks.STONE_BRICKS.defaultBlockState(),18);
            for(int y=floor+1;y<floor+21;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
        }
        for(int y=floor+1;y<floor+5;y++)level.setBlock(new BlockPos(12,y,10),Blocks.OAK_LOG.defaultBlockState(),18);
        for(int x=10;x<=14;x++)for(int z=8;z<=12;z++)level.setBlock(new BlockPos(x,floor+5,z),Blocks.OAK_LEAVES.defaultBlockState(),18);
        level.setBlock(new BlockPos(5,floor+1,8),Blocks.LANTERN.defaultBlockState(),18);
        var shaft=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"));
        level.setBlock(new BlockPos(11,floor+1,7),shaft.defaultBlockState(),18);
        level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        report.addProperty("prepare_"+level.dimension().location()+"_ms",(System.nanoTime()-begin)/1e6);
    }
    private void start(Minecraft mc) {
        ResourceKey<Level> selected=index==8&&Boolean.getBoolean("qa.transfer.extended")?survival:index%2==0?adventure:Level.OVERWORLD;
        if(finalValidation()&&index==2)selected=Level.NETHER;
        if(finalValidation()&&index==4)selected=Level.END;
        target=selected.location().toString();
        sample=new JsonObject();sample.addProperty("index",index);sample.addProperty("source",mc.level.dimension().location().toString());
        sample.addProperty("target",target);sample.addProperty("coldFirstTargetInProcess",visited.add(target));
        sample.addProperty("shaderExpected",Boolean.getBoolean("qa.transfer.shaders"));
        sample.addProperty("requestedUtc",Instant.now().toString());
        jitBegin=ManagementFactory.getCompilationMXBean().getTotalCompilationTime();gcBegin=ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();
        requestNs=System.nanoTime();lastFrame=requestNs;firstFrame=readyNs=moveNs=0;renderedFrames=moveTicks=0;maxFrameGap=0;
        serverReturned=false;serverMovementChecked=serverMovementRequested=serverObservedMovement=false;
        final var uuid=mc.player.getUUID();final var key=selected;
        mc.getSingleplayerServer().execute(()->{
            try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                var level=player.server.getLevel(key);if(level==null)throw new IllegalStateException("Missing target "+key);
                serverQueueMs=elapsed(System.nanoTime());
                long begin=System.nanoTime();
                if(player.changeDimension(new DimensionTransition(level,new Vec3(8.5,key.equals(Level.NETHER)?101:241,8.5),Vec3.ZERO,180,15,DimensionTransition.DO_NOTHING))==null)
                    throw new IllegalStateException("Core rejected QA route from "+player.level().dimension()+" to "+key);
                serverEngineMs=(System.nanoTime()-begin)/1e6;serverReturned=true;
            }catch(Throwable e){failure=e.toString();}
        });
    }
    private void render(RenderLevelStageEvent event) {
        if(finished||sample==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        Minecraft mc=Minecraft.getInstance();long now=System.nanoTime();maxFrameGap=Math.max(maxFrameGap,(now-lastFrame)/1e6);lastFrame=now;
        if(mc.level==null||mc.player==null||!mc.level.dimension().location().toString().equals(target))return;
        if(!mc.level.hasChunkAt(mc.player.blockPosition())||!mc.levelRenderer.isSectionCompiled(mc.player.blockPosition()))return;
        renderedFrames++;
        if(firstFrame==0){firstFrame=now;sample.addProperty("firstCompiledWorldFrameMs",elapsed(now));}
        if(readyNs==0&&mc.screen==null&&mc.getOverlay()==null){
            readyNs=now;sample.addProperty("visibleAndInputUnblockedMs",elapsed(now));
            sample.add("shaderState",shaderState());
            startX=mc.player.getX();startZ=mc.player.getZ();mc.options.keyUp.setDown(true);
        }
    }
    private void tick(ClientTickEvent.Post event) {
        if(finished)return;Minecraft mc=Minecraft.getInstance();
        try{
            ticks++;
            if(ticks%100==0)Files.writeString(Path.of("transfer-progress.json"),"{\"ticks\":"+ticks+",\"index\":"+index+",\"prepared\":"+prepared+",\"target\":"+json.toJson(target)+",\"dimension\":"+json.toJson(mc.level==null?null:mc.level.dimension().location().toString())+",\"position\":"+json.toJson(mc.player==null?null:mc.player.position().toString())+",\"renderedCompiledFrames\":"+renderedFrames+",\"screen\":"+json.toJson(String.valueOf(mc.screen))+"}");
            if(failure!=null)throw new IllegalStateException(failure);
            if(Files.exists(Path.of("request-normal-close.json"))||ticks>24000)throw new IllegalStateException("QA deadline");
            if(disconnecting){if(mc.level!=null||mc.getSingleplayerServer()!=null||++waitTicks<40)return;if(Boolean.getBoolean("qa.transfer.expectFix")){var lifecycle=(Map<?,?>)Class.forName("net.muxigame.core.compat.shaders.DimensionShaderSwap").getMethod("diagnosticLifecycleSnapshot").invoke(null);report.add("shaderCacheAfterLogout",json.toJsonTree(lifecycle));if(!Integer.valueOf(0).equals(lifecycle.get("parsedPacks"))||!Boolean.FALSE.equals(lifecycle.get("capturedPackRoot"))||!Boolean.FALSE.equals(lifecycle.get("pendingLevel")))throw new IllegalStateException("Own parsed shader cache retained after logout "+lifecycle);}report.addProperty("normalLogout",true);report.addProperty("success",true);write();finished=true;mc.stop();return;}
            if(viewValidating){if(++waitTicks<40)return;try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(Path.of("shader-scene.png"));}report.addProperty("shaderSceneScreenshot","shader-scene.png");report.add("shaderStateAtScene",shaderState());mc.level.disconnect();mc.disconnect(new TitleScreen());disconnecting=true;waitTicks=0;return;}
            if(!requestedWorld){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||ticks<60)return;
                requestedWorld=true;report.addProperty("method","actual 1.4.27 pack with dev Core/Terminal; integrated private server; actual changeDimension, loading screen, Iris/Euphoria/Sodium; synthetic fixed platforms");
                report.addProperty("seed",172943L);report.addProperty("landingChunkPreGenerated",true);
                if(Files.isDirectory(Path.of("saves/transfer-private")))mc.createWorldOpenFlows().openWorld("transfer-private",()->{});
                else mc.createWorldOpenFlows().createFreshLevel("transfer-private",new LevelSettings("Transfer private",GameType.CREATIVE,false,Difficulty.PEACEFUL,false,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(172943L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(!preparing){
                preparing=true;final UUID uuid=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    try{var server=mc.getSingleplayerServer();platform(server.overworld());platform(server.getLevel(adventure));if(Boolean.getBoolean("qa.transfer.extended"))platform(server.getLevel(survival));if(finalValidation()){platform(server.getLevel(Level.NETHER));platform(server.getLevel(Level.END));}var player=server.getPlayerList().getPlayer(uuid);if(player.level().dimension().equals(Level.OVERWORLD))player.teleportTo(8.5,241,8.5);else if(player.changeDimension(new DimensionTransition(server.overworld(),new Vec3(8.5,241,8.5),Vec3.ZERO,180,15,DimensionTransition.DO_NOTHING))==null)throw new IllegalStateException("Private home setup rejected");prepared=true;}
                    catch(Throwable e){failure=e.toString();}
                });return;
            }
            if(!prepared||mc.getOverlay()!=null)return;
            if(sample==null){if(mc.screen!=null||(!samples.isEmpty()?false:!mc.level.dimension().equals(Level.OVERWORLD))||++waitTicks<100)return;if(index==8&&Boolean.getBoolean("qa.transfer.extended")&&!reloadChecked){long begin=System.nanoTime();iris("reload");report.addProperty("explicitIrisReloadMs",(System.nanoTime()-begin)/1e6);JsonObject state=shaderState();if(state.has("fallback")&&state.get("fallback").getAsBoolean())throw new IllegalStateException("Manual shader reload fallback");report.add("shaderStateAfterExplicitReload",state);report.addProperty("explicitReloadCacheCleared",((Map<?,?>)Class.forName("net.muxigame.core.compat.shaders.DimensionShaderSwap").getMethod("diagnosticSnapshot").invoke(null)).isEmpty());reloadChecked=true;waitTicks=0;return;}visited.add(mc.level.dimension().location().toString());waitTicks=0;start(mc);return;}
            long now=System.nanoTime();
            if(now-requestNs>120_000_000_000L)throw new IllegalStateException("Transfer >120s "+target);
            if(readyNs==0)return;
            if(++moveTicks>=5)mc.options.keyUp.setDown(false);
            if(moveNs==0){
                if(Math.hypot(mc.player.getX()-startX,mc.player.getZ()-startZ)>.025){moveNs=now;sample.addProperty("movementResponseMs",elapsed(now));observedUntil=now+5_000_000_000L;}
                else if(now-readyNs>10_000_000_000L)throw new IllegalStateException("Ready screen but movement stayed blocked");
                return;
            }
            if(now<observedUntil||!serverReturned)return;
            if(Boolean.getBoolean("qa.transfer.serverMovement")){
                if(!serverMovementRequested){serverMovementRequested=true;final var uuid=mc.player.getUUID();final double x=startX,z=startZ;mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);serverObservedMovement=player!=null&&Math.hypot(player.getX()-x,player.getZ()-z)>.025;serverMovementChecked=true;});return;}
                if(!serverMovementChecked)return;
                if(!serverObservedMovement)throw new IllegalStateException("Client input moved but server position did not respond");
                sample.addProperty("serverObservedMovement",true);
            }
            sample.addProperty("serverQueueMs",serverQueueMs);sample.addProperty("serverChangeDimensionMs",serverEngineMs);
            sample.addProperty("jitCompilationDeltaMs",ManagementFactory.getCompilationMXBean().getTotalCompilationTime()-jitBegin);sample.addProperty("gcCollectionDeltaMs",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum()-gcBegin);
            mc.options.keyUp.setDown(false);sample.addProperty("postTransferObservationMs",(now-moveNs)/1e6);
            sample.addProperty("worstFrameGapMs",maxFrameGap);sample.addProperty("renderedCompiledFrames",renderedFrames);
            JsonObject shader=shaderState();sample.add("shaderStateAfterObservation",shader);
            boolean managed=coreDimension(sample.get("source").getAsString())&&coreDimension(target);sample.addProperty("managedShaderTransition",managed);
            try{var snapshot=(Map<?,?>)Class.forName("net.muxigame.core.compat.shaders.DimensionShaderSwap").getMethod("diagnosticSnapshot").invoke(null);sample.add("dimensionShaderSwap",json.toJsonTree(snapshot));if(Boolean.getBoolean("qa.transfer.expectFix")&&managed&&!target.equals(snapshot.get("dimension")))throw new IllegalStateException("Lean shader refresh did not cover measured transition "+snapshot);}catch(ClassNotFoundException ignored){if(Boolean.getBoolean("qa.transfer.expectFix"))throw new IllegalStateException("Expected fix missing");}
            boolean expected=Boolean.getBoolean("qa.transfer.shaders");
            if(shader.has("error")||!shader.has("enabled")||shader.get("enabled").getAsBoolean()!=expected)throw new IllegalStateException("Shader state mismatch "+shader);
            if(expected&&(!shader.has("pipeline")||!shader.get("pipeline").getAsString().contains("IrisRenderingPipeline")))throw new IllegalStateException("Expected actual shader pipeline "+shader);
            if(shader.has("fallback")&&shader.get("fallback").getAsBoolean())throw new IllegalStateException("Iris fallback "+shader);
            if(finalValidation()||index<2||index==Integer.getInteger("qa.transfer.samples",8)-1){try(var png=Screenshot.takeScreenshot(mc.getMainRenderTarget())){png.writeToFile(Path.of("transfer-"+index+".png"));}}
            samples.add(sample);sample=null;write();index++;
            if(index<Integer.getInteger("qa.transfer.samples",8))return;
            viewValidating=true;waitTicks=0;mc.player.setYRot(-60);mc.player.setXRot(15);
        }catch(Throwable e){finished=true;mc.options.keyUp.setDown(false);e.printStackTrace();report.addProperty("success",false);report.addProperty("error",e.toString());if(sample!=null)report.add("failedSample",sample);try{write();}catch(Exception ignored){}mc.stop();}
    }
}
