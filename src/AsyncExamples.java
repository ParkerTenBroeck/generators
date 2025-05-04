import async_runtime.Jokio;
import async_runtime.io.net.ServerSocket;
import async_runtime.io.net.Socket;
import future.Future;
import generators.loadtime.future.Cancellation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AsyncExamples {

    public static Future<Void, RuntimeException> run() throws IOException {
        Jokio.runtime().await().spawn(server());

        for(int i = 0; i < 50; i ++){
            var builder = new StringBuilder();
            for(int c = 0; c < 4096; c ++)
                builder.append((char)((Math.random()*('z'-'a')+'a')));
            Jokio.runtime().await().spawn(verify_echo(builder.toString()));
        }

        return Future.ret();
    }

    public static Future<Void, IOException> server() throws IOException {
        try(@Cancellation("close") var ss = ServerSocket.bind(new InetSocketAddress("0.0.0.0", 42069))){
            while (true){
                Jokio.runtime().await().spawn(echo(ss.accept().await()));
            }
        }
    }

    public static Future<Void, IOException> echo(@Cancellation("close") Socket socket) throws IOException{
        try(socket){
            var buffer = ByteBuffer.allocate(4096);
            while(true){
                socket.read(buffer).await();
                buffer.flip();
                socket.write_all(buffer).await();
                buffer.clear();
            }
        }
    }

    public static Future<Void, IOException> verify_echo(String message) throws IOException {
        byte[] msg_bytes = message.getBytes(StandardCharsets.UTF_8);
        try(@Cancellation("close") var socket = Socket.connect(new InetSocketAddress("localhost", 42069)).await()){
            var buffer = ByteBuffer.allocate(message.length());
            while(true){
                buffer.limit(message.length()).put(msg_bytes).position(0);
                var wrote = socket.write_all(buffer).await();
                buffer.clear().limit(wrote);
                socket.read_all(buffer).await();
                if(!buffer.position(0).equals(ByteBuffer.wrap(msg_bytes)))
                    throw new RuntimeException();
                buffer.clear();
            }
        }
    }
}
