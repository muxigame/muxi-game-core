import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpClient;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal runtime diagnosis. Local self-connections only; no HTTP requests or remote destinations. */
public class SocketRuntimeProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("pid="+ProcessHandle.current().pid());
        System.out.println("java="+System.getProperty("java.runtime.version"));
        Path socket=Path.of(args[1]).resolve("probe-"+ProcessHandle.current().pid()+".sock");
        try {
            switch(args[0]) {
                case "http" -> { try(HttpClient client=HttpClient.newHttpClient()) {} }
                case "unix" -> {
                    try(ServerSocketChannel server=ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                        var address=UnixDomainSocketAddress.of(socket);
                        server.bind(address);
                        System.out.println("boundAddressMatches="+address.equals(server.getLocalAddress()));
                        System.out.println("socketPathBytes="+socket.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                        try(SocketChannel client=SocketChannel.open(address); SocketChannel accepted=server.accept()) {}
                    }
                }
                case "tcp" -> {
                    try(ServerSocketChannel server=ServerSocketChannel.open(StandardProtocolFamily.INET)) {
                        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
                        try(SocketChannel client=SocketChannel.open(server.getLocalAddress()); SocketChannel accepted=server.accept()) {}
                    }
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
            System.out.println("success=true");
        } catch(Throwable failure) {
            System.out.println("success=false");
            failure.printStackTrace(System.out);
        } finally {
            try { Files.deleteIfExists(socket); }
            catch(java.io.IOException cleanupFailure) {
                System.out.println("socketCleanupFailure="+cleanupFailure);
            }
        }
    }
}
