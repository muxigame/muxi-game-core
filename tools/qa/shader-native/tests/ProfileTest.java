import java.nio.file.*;import net.muxigame.shadernative.FilesQA;
public final class ProfileTest {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);String scenario=args[1];Files.createDirectories(root.resolve("coordinator/shader-native"));System.setProperty("qa.local.root",root.toString());System.setProperty("qa.local.runId","cpu-test");System.setProperty("qa.local.role","host");
  String escaped=root.toAbsolutePath().normalize().toString().replace("\\","\\\\");Files.writeString(root.resolve("local-mc-owner.json"),"{\"runId\":\"cpu-test\",\"instanceRoot\":\""+escaped+"\",\"offlineLoopback\":true,\"mode\":\"hold\",\"roles\":[\"server\",\"host\"],\"serverPort\":25931,\"clientNames\":{\"host\":\"FreshSpawn131\"}}");
  String config=switch(scenario){case "wrong-run"->"{\"runId\":\"other\",\"mode\":\"first-spawn\",\"expectedPlayer\":\"FreshSpawn131\"}";case "wrong-player"->"{\"runId\":\"cpu-test\",\"mode\":\"first-spawn\",\"expectedPlayer\":\"Other\"}";case "unknown-field"->"{\"runId\":\"cpu-test\",\"mode\":\"matrix\",\"teleport\":true}";default->"{\"runId\":\"cpu-test\",\"mode\":\"first-spawn\",\"expectedPlayer\":\"FreshSpawn131\"}";};
  if(!scenario.equals("default"))Files.writeString(root.resolve("coordinator/shader-native/config.json"),config);
  boolean rejected=false;try{var p=FilesQA.profile();if(scenario.equals("default")&&!p.get("mode").getAsString().equals("matrix"))throw new AssertionError();if(scenario.equals("valid")&&!FilesQA.firstSpawn())throw new AssertionError();}catch(IllegalStateException expected){rejected=true;}
  if(rejected!=java.util.Set.of("wrong-run","wrong-player","unknown-field").contains(scenario))throw new AssertionError(scenario);System.out.println("PASS profile "+scenario);
 }
}
