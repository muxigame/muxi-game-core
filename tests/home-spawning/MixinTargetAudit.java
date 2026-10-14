import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Checks actual pinned target bytecode, without booting Minecraft or executing game classes. */
public final class MixinTargetAudit {
 static Map<String,byte[]> targets=new HashMap<>();static int checks;
 static AnnotationNode anno(List<AnnotationNode> a,String suffix){if(a!=null)for(var n:a)if(n.desc.endsWith(suffix))return n;return null;}
 static Object get(AnnotationNode a,String key){if(a==null||a.values==null)return null;for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;}
 static List<String> strings(Object o){if(o instanceof List<?> l)return l.stream().map(Object::toString).toList();return o==null?List.of():List.of(o.toString());}
 static ClassNode node(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 public static void main(String[] args)throws Exception{
  for(String path:Files.readAllLines(Path.of(args[1])))try(var z=new ZipFile(path)){for(var e:Collections.list(z.entries()))if(e.getName().endsWith(".class"))targets.putIfAbsent(e.getName().substring(0,e.getName().length()-6),z.getInputStream(e).readAllBytes());}
  try(var z=new ZipFile(args[0])){for(var e:Collections.list(z.entries())){if(!e.getName().startsWith("net/muxigame/core/mixin/home/")||!e.getName().endsWith(".class"))continue;var mix=node(z.getInputStream(e).readAllBytes());var a=anno(mix.invisibleAnnotations,"/Mixin;");if(a==null)a=anno(mix.visibleAnnotations,"/Mixin;");List<String> names=new ArrayList<>();Object values=get(a,"value");if(values instanceof List<?> l)for(var x:l)names.add(((Type)x).getInternalName());for(String n:strings(get(a,"targets")))names.add(n.replace('.','/'));
   for(String name:names){byte[] b=targets.get(name);if(b==null)throw new AssertionError("Audited target absent: "+name);var target=node(b);
    for(var method:mix.methods){AnnotationNode hook=null;for(String suffix:List.of("/WrapMethod;","/WrapOperation;","/Inject;")){hook=anno(method.invisibleAnnotations,suffix);if(hook==null)hook=anno(method.visibleAnnotations,suffix);if(hook!=null)break;}if(hook==null)continue;
     for(String selector:strings(get(hook,"method"))){String mn=selector.contains("(")?selector.substring(0,selector.indexOf('(')):selector;var found=target.methods.stream().filter(m->m.name.equals(mn)&&(!selector.contains("(")||m.desc.equals(selector.substring(selector.indexOf('('))))).toList();if(found.isEmpty())throw new AssertionError(mix.name+" missing target "+name+"."+selector);checks++;
      if(hook.desc.endsWith("/WrapOperation;")){Object av=get(hook,"at");if(av instanceof List<?> l)av=l.get(0);String invocation=(String)get((AnnotationNode)av,"target");boolean seen=false;for(var m:found)for(var ins:m.instructions)if(ins instanceof MethodInsnNode call){String id="L"+call.owner+";"+call.name+call.desc;if(id.equals(invocation))seen=true;}if(!seen)throw new AssertionError(mix.name+" invocation absent: "+invocation);checks++;}
     }
    }
   }
  }}System.out.println("Actual pinned mixin target/invocation checks passed: "+checks);
 }
}
