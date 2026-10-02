package net.muxigame.core.compat.shaders;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.security.MessageDigest;
/** Exact public provider ABI, plus transformed-call rejection for unsupported link parameters.
 * A missing/different provider disables that family; it never prevents native rendering. */
public final class ProgramBinaryPlugin implements IMixinConfigPlugin {
 private static final Map<String,String> PINS=Map.ofEntries(
  Map.entry("dev.djefrey.colorwheel.compile.ClrwlProgram","63aa2754b50fdf4254f45a3b8070245a9de2e6cce718a98fb24f063690bfc813"),
  Map.entry("net.irisshaders.iris.pipeline.programs.ExtendedShader","d84ce2ac8f90da4d0d001da76bfc0c5a8aadb4a4018c0946c2d469da477a3a0a"),
  Map.entry("net.irisshaders.iris.gl.shader.ProgramCreator","0fe4f9c64a4bdd8f544c23099cee9117ad91b0a73caed6429c38e2c560f18e2e"),
  Map.entry("net.irisshaders.iris.pipeline.programs.SodiumPrograms","f70254298d41e2f2775ec3f6f09c611bc9c63be2fc5298d1b72ab6a331fb0275"),
  Map.entry("net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder","80e9edbf32bf34171bdafcfb94e79a3c764a11b81d0a1183788220c64b8b7758"),
  Map.entry("net.neoforged.fml.ModLoader","5fa4a2bd9e8a2b0a4a2add1ca245229bdacf74e62e4f21cc53f98fd2beccfda1"),
  Map.entry("net.neoforged.fml.ModContainer","b21b0e39461ab25162e3963e2c7fe26a72b42dda193a8d26ec5dd8c0aa2d3e55"),
  Map.entry("net.neoforged.fml.javafmlmod.FMLModContainer","dff277ee42194cb26b03293ddfa164fea912b229282f60592d52c43a5e7012c4"),
  Map.entry("net.neoforged.bus.EventBus","cda8876c904d1374f77bbff02e8cdb01fba48ae0f528c69713eaefa11b5128a4"),
  Map.entry("net.neoforged.bus.ListenerList","3daccbcb1ef602dff0edfbbd1ed5483e4633d5bed6c0a1ccb35c01cef5c83888"),
  Map.entry("foundry.veil.forge.platform.NeoForgeVeilClientPlatform","df2ea3a6f3e9d0950a75c29428727fb2153bedb15e82ec2cc54782a5ba607c52"),
  Map.entry("foundry.veil.forge.event.ForgeVeilAddShaderProcessorsEvent","fa48246af439c4187424db8283900ab32f7b269f32f33b187feaf8acae241d8b"));
 private static boolean pin(String name){try(var in=ProgramBinaryPlugin.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){return in!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(PINS.get(name));}catch(Exception|LinkageError e){return false;}}
 public void onLoad(String pkg){ShaderBinaryBootstrap.owner("veil-event-dispatch",pin("net.neoforged.bus.EventBus")&&pin("net.neoforged.bus.ListenerList")&&pin("net.neoforged.fml.ModLoader")&&pin("net.neoforged.fml.ModContainer")&&pin("net.neoforged.fml.javafmlmod.FMLModContainer")&&pin("foundry.veil.forge.platform.NeoForgeVeilClientPlatform")&&pin("foundry.veil.forge.event.ForgeVeilAddShaderProcessorsEvent"));ShaderBinaryBootstrap.owner("iris-extended",pin("net.irisshaders.iris.pipeline.programs.ExtendedShader"));ShaderBinaryBootstrap.owner("iris-composite",pin("net.irisshaders.iris.gl.shader.ProgramCreator"));ShaderBinaryBootstrap.owner("iris-sodium",pin("net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder")&&pin("net.irisshaders.iris.pipeline.programs.SodiumPrograms"));ShaderBinaryBootstrap.owner("iris-colorwheel",pin("dev.djefrey.colorwheel.compile.ClrwlProgram"));}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(mixin.endsWith("VeilShaderEventDispatchMixin"))return ShaderBinaryBootstrap.owner("veil-event-dispatch");if(mixin.endsWith("ProgramBinarySodiumMixin"))return ShaderBinaryBootstrap.owner("iris-sodium");if(mixin.endsWith("ProgramBinaryColorwheelMixin"))return ShaderBinaryBootstrap.owner("iris-colorwheel");if(mixin.endsWith("ProgramBinaryReloadMixin"))return ShaderBinaryBootstrap.owner("iris-composite");return true;}
 public void acceptTargets(Set<String> own,Set<String> others){}
 public List<String> getMixins(){return null;}
 public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){inspect(target,node);}
 public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){inspect(target,node);}
 private static void inspect(String target,ClassNode node){boolean unsafe=false;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("org/lwjgl/opengl/")&&(call.name.contains("TransformFeedbackVaryings")||call.name.contains("BindFragDataLocationIndexed")||call.name.contains("ProgramParameteri")))unsafe=true;if(unsafe){String family=target.contains("caffeinemc")?"iris-sodium":target.contains("colorwheel")?"iris-colorwheel":null;if(family!=null)ShaderBinaryBootstrap.owner(family,false);else{ShaderBinaryBootstrap.owner("iris-extended",false);ShaderBinaryBootstrap.owner("iris-composite",false);}}}
}
