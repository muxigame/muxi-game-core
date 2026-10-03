package net.muxigame.memoryqa;

import com.google.gson.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Explicit render/client-thread snapshots only; no poller, forced GC, GL binding or world mutation. */
public final class MemoryLifecycleProbe {
 private static final Gson JSON=new Gson();
 private MemoryLifecycleProbe(){}
 public static JsonObject snapshot(String phase){
  if(phase==null||!phase.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("phase");
  if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("clientThreadRequired");
  JsonObject out=new JsonObject();out.addProperty("phase",phase);out.addProperty("epochMs",System.currentTimeMillis());out.addProperty("nanoTime",System.nanoTime());out.addProperty("pid",ProcessHandle.current().pid());
  MemoryMXBean memory=ManagementFactory.getMemoryMXBean();out.add("heap",usage(memory.getHeapMemoryUsage()));out.add("nonHeap",usage(memory.getNonHeapMemoryUsage()));
  JsonArray collectors=new JsonArray();for(GarbageCollectorMXBean gc:ManagementFactory.getGarbageCollectorMXBeans()){JsonObject row=new JsonObject();row.addProperty("name",gc.getName());row.addProperty("collections",gc.getCollectionCount());row.addProperty("collectionTimeMs",gc.getCollectionTime());collectors.add(row);}out.add("gc",collectors);
  JsonArray buffers=new JsonArray();for(BufferPoolMXBean pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){JsonObject row=new JsonObject();row.addProperty("name",pool.getName());row.addProperty("count",pool.getCount());row.addProperty("usedBytes",pool.getMemoryUsed());row.addProperty("capacityBytes",pool.getTotalCapacity());buffers.add(row);}out.add("bufferPools",buffers);
  out.add("dimensionShader",read("net.muxigame.core.compat.shaders.DimensionShaderSwap","diagnosticLifecycleSnapshot"));
  out.add("coreProgramBinary",read("net.muxigame.core.compat.shaders.ShaderProgramBinary","snapshot"));
  out.add("legacyStageCache",read("net.muxigame.shaderstage.ShaderStageCache","snapshot"));
  out.add("legacyProgramBinary",read("net.muxigame.programlab.ProgramBinaryCache","snapshot"));
  out.addProperty("cacheSnapshotsMayApplyPendingInvalidation",true);out.addProperty("gpuObjectLedgerComplete",false);return out;
 }
 private static JsonObject usage(MemoryUsage usage){JsonObject out=new JsonObject();out.addProperty("usedBytes",usage.getUsed());out.addProperty("committedBytes",usage.getCommitted());out.addProperty("maxBytes",usage.getMax());return out;}
 private static JsonObject read(String name,String method){
  JsonObject result=new JsonObject();try{Class<?> type=net.muxigame.shadernative.PerformanceProbe.type(name);Object value=type.getMethod(method).invoke(null);result.addProperty("status","available");result.add("value",JSON.toJsonTree(value));}
  catch(ClassNotFoundException missing){result.addProperty("status","absent");}
  catch(ReflectiveOperationException|LinkageError|RuntimeException failure){Throwable root=failure;while(root instanceof InvocationTargetException&&root.getCause()!=null)root=root.getCause();result.addProperty("status","failed");result.addProperty("failureClass",root.getClass().getName());}
  return result;
 }
}
