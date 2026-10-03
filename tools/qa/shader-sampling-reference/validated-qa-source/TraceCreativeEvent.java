package net.muxigame.binaryqa.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import net.muxigame.binaryqa.Trace;
@Mixin(net.neoforged.neoforge.event.EventHooks.class) public class TraceCreativeEvent {
 @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="onCreativeModeTabBuildContents",at=@At(value="INVOKE",target="Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V")) private static void post(net.neoforged.bus.api.Event event,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){long begin=System.nanoTime();Trace.begin("creativeTabModDispatch");try{original.call(event);}finally{Trace.end("creativeTabModDispatch");net.muxigame.binaryqa.CreativeOrigins.dispatch(begin,System.nanoTime());}}

 @Inject(method="onCreativeModeTabBuildContents",at=@At("HEAD")) private static void begin(net.minecraft.world.item.CreativeModeTab tab,net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> key,net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator generator,net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters params,net.minecraft.world.item.CreativeModeTab.Output output,CallbackInfo ci){net.muxigame.binaryqa.CreativeOrigins.begin(key,params);Trace.begin("creativeTabBuild");}
 @Inject(method="onCreativeModeTabBuildContents",at=@At("RETURN")) private static void end(CallbackInfo ci){Trace.end("creativeTabBuild");net.muxigame.binaryqa.CreativeOrigins.end();}
}
