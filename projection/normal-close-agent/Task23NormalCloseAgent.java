import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.io.File;
/** Temporary recovery: request standard integrated-server halt(false) only in own verified QA world. */
public final class Task23NormalCloseAgent {
 public static void agentmain(String arg, Instrumentation inst) throws Exception {
  Path lab=Path.of(arg).toAbsolutePath().normalize();
  Path expected=Path.of("C:/Users/ranzh/Documents/Codex/schematic-task23-20261002/runs/projection-20261002-044944-d9394440").toAbsolutePath().normalize();
  if(!lab.equals(expected)||ProcessHandle.current().pid()!=33816)throw new IllegalStateException("Refuse other process/path");
  Class<?> mcClass=null,hooksClass=null;
  for(Class<?> type:inst.getAllLoadedClasses()){if(type.getName().equals("net.minecraft.client.Minecraft"))mcClass=type;if(type.getName().equals("net.neoforged.neoforge.server.ServerLifecycleHooks"))hooksClass=type;}
  if(mcClass==null||hooksClass==null)throw new IllegalStateException("Own client/server classes absent");
  Object mc=mcClass.getMethod("getInstance").invoke(null);
  File directory=(File)mcClass.getField("gameDirectory").get(mc);
  if(!directory.toPath().toAbsolutePath().normalize().equals(lab))throw new IllegalStateException("Game directory differs");
  Object server=hooksClass.getMethod("getCurrentServer").invoke(null);
  if(server==null)throw new IllegalStateException("No current integrated server");
  Object data=server.getClass().getMethod("getWorldData").invoke(server);
  String name=(String)data.getClass().getMethod("getLevelName").invoke(data);
  if(!name.equals("Task23 isolated schematic world"))throw new IllegalStateException("Other world");
  server.getClass().getMethod("halt",boolean.class).invoke(server,false);
  Files.writeString(lab.resolve("standard-server-stop-request.txt"),"Verified own PID33816/gameDirectory/world. Called MinecraftServer.halt(false); normal save/shutdown, no forced termination.\n");
 }
}
