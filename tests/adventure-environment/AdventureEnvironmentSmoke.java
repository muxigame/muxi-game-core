package net.muxigame.core.adventureqa;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.muxigame.core.feature.rules.AdventureEnvironmentProtection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import static net.minecraft.commands.Commands.literal;

/** QA-only mod host: loads exact production protection classes/config, not production identity/network. */
@Mod(value="muxi_game_core",dist=Dist.DEDICATED_SERVER)
public final class AdventureEnvironmentSmoke {
    static final List<String> passed=new ArrayList<>();
    public AdventureEnvironmentSmoke(){NeoForge.EVENT_BUS.addListener(this::commands);}
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed.add(name);}
    void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(literal("adventureqa_prepare").requires(s->s.hasPermission(4)).executes(c->{
            var server=c.getSource().getServer();
            for(var key:List.of(WorldDimensions.ADVENTURE,Level.OVERWORLD,WorldDimensions.OVERWORLD,Level.NETHER)){
                var level=server.getLevel(key);level.setChunkForced(6,6,true);
                if(key.equals(WorldDimensions.ADVENTURE))for(int x=7;x<=13;x++)for(int z=7;z<=13;z++)level.setChunkForced(x,z,true);
            }
            return 1;
        }));
        e.getDispatcher().register(literal("adventureqa").requires(s->s.hasPermission(4)).executes(c->{
        var result=new JsonObject();
        try{run(c.getSource().getServer());result.addProperty("passed",true);}
        catch(Throwable error){result.addProperty("passed",false);result.addProperty("error",error.toString());error.printStackTrace();}
        result.add("assertions",new Gson().toJsonTree(passed));
        try{Files.writeString(Path.of("adventure-environment-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){throw new RuntimeException(error);}
        return result.get("passed").getAsBoolean()?1:0;
    }));}
    static void run(net.minecraft.server.MinecraftServer server)throws Exception{
        var adventure=server.getLevel(WorldDimensions.ADVENTURE);
        check(adventure!=null,"adventure dimension exists");
        check(AdventureEnvironmentProtection.protectedLevel(adventure),"exact adventure key protected");
        check(!AdventureEnvironmentProtection.protectedLevel(server.overworld()),"home policy unchanged");
        for(var key:List.of(WorldDimensions.ADVENTURE,Level.OVERWORLD,WorldDimensions.OVERWORLD,Level.NETHER)){
            var level=server.getLevel(key);check(level!=null,"dimension exists "+key.location());
            var pos=new BlockPos(96,160,96);level.getChunkAt(pos);
            level.setBlock(pos.below(),Blocks.NETHERRACK.defaultBlockState(),3);
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            boolean protectedWorld=key.equals(WorldDimensions.ADVENTURE);
            boolean fire=level.setBlock(pos,Blocks.FIRE.defaultBlockState(),3);
            check(fire!=protectedWorld,"fire placement scope "+key.location());
            check(level.getBlockState(pos).is(Blocks.FIRE)!=protectedWorld,"actual fire state "+key.location());
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos.below(),Blocks.SOUL_SOIL.defaultBlockState(),3);
            boolean soul=level.setBlockAndUpdate(pos,Blocks.SOUL_FIRE.defaultBlockState());
            check(soul!=protectedWorld,"soul fire placement scope "+key.location());
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            // Exercise a standard mod event and prove that the entity list is preserved.
            var explosion=new Explosion(level,null,96.5,160.5,96.5,4,true,Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(pos);
            var pig=EntityType.PIG.create(level);var entities=new ArrayList<Entity>();entities.add(pig);
            NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level,explosion,entities));
            check(explosion.getToBlow().isEmpty()==protectedWorld,"standard mod explosion block scope "+key.location());
            check(entities.size()==1&&entities.getFirst()==pig,"explosion entity list retained "+key.location());
        }
        // Vanilla TNT-source, creeper-source and source-less incendiary blasts run the actual explode/finalize paths.
        for(int type=0;type<3;type++){
            var pos=new BlockPos(120+type*24,160,120);adventure.getChunkAt(pos);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)adventure.setBlock(pos.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            Entity source=type==0?new PrimedTnt(adventure,pos.getX()+.5,160.5,120.5,null):type==1?EntityType.CREEPER.create(adventure):null;
            var pig=EntityType.PIG.create(adventure);pig.setPos(pos.getX()+1.5,160,120.5);pig.setNoAi(true);adventure.addFreshEntity(pig);
            check(adventure.getEntities((Entity)null,pig.getBoundingBox().inflate(8)).contains(pig),"damage fixture entity query ready source "+type);
            float before=pig.getHealth();
            var blast=adventure.explode(source,pos.getX()+.5,160.5,120.5,4,true,Level.ExplosionInteraction.TNT);
            check(blast.getToBlow().isEmpty(),"actual blast block list empty source "+type);
            check(pig.getHealth()<before||!pig.isAlive(),"actual blast entity damage retained source "+type);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)check(adventure.getBlockState(pos.offset(x,-1,z)).is(Blocks.OAK_PLANKS),"terrain preserved "+type+":"+x+":"+z);
            check(!adventure.getBlockState(pos).is(Blocks.FIRE),"incendiary blast no fire "+type);pig.discard();
        }
        // Existing-save fire is inserted below Level's write guard solely as a QA fixture.
        var pos=new BlockPos(220,160,220);adventure.getChunkAt(pos);
        adventure.setBlock(pos.below(),Blocks.NETHERRACK.defaultBlockState(),3);
        for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL)adventure.setBlock(pos.relative(dir),Blocks.OAK_PLANKS.defaultBlockState(),3);
        adventure.getChunkAt(pos).setBlockState(pos,Blocks.FIRE.defaultBlockState(),false);
        var tick=FireBlock.class.getDeclaredMethod("tick",net.minecraft.world.level.block.state.BlockState.class,ServerLevel.class,BlockPos.class,net.minecraft.util.RandomSource.class);tick.setAccessible(true);
        for(int i=0;i<200;i++)tick.invoke(Blocks.FIRE,Blocks.FIRE.defaultBlockState(),adventure,pos,adventure.random);
        for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL)check(adventure.getBlockState(pos.relative(dir)).is(Blocks.OAK_PLANKS),"existing fire cannot consume neighbor "+dir);
        check(adventure.getBlockState(pos).is(Blocks.FIRE),"existing-save fire not deleted");
        check(adventure.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK),"global fire gamerule unchanged");
    }
}
