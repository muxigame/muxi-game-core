package net.muxigame.core.client.input.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.muxigame.core.client.input.*;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(KeyMapping.class)
public abstract class GlobalKeyMappingMixin {
    @Shadow @Final @Mutable private InputConstants.Key defaultKey;
    @Shadow private InputConstants.Key key;
    @Shadow private KeyModifier keyModifierDefault;
    @Shadow private int clickCount;
    @Inject(method={
        "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
        "<init>(Ljava/lang/String;Lnet/neoforged/neoforge/client/settings/IKeyConflictContext;Lnet/neoforged/neoforge/client/settings/KeyModifier;Lcom/mojang/blaze3d/platform/InputConstants$Key;Ljava/lang/String;)V"
    },at=@At("RETURN"))
    private void muxi$defaults(CallbackInfo ci){
        var value=GlobalKeyBindingPlan.defaultBinding(((KeyMapping)(Object)this).getName());if(value==null)return;
        var parts=value.split(":",2);defaultKey=InputConstants.getKey(parts[0]);
        keyModifierDefault=parts.length==1?KeyModifier.NONE:KeyModifier.valueOf(parts[1]);
        ((KeyMapping)(Object)this).setKeyModifierAndCode(keyModifierDefault,defaultKey);
    }
    @Inject(method="matches",at=@At("HEAD"),cancellable=true)
    private void muxi$matches(int code,int scan,CallbackInfoReturnable<Boolean> cir){
        if(!GameplayInputPriority.allowed((KeyMapping)(Object)this,code))cir.setReturnValue(false);
    }
    @Inject(method="setDown",at=@At("HEAD"),cancellable=true)
    private void muxi$down(boolean down,CallbackInfo ci){
        if(down&&key.getType()==InputConstants.Type.KEYSYM&&!GameplayInputPriority.allowed((KeyMapping)(Object)this,key.getValue())){clickCount=0;ci.cancel();}
    }
    @Inject(method="isDown",at=@At("HEAD"),cancellable=true)
    private void muxi$poll(CallbackInfoReturnable<Boolean> cir){
        if(key.getType()==InputConstants.Type.KEYSYM&&!GameplayInputPriority.allowed((KeyMapping)(Object)this,key.getValue()))cir.setReturnValue(false);
    }
    @Inject(method="consumeClick",at=@At("HEAD"),cancellable=true)
    private void muxi$consume(CallbackInfoReturnable<Boolean> cir){
        if(key.getType()==InputConstants.Type.KEYSYM&&!GameplayInputPriority.allowed((KeyMapping)(Object)this,key.getValue())){clickCount=0;cir.setReturnValue(false);}
    }
    @Inject(method="releaseAll",at=@At("HEAD"))
    private static void muxi$release(CallbackInfo ci){GameplayInputPriority.releaseAll();}
}
