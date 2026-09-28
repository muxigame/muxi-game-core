package net.muxigame.core.taskssmoke.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.function.*;

/** Test artifact only. Create an invisible GL context from the outset; never flash/focus a desktop window. */
@Mixin(value=Window.class,remap=false)
public abstract class HiddenWindowMixin {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long muxi$offscreen(IntSupplier width,IntSupplier height,Supplier<String> title,LongSupplier monitor){
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        return GLFW.glfwCreateWindow(width.getAsInt(),height.getAsInt(),"muxi hidden render QA",0,0);
    }
}
