from pathlib import Path
import subprocess,tempfile,json,os
root=Path(__file__).resolve().parent;out=root/'evidence';out.mkdir(exist_ok=True)
java='''import java.nio.file.*;import net.muxigame.core.feature.waystones.network.NetworkFacilityConfig;
public class FacilityConfigTest{
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void invalid(String json){try{NetworkFacilityConfig.parse(json);throw new AssertionError("accepted "+json);}catch(RuntimeException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  String empty="{\\"version\\":1,\\"facilities\\":[]}";
  var cfg=NetworkFacilityConfig.parse(empty);check(cfg.facilities().isEmpty()&&cfg.associationRadius()==4&&cfg.entranceVertical()==3,"defaults");
  String f="{\\"dimension\\":\\"minecraft:overworld\\",\\"portalPos\\":[1,64,2],\\"waystoneUid\\":\\"00000000-0000-0000-0000-000000000001\\"}";
  var one=NetworkFacilityConfig.parse("{\\"version\\":1,\\"associationRadius\\":5,\\"entranceRadius\\":2,\\"facilities\\":["+f+"]}");check(one.facilities().size()==1&&one.associationRadius()==5&&one.entranceRadius()==2,"independent configured association/player ranges");
  invalid(empty.replace("version\\":1","version\\":2"));invalid(empty.replace("version\\":1","version\\":1.5"));invalid(empty.replace("version\\":1","version\\":\\"1\\""));
  invalid("{\\"version\\":1,\\"associationRadius\\":9,\\"facilities\\":[]}");invalid("{\\"version\\":1,\\"entranceRadius\\":0,\\"facilities\\":[]}");invalid("{\\"version\\":1,\\"entranceVertical\\":-1,\\"facilities\\":[]}");
  invalid("{\\"version\\":1,\\"associationRadius\\":1.25,\\"facilities\\":[]}");
  invalid("{\\"version\\":1,\\"facilities\\":["+f+","+f+"]}");
  for(String bad:new String[]{f.replace("minecraft:overworld","https://example.com"),f.replace("[1,64,2]","[30000001,64,2]"),f.replace("[1,64,2]","[1,5000,2]"),f.replace("[1,64,2]","[1.9,64,2]"),f.replace("[1,64,2]","[1e20,64,2]"),f.replace("[1,64,2]","[1,64]"),f.replace("00000000-0000-0000-0000-000000000001","1-1-1-1-1")})invalid("{\\"version\\":1,\\"facilities\\":["+bad+"]}");
  Path dir=Path.of(args[0]);Path absent=dir.resolve("absent.json");check(NetworkFacilityConfig.read(absent).facilities().isEmpty()&&!Files.exists(absent),"absent file never created");
  Path bad=dir.resolve("bad.json");Files.writeString(bad,"{bad");check(NetworkFacilityConfig.read(bad).facilities().isEmpty(),"invalid config fails closed");Files.writeString(bad," ".repeat(65537));check(NetworkFacilityConfig.read(bad).facilities().isEmpty(),"oversized file rejected");
  Path valid=dir.resolve("valid.json");Files.writeString(valid,empty);var first=NetworkFacilityConfig.read(valid);Files.writeString(valid,"{\\"version\\":1,\\"facilities\\":["+f+"]}");check(NetworkFacilityConfig.read(valid).facilities().size()==1&&first.facilities().isEmpty(),"re-read changes apply without invented or stale facility state");
  check(Files.readString(valid).contains("waystoneUid"),"reading preserves operator content");
  System.out.println("{\\"success\\":true,\\"checks\\":"+checks+",\\"scope\\":\\"production bounded read-only local facility configuration; synthetic files only\\"}");
 }
}'''
aggregate=Path(os.environ.get('TASK6_COMPILER_DEPENDENCIES','C:/Users/Administrator/Documents/Codex/2026-10-01/task-21/music-app/build/compiler-dependencies.jar'))
with tempfile.TemporaryDirectory(dir=out) as temp:
 temp=Path(temp);test=temp/'FacilityConfigTest.java';test.write_text(java,encoding='utf-8');args=temp/'args'
 opts=['--release','21','-encoding','UTF-8','-proc:none','-cp',str(aggregate),'-d',str(temp/'classes'),str(root/'muxi-game-core/src/main/java/net/muxigame/core/feature/waystones/network/NetworkFacilityConfig.java'),str(test)]
 args.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in opts),encoding='utf-8')
 result=subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','@'+str(args)],capture_output=True)
 if result.returncode:print(result.stderr.decode('utf-8','replace'));result.check_returncode()
 result=subprocess.run(['C:/Program Files/Java/jdk-24/bin/java.exe','-cp',str(temp/'classes')+os.pathsep+str(aggregate),'FacilityConfigTest',str(temp)],capture_output=True)
 if result.returncode:print(result.stderr.decode('utf-8','replace'));result.check_returncode()
 report=json.loads(result.stdout);(out/'facility-config-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
