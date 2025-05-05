package sockets;

import com.parkertenbroeck.async_runtime.Delay;
import com.parkertenbroeck.async_runtime.Jokio;
import com.parkertenbroeck.async_runtime.io.net.ServerSocket;
import com.parkertenbroeck.async_runtime.io.net.Socket;
import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.bcsm.loadtime.future.Cancellation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Sockets {

    private static long bytes_sent = 0;
    private static long bytes_received = 0;

    public static Future<Void, RuntimeException> run() throws IOException {
        Jokio.runtime().await().spawn(server());
        for(int i = 0; i < 50; i ++){
            var builder = new StringBuilder();
            for(int c = 0; c < 4096*2; c ++)
                builder.append((char)((Math.random()*('z'-'a')+'a')));
            Jokio.runtime().await().spawn(verify_echo(builder.toString()));
        }

        stats().await();
        return Future.ret();
    }

    public static Future<Void, RuntimeException> stats() {
        long start = System.currentTimeMillis();
        long start_s = bytes_sent;
        long start_r = bytes_received;
        while(true){
            Delay.delay(1000).await();
            long end = System.currentTimeMillis();
            var duration = (end-start)/1000.0;
            start = end;
            System.out.format("sent: % 10d B\treceived: % 10d B\tduration: % 2.3fs\n", bytes_sent, bytes_received, duration);
            System.out.format("sent: % 2.5f Gib/s\treceived: % 2.5f Gib/s\n", (bytes_sent-start_s)/duration*7.4505805969238E-9,  (bytes_received-start_r)/duration*7.4505805969238E-9);
            start_s = bytes_sent;
            start_r = bytes_received;
        }
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
            var buffer = ByteBuffer.allocate(4096*2);
            while(true){
                bytes_received = socket.read(buffer).await() + bytes_received;
                buffer.flip();
                bytes_sent = socket.write_all(buffer).await() + bytes_sent;
                buffer.clear().limit(buffer.capacity());
            }
        }
    }

    public static Future<Void, IOException> verify_echo(String message) throws IOException {
        byte[] msg_bytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer msg_cmp_buffer = ByteBuffer.wrap(msg_bytes);

        var buffer = ByteBuffer.allocate(message.length());
        buffer.put(msg_bytes).limit(msg_bytes.length).position(0);

        try(@Cancellation("close") var socket = Socket.connect(new InetSocketAddress("localhost", 42069)).await()){
            while(true){
                bytes_sent = socket.write_all(buffer).await() + bytes_sent;
                buffer.flip();
                bytes_received = socket.read_all(buffer).await() + bytes_received;
                buffer.flip();
                if(!buffer.equals(msg_cmp_buffer))
                    throw new RuntimeException();
            }
        }
    }
}
