package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerPlayer;
@Mixin(Commands.class)
public abstract class HomeCommandContextMixin {
 @WrapMethod(method="performCommand") private void muxi$command(ParseResults<CommandSourceStack> parsed,String command,Operation<Void> original){var source=parsed.getContext().getSource();if(source.getEntity() instanceof ServerPlayer||(source.getEntity()==null&&source.hasPermission(2))){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(parsed,command);}}else original.call(parsed,command);}
}
