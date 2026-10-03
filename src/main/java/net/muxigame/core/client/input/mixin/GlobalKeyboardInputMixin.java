package net.muxigame.core.client.input.mixin;

import net.minecraft.client.KeyboardHandler;
import net.muxigame.core.client.input.GameplayInputPriority;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(KeyboardHandler.class)
public abstract class GlobalKeyboardInputMixin {
    @WrapMethod(method="keyPress")
    private void muxi$dispatch(long window,int key,int scan,int action,int modifiers,Operation<Void> original){
        boolean tracked=GameplayInputPriority.begin(window,key,action,modifiers);
        try { if(tracked)GameplayInputPriority.dispatchPlainF();original.call(window,key,scan,action,modifiers); }
        finally { if(tracked)GameplayInputPriority.end(key,action); }
    }
}
