import net.muxigame.core.compat.shaders.*;
import java.nio.file.*;
import java.util.*;

public final class ShaderBinaryCorrectnessTest {
 static int checks;
 static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
 static String key(String value){return ShaderBinaryStore.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static String content(String family,String dim,String driver,String pack,String options,String macros,List<ShaderBinaryKey.Stage> stages,Map<String,Integer> attrs,Map<String,Integer> frags){return ShaderBinaryKey.create(family,dim,driver,pack,options,macros,stages,attrs,frags);}
 static class FakeDriver implements ShaderBinaryLinker.Driver {
  int loads,captures;boolean accept=true;Error error;RuntimeException failure;
  public boolean load(int format,byte[] bytes){loads++;if(error!=null)throw error;if(failure!=null)throw failure;return accept;}
  public ShaderBinaryStore.Binary capture(){captures++;if(error!=null)throw error;if(failure!=null)throw failure;return new ShaderBinaryStore.Binary(7,new byte[]{1,2,3});}
 }
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Files.createDirectories(root);var dir=root.resolve("records");var store=new ShaderBinaryStore(dir);
  Map<String,String> ambiguous1=Map.of("a","b, c=d"),ambiguous2=new LinkedHashMap<>();ambiguous2.put("a","b");ambiguous2.put("c","d");
  check(ambiguous1.toString().equals(ambiguous2.toString()),"fixture exposes old Map.toString ambiguity");
  check(!ShaderBinaryKey.map(ambiguous1).equals(ShaderBinaryKey.map(ambiguous2)),"framed map collision avoided");
  check(ShaderBinaryKey.map(new LinkedHashMap<>(Map.of("a","1","b","2"))).equals(ShaderBinaryKey.map(new TreeMap<>(Map.of("b","2","a","1")))),"map iteration order independent");
  var stages=List.of(new ShaderBinaryKey.Stage(35633,key("vertex")),new ShaderBinaryKey.Stage(35632,key("fragment")));
  String normal=content("iris-sodium","overworld","driver","pack","options","macros",stages,Map.of("Position",0),Map.of("fragColor",0));
  String[] parts={"iris-sodium","overworld","driver","pack","options","macros"};for(int i=0;i<parts.length;i++){String[] changed=parts.clone();changed[i]+="!";check(!normal.equals(content(changed[0],changed[1],changed[2],changed[3],changed[4],changed[5],stages,Map.of("Position",0),Map.of("fragColor",0))),"content dimension "+i+" separated");}
  check(!normal.equals(content(parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],stages,Map.of("Position",1),Map.of("fragColor",0))),"attribute binding separated");
  check(!normal.equals(content(parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],stages,Map.of("Position",0),Map.of("fragColor",1))),"fragment binding separated");
  check(!normal.equals(content(parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],List.of(new ShaderBinaryKey.Stage(35633,key("changed vertex")),stages.get(1)),Map.of("Position",0),Map.of("fragColor",0))),"actual transformed source separated");
  check(normal.equals(content(parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],List.of(stages.get(1),stages.get(0)),Map.of("Position",0),Map.of("fragColor",0))),"one shader per stage attachment order independent");
  String k=key("cache");store.put(k,7,new byte[]{1,2,3});store.clear(false);check(Arrays.equals(store.get(k).data(),new byte[]{1,2,3}),"disk load round trip");
  store.clear(false);Path file=dir.resolve(k+".bin");byte[] damaged=Files.readAllBytes(file);damaged[damaged.length-1]^=1;Files.write(file,damaged);check(store.get(k)==null&&!Files.exists(file)&&store.corruptions==1,"corrupt checksum removes only owned record");
  Files.write(file,new byte[]{1,2,3});check(store.get(k)==null&&!Files.exists(file),"truncated header safe miss");
  Path sentinel=dir.resolve("user.bin");Files.writeString(sentinel,"preserve");for(int i=0;i<260;i++)store.put(key("capacity"+i),7,new byte[]{1});check(store.entries()==256&&store.diskEntries()==256,"count bound");
  store.clear(true);check(Files.exists(sentinel)&&store.entries()==0&&store.bytes()==0&&store.diskEntries()==0,"clear owns only exact cache records");
  for(int i=0;i<9;i++)store.put(key("bytes"+i),7,new byte[ShaderBinaryStore.ENTRY_LIMIT]);check(store.entries()<=8&&store.bytes()<=ShaderBinaryStore.BYTE_LIMIT,"RAM byte bound");check(store.diskEntries()<=7,"disk total including headers bound");
  store.put(key("oversize"),7,new byte[ShaderBinaryStore.ENTRY_LIMIT+1]);check(store.get(key("oversize"))==null,"oversized binary not stored");store.clear(true);
  try{store.get("../outside");throw new AssertionError("unsafe key allowed");}catch(IllegalArgumentException correct){checks++;}
  var linker=new ShaderBinaryLinker(store);var driver=new FakeDriver();check(!linker.load(k,new int[]{7},driver)&&driver.loads==0,"miss never calls driver");linker.linked(k,1000,driver);check(driver.captures==1&&store.entries()==1,"native success captures bytes");check(linker.load(k,new int[]{7},driver)&&driver.loads==1,"successful driver LINK_STATUS hit");
  driver.accept=false;check(!linker.load(k,new int[]{7},driver)&&store.get(k)==null,"driver rejection removes binary and requests original link");check(((Number)linker.snapshot().get("rejected")).longValue()==1,"rejection recorded");
  store.put(k,9,new byte[]{1});int before=driver.loads;check(!linker.load(k,new int[]{7},driver)&&driver.loads==before&&store.get(k)==null,"unsupported format never submitted");
  store.put(k,7,new byte[]{1});driver.failure=new IllegalStateException("synthetic optional failure");check(!linker.load(k,new int[]{7},driver)&&linker.disabled()&&store.entries()==0,"optional load exception disables cache and requests native link");
  var oomLinker=new ShaderBinaryLinker(store);store.put(k,7,new byte[]{1});driver.failure=null;driver.error=new OutOfMemoryError("synthetic only");check(!oomLinker.load(k,new int[]{7},driver)&&oomLinker.disabled(),"optional native buffer OOM preserves fallback");
  driver.error=null;var lifecycle=new ShaderBinaryLinker(store);lifecycle.linked(k,1000,driver);lifecycle.reset(false);check(store.entries()==0&&lifecycle.load(k,new int[]{7},driver)==false,"rejection state in fixture remains false");driver.accept=true;lifecycle.linked(k,1000,driver);lifecycle.reset(false);check(store.entries()==0&&lifecycle.load(k,new int[]{7},driver),"logout clears RAM and disk supports reconnect");lifecycle.reset(true);check(store.entries()==0&&store.diskEntries()==0&&!lifecycle.load(k,new int[]{7},driver),"genuine resource reload invalidates RAM and owned disk");
  check(!ShaderBinaryBootstrap.ready&&!ShaderBinaryBootstrap.owner("iris-sodium"),"bootstrap defaults safe and requires provider validation");
  System.out.println("Shader binary correctness checks passed: "+checks);
 }
}
