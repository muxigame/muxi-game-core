package net.muxigame.core.feature.spawning;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class HomeSpawnPolicyTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void deny(String reason){check(HomeSpawnRules.deny(true,false,false,reason,null),"home must deny "+reason);}
 static void allow(String reason){check(!HomeSpawnRules.deny(true,false,false,reason,null),"home must allow "+reason);}
 public static void main(String[] args)throws Exception {
  for(String reason:new String[]{"NATURAL","CHUNK_GENERATION","STRUCTURE","PATROL","REINFORCEMENT","EVENT","TRIGGERED","JOCKEY","COMMAND","MOB_SUMMONED",null})deny(reason);
  for(String reason:new String[]{"SPAWNER","TRIAL_SPAWNER"}){deny(reason);check(!HomeSpawnRules.deny(true,false,false,reason,HomeSpawnContext.Origin.SPAWNER),"real spawner context "+reason);}
  for(String reason:new String[]{"BREEDING","CONVERSION","BUCKET","SPAWN_EGG","DISPENSER"})allow(reason);
  for(String reason:new String[]{"NATURAL","CHUNK_GENERATION","STRUCTURE","SPAWNER","TRIGGERED",null}){
   check(!HomeSpawnRules.deny(false,false,false,reason,null),"other dimensions unaffected");
   check(!HomeSpawnRules.deny(true,true,false,reason,HomeSpawnContext.Origin.AMBIENT),"all existing disk entities retained");
   check(!HomeSpawnRules.deny(true,false,false,reason,HomeSpawnContext.Origin.TRAVEL),"all imported creatures retained");
  }
  check(!HomeSpawnRules.deny(true,false,false,null,HomeSpawnContext.Origin.PLAYER),"NPC player tools without finalize");
  check(!HomeSpawnRules.deny(true,false,false,"EVENT",HomeSpawnContext.Origin.PLAYER),"explicit player summoning");
  check(HomeSpawnRules.deny(true,false,true,"SPAWNER",HomeSpawnContext.Origin.SPAWNER),"worldgen cannot pretend to be a spawner");
  for(String reason:new String[]{"SPAWNER","COMMAND","SPAWN_EGG","MOB_SUMMONED",null})check(HomeSpawnRules.deny(true,false,false,reason,HomeSpawnContext.Origin.AMBIENT),"ambient source cannot spoof reason "+reason);
  check(HomeSpawnContext.current()==null,"initial empty context");
  try(var a=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){
   check(HomeSpawnContext.current()==HomeSpawnContext.Origin.PLAYER,"player scope");
   try(var b=HomeSpawnContext.enter(HomeSpawnContext.Origin.AMBIENT)){
    check(HomeSpawnContext.current()==HomeSpawnContext.Origin.AMBIENT,"ambient overrides caller");
    try(var p=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){check(HomeSpawnContext.current()==HomeSpawnContext.Origin.AMBIENT,"nested tool cannot erase ambient origin");}
    try(var t=HomeSpawnContext.enter(HomeSpawnContext.Origin.TRAVEL)){check(HomeSpawnContext.current()==HomeSpawnContext.Origin.TRAVEL,"existing travel wins");}
   }
   check(HomeSpawnContext.current()==HomeSpawnContext.Origin.PLAYER,"nested restore");
  }
  check(HomeSpawnContext.current()==null,"no retained scope");
  AtomicReference<HomeSpawnContext.Origin> inside=new AtomicReference<>();Runnable task;
  try(var p=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){task=HomeSpawnContext.bind(HomeSpawnContext.Origin.AMBIENT,()->inside.set(HomeSpawnContext.current()));}
  check(HomeSpawnContext.current()==null,"capturing doesn't leak caller");
  ExecutorService worker=Executors.newSingleThreadExecutor();try{
   worker.submit(task).get();check(inside.get()==HomeSpawnContext.Origin.AMBIENT,"async NPC retains ambient provenance");
   check(worker.submit(HomeSpawnContext::current).get()==null,"async worker cleaned after success");
   try{worker.submit(HomeSpawnContext.bind(HomeSpawnContext.Origin.AMBIENT,()->{throw new IllegalStateException("expected fixture");})).get();throw new AssertionError("missing exception");}catch(ExecutionException expected){checks++;}
   check(worker.submit(HomeSpawnContext::current).get()==null,"async worker cleaned after failure");
   check(worker.submit(()->{try(var p=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){return !HomeSpawnRules.deny(true,false,false,null,HomeSpawnContext.current());}}).get(),"later player creation not poisoned by prior NPC task");
   check(worker.submit(HomeSpawnContext::current).get()==null,"worker clear after player");
  }finally{worker.shutdown();check(worker.awaitTermination(5,TimeUnit.SECONDS),"worker normal exit");}
  System.out.println("Home spawn policy/context: "+checks+" checks passed");
 }
}
