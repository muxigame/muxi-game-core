package net.muxigame.core.compat.shaders;

import net.neoforged.bus.api.*;
import net.neoforged.fml.*;
import java.lang.reflect.*;
import java.util.*;

/** A fresh registration event and fresh processor registry still run every time.
 * Only an empty native priority listener array avoids its no-op container dispatch/logging.
 * Phase-major order, inherited listeners, dynamic registration and native exceptions remain native.
 * No resource provider, event, listener, shader or GPU object is retained. */
public final class VeilShaderEventDispatch {
 private static volatile Method lookup;
 private static Method phaseListeners;
 private static long calls,skipped,delivered,fallbacks,elapsedNs,probeNs;
 private static boolean disabled;
 private VeilShaderEventDispatch(){}
 public static boolean enabled(){return ShaderBinaryBootstrap.ready&&Boolean.getBoolean("muxi.veilShaderEventDispatch")&&!disabled&&ShaderBinaryBootstrap.owner("veil-event-dispatch");}
 /** Uses exactly the native EventBus.post(priority,event) listener lookup, never a cached subscription. */
 public static boolean empty(IEventBus bus,EventPriority priority,Event event)throws ReflectiveOperationException {
  if(bus==null||!bus.getClass().getName().equals("net.neoforged.bus.EventBus"))return false;
  if(lookup==null)initialize(bus.getClass());
  Object list=lookup.invoke(bus,event.getClass());Object[] listeners=(Object[])phaseListeners.invoke(list,priority);return listeners.length==0;
 }
 private static synchronized void initialize(Class<?> type)throws ReflectiveOperationException {if(lookup!=null)return;Method method=type.getDeclaredMethod("getListenerList",Class.class);method.setAccessible(true);phaseListeners=method.getReturnType().getMethod("getPhaseListeners",EventPriority.class);lookup=method;}
 public static void dispatch(Event event,Runnable original){
  if(!enabled()||!event.getClass().getName().equals("foundry.veil.forge.event.ForgeVeilAddShaderProcessorsEvent")||ModLoader.hasErrors()){original.run();return;}
  long begin=System.nanoTime();calls++;
  try{
   // This is the pinned ModLoader.postEvent loop, with the empty-array no-op guarded per phase.
   for(EventPriority priority:EventPriority.values())ModList.get().forEachModInOrder(container->{
    boolean skip=false;long probe=System.nanoTime();
    try{if(container.getClass().getName().equals("net.neoforged.fml.javafmlmod.FMLModContainer"))skip=empty(container.getEventBus(),priority,event);else fallbacks++;}
    catch(ReflectiveOperationException|RuntimeException|LinkageError failure){fallbacks++;disabled=true;}
    finally{probeNs+=System.nanoTime()-probe;}
    if(skip){skipped++;return;}
    delivered++;
    // Keep the original container's phase dispatch, logging and ModLoadingException handling.
    container.acceptEvent(priority,(Event & net.neoforged.fml.event.IModBusEvent)event);
   });
  }finally{elapsedNs+=System.nanoTime()-begin;}
 }
 public static Map<String,Object> snapshot(){return Map.of("enabled",enabled(),"disabledAfterFailure",disabled,"calls",calls,"skippedEmptyPhases",skipped,"nativeDeliveredPhases",delivered,"fallbacks",fallbacks,"dispatchMs",elapsedNs/1e6,"probeMs",probeNs/1e6,"retainedRegistries",0);}
}
