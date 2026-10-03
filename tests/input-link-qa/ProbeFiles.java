package net.muxigame.inputlinkqa;
import com.google.gson.*;
import java.nio.file.*;
public final class ProbeFiles {
    public static final Path ROOT=Path.of(System.getProperty("qa.local.root","missing-local-qa-root"));
    public static final String RUN=System.getProperty("qa.local.runId","");
    public static boolean enabled(){try{var marker=JsonParser.parseString(Files.readString(ROOT.resolve("local-mc-owner.json"))).getAsJsonObject();return !RUN.isBlank()&&RUN.equals(marker.get("runId").getAsString())&&ROOT.toAbsolutePath().normalize().toString().equals(marker.get("instanceRoot").getAsString());}catch(Exception absent){return false;}}
    public static JsonObject read(String name){try{var row=JsonParser.parseString(Files.readString(ROOT.resolve("coordinator").resolve(name))).getAsJsonObject();return RUN.equals(row.get("runId").getAsString())?row:null;}catch(Exception busy){return null;}}
    public static void write(String name,JsonObject row)throws Exception{row.addProperty("runId",RUN);row.addProperty("physicalOSInput",false);Path p=ROOT.resolve("coordinator").resolve(name),tmp=p.resolveSibling(name+".tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(row));Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
}
