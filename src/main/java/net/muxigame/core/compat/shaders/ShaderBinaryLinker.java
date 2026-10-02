package net.muxigame.core.compat.shaders;

import java.util.*;

/** Owns bytes only. Optional cache failure must never swallow or repeat the original native link. */
public final class ShaderBinaryLinker {
 public interface Driver {
  /** The implementation must return true ONLY after querying GL_LINK_STATUS. */
  boolean load(int format,byte[] bytes);
  ShaderBinaryStore.Binary capture();
 }
 private final ShaderBinaryStore store;
 private boolean disabled;
 private long hits,misses,rejected,invalidations,nativeLinks,nativeNs,loadNs,saveNs;
 public ShaderBinaryLinker(ShaderBinaryStore store){this.store=store;}
 public synchronized boolean disabled(){return disabled;}
 public synchronized void disable(){disabled=true;try{store.clear(false);}catch(Exception|LinkageError|OutOfMemoryError ignored){}}
 public synchronized void reset(boolean disk){invalidations++;try{store.clear(disk);}catch(Exception|LinkageError|OutOfMemoryError failure){disable();}}
 public synchronized boolean load(String key,int[] supported,Driver driver) {
  if(disabled||key==null||supported.length==0)return false;
  try {var binary=store.get(key);if(binary!=null){boolean format=Arrays.stream(supported).anyMatch(v->v==binary.format());if(format){long begin=System.nanoTime();boolean linked;try{linked=driver.load(binary.format(),binary.data());}finally{loadNs+=System.nanoTime()-begin;}if(linked){hits++;return true;}rejected++;}store.remove(key);}misses++;}
  catch(Exception|LinkageError|OutOfMemoryError optionalFailure){disable();}
  return false;
 }
 public synchronized void linked(String key,long elapsed,Driver driver) {
  nativeLinks++;nativeNs+=elapsed;if(disabled||key==null)return;
  long begin=System.nanoTime();try{var binary=driver.capture();if(binary!=null)store.put(key,binary.format(),binary.data());}
  catch(Exception|LinkageError|OutOfMemoryError optionalFailure){disable();}finally{saveNs+=System.nanoTime()-begin;}
 }
 public synchronized Map<String,Object> snapshot(){var result=new LinkedHashMap<String,Object>();result.put("disabledAfterFailure",disabled);result.put("hits",hits);result.put("misses",misses);result.put("rejected",rejected);result.put("invalidations",invalidations);result.put("nativeLinkCount",nativeLinks);result.put("nativeLinkMs",nativeNs/1e6);result.put("loadMs",loadNs/1e6);result.put("saveMs",saveNs/1e6);result.put("memoryEntries",store.entries());result.put("memoryBytes",store.bytes());result.put("memoryHits",store.memoryHits);result.put("diskHits",store.diskHits);result.put("corruptions",store.corruptions);result.put("diskWrites",store.writes);return result;}
}
