package net.muxigame.binaryqa.creative.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.EventHooks;
import net.muxigame.binaryqa.creative.CreativeBuildPhases;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=EventHooks.class, remap=false)
public abstract class CreativeBuildPhasesMixin {
    // Entry/normal return correspond to HEAD/RETURN; finally also clears on throws.
    @WrapMethod(method="onCreativeModeTabBuildContents(Lnet/minecraft/world/item/CreativeModeTab;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V")
    private static void qa$contents(CreativeModeTab tab, ResourceKey<CreativeModeTab> key,
            CreativeModeTab.DisplayItemsGenerator generator, CreativeModeTab.ItemDisplayParameters parameters,
            CreativeModeTab.Output output, Operation<Void> original) {
        CreativeBuildPhases.Frame frame=CreativeBuildPhases.enter(key==null?"<null>":key.location().toString());
        boolean completed=false;
        try { original.call(tab,key,generator,parameters,output); completed=true; }
        finally { CreativeBuildPhases.leave(frame,completed); }
    }
    @WrapOperation(method="onCreativeModeTabBuildContents",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;accept(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V"),require=1,allow=1)
    private static void qa$generator(CreativeModeTab.DisplayItemsGenerator generator,
            CreativeModeTab.ItemDisplayParameters parameters,CreativeModeTab.Output output,Operation<Void> original) {
        CreativeBuildPhases.boundary("generator");
        original.call(generator,parameters,output);
        CreativeBuildPhases.boundary("eventPrepare");
    }
    @WrapOperation(method="onCreativeModeTabBuildContents",at=@At(value="INVOKE",
            target="Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"),require=1,allow=1)
    private static void qa$post(Event event,Operation<Void> original) {
        CreativeBuildPhases.boundary("postEvent");
        original.call(event);
        CreativeBuildPhases.boundary("outputLoops");
    }
}
