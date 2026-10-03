package net.muxigame.core.client.input;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Applies a per-binding revision after native options load, including existing clients. */
public final class GlobalKeyBindingMigration {
    private GlobalKeyBindingMigration() {}
    public static void apply(Options options){
        var mc=Minecraft.getInstance();if(mc==null||mc.gameDirectory==null||options.keyMappings==null)return;
        var root=mc.gameDirectory.toPath();var file=root.resolve("options.txt");
        var marker=root.resolve("config/muxi_game_core/input-migrations.json");
        try{
            var applied=new LinkedHashMap<String,String>();
            if(Files.exists(marker)){
                var json=JsonParser.parseString(Files.readString(marker,StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.has("bindings"))json.getAsJsonObject("bindings").entrySet().forEach(e->applied.put(e.getKey(),e.getValue().getAsString()));
            }
            var mappings=new LinkedHashMap<String,KeyMapping>();var current=new LinkedHashMap<String,String>();
            for(var mapping:options.keyMappings){
                if(GlobalKeyBindingPlan.defaultBinding(mapping.getName())==null)continue;
                mappings.put(mapping.getName(),mapping);
                var modifier=mapping.getKeyModifier();current.put(mapping.getName(),mapping.getKey().getName()+(modifier==KeyModifier.NONE?"":":"+modifier.name()));
            }
            var changes=GlobalKeyBindingPlan.changes(current,applied);
            if(!changes.isEmpty()){
                var original=Files.exists(file)?Files.readString(file,StandardCharsets.UTF_8):"";
                var updated=GlobalKeyBindingPlan.updateOptions(original,changes);
                if(Files.exists(file)&&!Files.readString(file,StandardCharsets.UTF_8).equals(original))throw new IllegalStateException("Options changed during migration");
                atomicWrite(file,updated);
                changes.forEach((name,value)->{
                    var parts=value.split(":",2);var modifier=parts.length==1?KeyModifier.NONE:KeyModifier.valueOf(parts[1]);
                    mappings.get(name).setKeyModifierAndCode(modifier,InputConstants.getKey(parts[0]));
                });
                KeyMapping.resetMapping();
            }
            boolean mark=false;
            for(var change:GlobalKeyBindingPlan.CHANGES)if(mappings.containsKey(change.name())&&!change.desired().equals(applied.get(change.name()))){applied.put(change.name(),change.desired());mark=true;}
            if(mark){var json=new JsonObject();json.addProperty("schema",1);var bindings=new JsonObject();applied.forEach(bindings::addProperty);json.add("bindings",bindings);atomicWrite(marker,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n");}
        }catch(Exception error){LoggerFactory.getLogger("muxi-game-core/input").warn("Key binding migration was not persisted; unrelated options are preserved",error);}
    }
    private static void atomicWrite(Path path,String text)throws Exception{
        Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"input-migration-",".tmp");
        try{
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
