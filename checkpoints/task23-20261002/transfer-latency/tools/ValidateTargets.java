import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reads class bytes and annotations only. Does not load any Minecraft classes. */
public final class ValidateTargets {
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> all = new ArrayList<>();
        if (a != null) all.addAll(a);
        if (b != null) all.addAll(b);
        return all;
    }
    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[0]);
        List<ZipFile> jars = new ArrayList<>();
        for (int i = 1; i < args.length; i++) jars.add(new ZipFile(args[i]));
        int injections = 0, shadows = 0;
        try (var paths = Files.walk(classes.resolve("net/muxigame/transferprobe/mixin"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassNode mixin = read(Files.readAllBytes(file));
                String target = null;
                for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    Object typed = value(annotation, "value");
                    Object named = value(annotation, "targets");
                    if (typed instanceof List<?> list) target = ((Type)list.getFirst()).getClassName();
                    else if (named instanceof List<?> list) target = (String)list.getFirst();
                }
                if (target == null) throw new AssertionError("Missing target: " + mixin.name);
                ClassNode original = null;
                String member = target.replace('.', '/') + ".class";
                for (ZipFile jar : jars) {
                    ZipEntry entry = jar.getEntry(member);
                    if (entry != null) {
                        try (var stream = jar.getInputStream(entry)) { original = read(stream.readAllBytes()); }
                        break;
                    }
                }
                if (original == null) throw new AssertionError("Missing pinned original: " + target);
                for (FieldNode shadow : mixin.fields) {
                    for (AnnotationNode annotation : annotations(shadow.visibleAnnotations, shadow.invisibleAnnotations)) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) continue;
                        boolean found = original.fields.stream().anyMatch(f -> f.name.equals(shadow.name) && f.desc.equals(shadow.desc));
                        if (!found) throw new AssertionError("Missing shadow field " + shadow.name);
                        shadows++;
                    }
                }
                for (MethodNode handler : mixin.methods) {
                    for (AnnotationNode annotation : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                        if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                            boolean found = original.methods.stream().anyMatch(m -> m.name.equals(handler.name) && m.desc.equals(handler.desc));
                            if (!found) throw new AssertionError("Missing shadow " + handler.name);
                            shadows++;
                        }
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                        List<?> selectors = (List<?>)value(annotation, "method");
                        for (Object selectorValue : selectors) {
                            String selector = (String)selectorValue;
                            int separator = selector.indexOf('(');
                            String methodName = separator < 0 ? selector : selector.substring(0, separator);
                            String descriptor = separator < 0 ? null : selector.substring(separator);
                            List<MethodNode> matches = original.methods.stream().filter(m -> m.name.equals(methodName)
                                    && (descriptor == null || m.desc.equals(descriptor))).toList();
                            if (matches.size() != 1) throw new AssertionError("Expected one target: " + target + "." + selector + " found=" + matches.size());
                            MethodNode method = matches.getFirst();
                            Type[] captures = Type.getArgumentTypes(handler.desc);
                            Type[] arguments = Type.getArgumentTypes(method.desc);
                            if (captures.length != 1) {
                                if (captures.length != arguments.length + 1) throw new AssertionError("Capture arity " + handler.name);
                                for (int i = 0; i < arguments.length; i++)
                                    if (!captures[i].equals(arguments[i])) throw new AssertionError("Capture type " + handler.name);
                            }
                            String callback = captures[captures.length - 1].getClassName();
                            String expected = Type.getReturnType(method.desc).equals(Type.VOID_TYPE)
                                    ? "org.spongepowered.asm.mixin.injection.callback.CallbackInfo"
                                    : "org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable";
                            if (!callback.equals(expected)) throw new AssertionError("Callback type " + handler.name);
                            injections++;
                        }
                    }
                }
            }
        } finally {
            for (ZipFile jar : jars) jar.close();
        }
        System.out.println("{\"targetInjectionSignaturesChecked\":" + injections + ",\"shadowSignaturesChecked\":"
                + shadows + ",\"minecraftClassesLoaded\":false,\"runtimeMixinApplicationVerified\":false}");
    }
}
