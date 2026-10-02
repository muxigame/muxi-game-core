package net.muxigame.core.compat.shaders;

import com.mojang.blaze3d.shaders.Shader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bytes only. The four supported owners retain native shader compilation, status checks,
 * native program wrappers, uniform/sampler/resource setup, and native pipeline destruction. */
public final class ShaderProgramBinary {
 private record Scope(String pack,String options,String macros,long epoch){}
 private record Request(String key,int[] formats,long epoch){}
 private static final Set<String> CORE=Set.of("minecraft:overworld","muxi_game_core:overworld","muxi_game_core:adventure");
 private static final AtomicLong EPOCH=new AtomicLong();
 private static final Map<ShaderPackSourceCarrier,Scope> SCOPES=new WeakHashMap<>();
 private static final Map<Integer,SortedMap<String,Integer>> ATTRIBUTES=new HashMap<>();
 private static final ThreadLocal<Boolean> EXTENDED=new ThreadLocal<>();
 private static final Map<String,long[]> FAMILY=new TreeMap<>();
 private static final Map<String,Map<String,Object>> RECORDS=new LinkedHashMap<>();
 private static Object context;
 private static ShaderBinaryLinker linker;
 private static boolean disabled,pendingDisk;
 private static long appliedEpoch,unsupported,sourceQueryNs;
 private ShaderProgramBinary(){}
 public static void preload(){ /* Load outside mod transformation/log filters, before setting the plain bootstrap gate. */ }
 private static void failed(){disabled=true;if(linker!=null)linker.disable();}
 public static boolean enabled(){return Boolean.getBoolean("muxi.programBinaryCache")&&!disabled;}
 public static boolean enter(Shader shader){try{boolean old=Boolean.TRUE.equals(EXTENDED.get());EXTENDED.set(shader.getClass().getName().equals("net.irisshaders.iris.pipeline.programs.ExtendedShader"));return old;}catch(Exception|LinkageError|OutOfMemoryError error){failed();return false;}}
 public static void leave(boolean old){try{if(old)EXTENDED.set(true);else EXTENDED.remove();}catch(Exception|LinkageError|OutOfMemoryError error){failed();}}
 public static synchronized void invalidate(boolean disk){pendingDisk|=disk;EPOCH.incrementAndGet();}
 public static void irisReload(){boolean dimension=StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals("net.minecraft.client.Minecraft")&&f.getMethodName().contains("lambda$onDimensionChange")&&f.getMethodName().contains("euphoria_patcher")));invalidate(!dimension);}
 private static void pending(){if(appliedEpoch==EPOCH.get())return;if(linker!=null)linker.reset(pendingDisk);pendingDisk=false;SCOPES.clear();ATTRIBUTES.clear();context=null;appliedEpoch=EPOCH.get();}
 public static synchronized void create(int id){try{pending();if(context!=GL.getCapabilities()){if(linker!=null)linker.reset(false);ATTRIBUTES.clear();context=GL.getCapabilities();}if(ATTRIBUTES.size()<1024)ATTRIBUTES.put(id,new TreeMap<>());else failed();}catch(Exception|LinkageError|OutOfMemoryError error){failed();}}
 public static synchronized void bind(int id,int index,CharSequence name){var attrs=ATTRIBUTES.get(id);if(attrs!=null)bind(attrs,index,name);}
 public static void bind(Map<String,Integer> values,int index,CharSequence name){try{if(values.size()<64||values.containsKey(name.toString()))values.put(name.toString(),index);else failed();}catch(Exception|LinkageError|OutOfMemoryError error){failed();}}
 public static synchronized void delete(int id){ATTRIBUTES.remove(id);}
 public static synchronized void release(){try{invalidate(false);pending();RECORDS.clear();EXTENDED.remove();}catch(Exception|LinkageError|OutOfMemoryError failure){failed();}}
 public static synchronized void close(){release();}
 private static String hash(String value){return ShaderBinaryStore.digest(value.getBytes(StandardCharsets.UTF_8));}
 private static String family(){if(Boolean.TRUE.equals(EXTENDED.get()))return "iris-extended";return StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals("net.irisshaders.iris.gl.shader.ProgramCreator")))?"iris-composite":null;}
 private static int[] formats(){var caps=GL.getCapabilities();if(!caps.OpenGL41&&!caps.GL_ARB_get_program_binary)return new int[0];int n=GL11C.glGetInteger(0x87FE);if(n<1||n>16)return new int[0];int[] values=new int[n];GL11C.glGetIntegerv(0x87FF,values);return values;}
 private static String dimension(){var mc=Minecraft.getInstance();if(mc.level!=null)return mc.level.dimension().location().toString();boolean bootstrap=StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals("net.irisshaders.iris.Iris")&&f.getMethodName().equals("onLoadingComplete")));return bootstrap?"bootstrap/overworld":null;}
 private static String packHash(Path root)throws Exception {
  MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int count=0;
  if(root==null||Files.isSymbolicLink(root))throw new IOException("Unavailable shader root");
  // Only the actual selected shader root supplied by Iris; never account/config/environment files.
  try(var paths=Files.walk(root)){var files=new ArrayList<Path>();var iterator=paths.iterator();while(iterator.hasNext()){Path file=iterator.next();if(++count>10000)throw new IOException("Shader source fingerprint count budget exceeded");if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))files.add(file);}files.sort(Comparator.comparing(p->root.relativize(p).toString()));
   for(Path file:files){byte[] name=root.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());digest.update(name);long size=Files.size(file),read=0;if(size>64L*1024*1024-total)throw new IOException("Shader source fingerprint byte budget exceeded");digest.update(java.nio.ByteBuffer.allocate(8).putLong(size).array());try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){total+=n;read+=n;if(total>64L*1024*1024)throw new IOException("Shader source fingerprint byte budget exceeded");digest.update(block,0,n);}}if(read!=size)throw new IOException("Shader source changed during fingerprint");}
  }return HexFormat.of().formatHex(digest.digest());
 }
 private static Scope scope()throws Exception {
  var iris=Class.forName("net.irisshaders.iris.Iris");var field=iris.getDeclaredField("currentPack");field.setAccessible(true);Object pack=field.get(null);
  if(!(pack instanceof ShaderPackSourceCarrier carrier))return null;var source=carrier.muxi$shaderPackSource();if(source==null||source.defines().isEmpty())return null;
  Scope cached=SCOPES.get(carrier);if(cached!=null&&cached.epoch==EPOCH.get())return cached;
  cached=new Scope(packHash(source.root()),ShaderBinaryKey.map(source.options()),ShaderBinaryKey.map(source.defines()),EPOCH.get());SCOPES.put(carrier,cached);if(SCOPES.size()>4)SCOPES.remove(SCOPES.keySet().iterator().next());return cached;
 }
 private static Request request(int id,Map<String,Integer> attributes,Map<String,Integer> fragments,String family)throws Exception {
  if(family==null||attributes==null||!ShaderBinaryBootstrap.owner(family))return null;String dim=dimension();if(dim==null||!CORE.contains(dim)&&!dim.equals("bootstrap/overworld"))return null;
  if(family.equals("iris-sodium")&&!StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals("net.irisshaders.iris.pipeline.programs.SodiumPrograms"))))return null;
  int[] formats=formats();if(formats.length==0){unsupported++;return null;}Arrays.sort(formats);
  // Supported owner bytecode has no feedback or separable setup. Non-default observed flags bypass caching.
  if(GL20C.glGetProgrami(id,0x8C83)!=0)return null;
  var caps=GL.getCapabilities();if((caps.OpenGL41||caps.GL_ARB_separate_shader_objects)&&GL20C.glGetProgrami(id,0x8258)!=0)return null;
  Scope scope=scope();if(scope==null)return null;int n=GL20C.glGetProgrami(id,GL20C.GL_ATTACHED_SHADERS);if(n<1||n>6)return null;
  int[] ids=new int[n];GL20C.glGetAttachedShaders(id,(int[])null,ids);var stages=new ArrayList<ShaderBinaryKey.Stage>();long begin=System.nanoTime();
  var types=new HashSet<Integer>();for(int shader:ids){if(GL20C.glGetShaderi(shader,GL20C.GL_COMPILE_STATUS)!=GL11.GL_TRUE)return null;int length=GL20C.glGetShaderi(shader,GL20C.GL_SHADER_SOURCE_LENGTH),type=GL20C.glGetShaderi(shader,GL20C.GL_SHADER_TYPE);if(length<1||length>2*1024*1024||!types.add(type))return null;String code=GL20C.glGetShaderSource(shader);if(code==null||code.isEmpty())return null;stages.add(new ShaderBinaryKey.Stage(type,hash(code)));}sourceQueryNs+=System.nanoTime()-begin;
  String vendor=GL11C.glGetString(GL11.GL_VENDOR),renderer=GL11C.glGetString(GL11.GL_RENDERER),version=GL11C.glGetString(GL11.GL_VERSION);if(vendor==null||renderer==null||version==null)return null;
  String driver=hash(vendor+"\n"+renderer+"\n"+version+"\n"+Arrays.toString(formats));
  String key=ShaderBinaryKey.create(family,dim,driver,scope.pack,scope.options,scope.macros,stages,attributes,fragments);
  if(Boolean.getBoolean("muxi.programBinaryCache.diagnostics")){RECORDS.put(key,Map.of("family",family,"dimension",dim,"fullKey",key,"optionsHash",scope.options,"macroHash",scope.macros,"shaderPackHash",scope.pack,"driverHash",driver,"programCodeAndBindingsHash",ShaderBinaryKey.create(family,"code","driver","pack","options","macros",stages,attributes,fragments)));while(RECORDS.size()>512)RECORDS.remove(RECORDS.keySet().iterator().next());}
  return new Request(key,formats,EPOCH.get());
 }
 private static ShaderBinaryLinker linker()throws Exception {if(linker==null)linker=new ShaderBinaryLinker(new ShaderBinaryStore(Minecraft.getInstance().gameDirectory.toPath().resolve(".muxi-program-cache-v2")));return linker;}
 private static ShaderBinaryLinker.Driver driver(int id){return new ShaderBinaryLinker.Driver(){
  public boolean load(int format,byte[] bytes){ByteBuffer buffer=MemoryUtil.memAlloc(bytes.length);try{buffer.put(bytes).flip();ARBGetProgramBinary.glProgramBinary(id,format,buffer);return GL20C.glGetProgrami(id,GL20C.GL_LINK_STATUS)==GL11.GL_TRUE;}finally{MemoryUtil.memFree(buffer);}}
  public ShaderBinaryStore.Binary capture(){if(GL20C.glGetProgrami(id,GL20C.GL_LINK_STATUS)!=GL11.GL_TRUE)return null;int size=GL20C.glGetProgrami(id,0x8741);if(size<1||size>ShaderBinaryStore.ENTRY_LIMIT)return null;ByteBuffer buffer=MemoryUtil.memAlloc(size);try{int[] actual=new int[1],format=new int[1];ARBGetProgramBinary.glGetProgramBinary(id,actual,format,buffer);if(actual[0]<1||actual[0]>size)return null;byte[] bytes=new byte[actual[0]];buffer.position(0);buffer.get(bytes);return new ShaderBinaryStore.Binary(format[0],bytes);}finally{MemoryUtil.memFree(buffer);}}
 };}
 public static synchronized void link(int id,Map<String,Integer> attributes,Map<String,Integer> fragments,String family,Runnable original){
  Request request=null;boolean loaded=false;long[] counters=null;
  try{pending();counters=FAMILY.computeIfAbsent(family==null?"other":family,k->new long[8]);if(enabled()){long begin=System.nanoTime();request=request(id,attributes,fragments,family);counters[3]++;counters[4]+=System.nanoTime()-begin;if(request!=null){begin=System.nanoTime();loaded=linker().load(request.key,request.formats,driver(id));counters[5]+=System.nanoTime()-begin;if(!loaded&&!linker.disabled())ARBGetProgramBinary.glProgramParameteri(id,0x8257,GL11.GL_TRUE);}else counters[7]++;}}
  catch(Exception|LinkageError|OutOfMemoryError cacheFailure){failed();request=null;}
  if(loaded){if(counters!=null)counters[0]++;return;}
  // Deliberately outside the optional-cache catch: preserve native failures, and call only once.
  long begin=System.nanoTime();original.run();long elapsed=System.nanoTime()-begin;
  try{if(counters!=null){counters[1]++;counters[2]+=elapsed;}if(linker!=null){begin=System.nanoTime();linker.linked(request!=null&&request.epoch==EPOCH.get()?request.key:null,elapsed,driver(id));if(counters!=null)counters[6]+=System.nanoTime()-begin;}}catch(Exception|LinkageError|OutOfMemoryError failure){failed();}
 }
 public static synchronized void link(int id,Runnable original){Map<String,Integer> attrs=null;String owner=null;try{attrs=ATTRIBUTES.get(id);owner=family();}catch(Exception|LinkageError|OutOfMemoryError failure){failed();}link(id,attrs,Map.of(),owner,original);}
 public static synchronized List<Map<String,Object>> fingerprints(){return List.copyOf(RECORDS.values());}
 public static synchronized Map<String,Object> snapshot(){pending();var out=new LinkedHashMap<String,Object>();if(linker!=null)out.putAll(linker.snapshot());else for(String key:List.of("hits","misses","rejected","invalidations","nativeLinkCount","nativeLinkMs","loadMs","saveMs","memoryEntries","memoryBytes","memoryHits","diskHits","corruptions","diskWrites"))out.put(key,0);out.put("enabled",enabled());out.put("disabledAfterFailure",disabled||linker!=null&&linker.disabled());out.put("unsupported",unsupported);out.put("sourceMetadata",SCOPES.size());out.put("bindingMetadata",ATTRIBUTES.size());out.put("sourceQueryMs",sourceQueryNs/1e6);var families=new TreeMap<String,Object>();FAMILY.forEach((key,value)->families.put(key,Map.of("hits",value[0],"nativeLinks",value[1],"nativeMs",value[2]/1e6,"attempts",value[3],"keyMs",value[4]/1e6,"lookupLoadMs",value[5]/1e6,"saveMs",value[6]/1e6,"bypass",value[7])));out.put("families",families);out.put("providerGuards",ShaderBinaryBootstrap.owners());return out;}
}
