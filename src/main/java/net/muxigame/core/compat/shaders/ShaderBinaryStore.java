package net.muxigame.core.compat.shaders;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.util.*;
/** Bounded application-owned data only. No OpenGL objects, driver cache, or shader text. */
public final class ShaderBinaryStore {
    public record Binary(int format,byte[] data){}
    public static final int ENTRY_LIMIT=8*1024*1024,COUNT_LIMIT=256;
    public static final long BYTE_LIMIT=64L*1024*1024;
    private final LinkedHashMap<String,Binary> memory=new LinkedHashMap<>(16,.75f,true);
    private final Path directory;private long bytes;
    public long memoryHits,diskHits,corruptions,writes,evictions;
    public ShaderBinaryStore(Path directory)throws IOException{this.directory=directory.toAbsolutePath().normalize();Files.createDirectories(this.directory);if(Files.isSymbolicLink(this.directory)||!this.directory.toRealPath().equals(this.directory))throw new IOException("Cache directory is a symbolic link");trimDisk();}
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private Path file(String key){if(!key.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid content key");Path result=directory.resolve(key+".bin").normalize();if(!result.getParent().equals(directory))throw new IllegalArgumentException();return result;}
    private void remember(String key,Binary value){Binary previous=memory.put(key,value);if(previous!=null)bytes-=previous.data.length;bytes+=value.data.length;while(memory.size()>COUNT_LIMIT||bytes>BYTE_LIMIT){var iterator=memory.entrySet().iterator();var entry=iterator.next();bytes-=entry.getValue().data.length;iterator.remove();evictions++;}}
    public Binary get(String key)throws IOException{
        Binary value=memory.get(key);if(value!=null){memoryHits++;return value;}
        Path path=file(key);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return null;
        try{if(Files.size(path)>ENTRY_LIMIT+512)throw new IOException("Oversized cache record");try(var in=new DataInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS))){if(in.readInt()!=0x4D424332||in.readInt()!=2||!in.readUTF().equals(key))throw new IOException("Invalid cache header");int format=in.readInt(),length=in.readInt();if(length<=0||length>ENTRY_LIMIT)throw new IOException("Invalid cache length");String checksum=in.readUTF();byte[] data=in.readNBytes(length);if(data.length!=length||in.read()!=-1||!digest(data).equals(checksum))throw new IOException("Cache integrity mismatch");value=new Binary(format,data);}}
        catch(IOException|RuntimeException failure){corruptions++;Files.deleteIfExists(path);return null;}
        remember(key,value);diskHits++;Files.setLastModifiedTime(path,FileTime.fromMillis(System.currentTimeMillis()));return value;
    }
    public void put(String key,int format,byte[] data)throws IOException{
        if(data.length<=0||data.length>ENTRY_LIMIT)return;Binary value=new Binary(format,data.clone());remember(key,value);
        Path path=file(key),temp=directory.resolve(key+"."+UUID.randomUUID()+".tmp");
        try{try(var stream=new DataOutputStream(Files.newOutputStream(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS))){stream.writeInt(0x4D424332);stream.writeInt(2);stream.writeUTF(key);stream.writeInt(format);stream.writeInt(data.length);stream.writeUTF(digest(data));stream.write(data);}try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}writes++;trimDisk();}finally{Files.deleteIfExists(temp);}
    }
    public void remove(String key)throws IOException{Binary old=memory.remove(key);if(old!=null)bytes-=old.data.length;Files.deleteIfExists(file(key));}
    private List<Path> ownedFiles()throws IOException{var paths=new ArrayList<Path>();try(var files=Files.newDirectoryStream(directory,"*.bin")){for(Path path:files)if(path.getFileName().toString().matches("[0-9a-f]{64}\\.bin")&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))paths.add(path);}return paths;}
    private void trimDisk()throws IOException{var files=ownedFiles();files.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0L;}}));long total=0;for(Path path:files)total+=Files.size(path);int count=files.size();for(Path path:files){if(count<=COUNT_LIMIT&&total<=BYTE_LIMIT)break;total-=Files.size(path);Files.delete(path);count--;evictions++;}}
    public void clear(boolean disk)throws IOException{memory.clear();bytes=0;if(disk)for(Path path:ownedFiles())Files.delete(path);}
    public int entries(){return memory.size();}public long bytes(){return bytes;}
    public int diskEntries()throws IOException{return ownedFiles().size();}
}
