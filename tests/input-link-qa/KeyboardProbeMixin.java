package net.muxigame.inputlinkqa.mixin;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.muxigame.core.client.input.GameplayInputPriority;
import net.muxigame.inputlinkqa.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=KeyboardHandler.class,priority=2000)
public abstract class KeyboardProbeMixin {
    @Inject(method="keyPress",at=@At("HEAD"))
    private void probe(long window,int key,int scan,int action,int mods,CallbackInfo ci){if(!ProbeFiles.enabled()||action!=1)return;var rows=new JsonObject();for(var mapping:Minecraft.getInstance().options.keyMappings)if(mapping.getKey().getValue()==key){var row=new JsonObject();row.addProperty("allowed",GameplayInputPriority.allowed(mapping,key));row.addProperty("matches",mapping.matches(key,scan));rows.add(mapping.getName(),row);}ProbeCounters.frame=rows;}
}
