import async_runtime.Delay;
import async_runtime.Jokio;
import async_runtime.net.ServerSocket;
import async_runtime.net.Socket;
import future.Future;
import future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AsyncExamples {

    static long sent = 0;
    static long received = 0;
    public static Future<Void, RuntimeException> test(){
        Jokio.runtime().await().spawn(server());


        for(int i = 0; i < 200; i ++){
            var builder = new StringBuilder();
            for(int c = 0; c < 4096*16*3; c ++)
                builder.append((char)((Math.random()*('z'-'a')+'a')));
            Jokio.runtime().await().spawn(echoForever(builder.toString()));
        }
        var start = System.currentTimeMillis();
        while(true){
            Delay.delay(100).await();
            var now = System.currentTimeMillis();
            System.out.println(sent + " " + received  + " " + (now-start));
            start = now;
        }
    }

    public static Future<Integer, RuntimeException> number(){
        return Future.ret(12);
    }


    public static Future<Void, IOException> server(){
        try(var ss = ServerSocket.bind(new InetSocketAddress("0.0.0.0", 42069))){
            while (true){
                var socket = ss.accept().await();
                Jokio.runtime(Waker.waker()).spawn(echo(socket));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Future.ret(null);
    }

    public static Future<Void, IOException> echo(Socket socket){
        try(socket){
            var buffer = ByteBuffer.allocate(4096*16*3);
            while(true){
                var read = socket.read(buffer).await();
                buffer.clear().limit(read);
                socket.write_all(buffer).await();
                buffer.clear();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Future<Void, IOException> echoForever(String message){
        byte[] msg_bytes = message.getBytes(StandardCharsets.UTF_8);
        try(var socket = Socket.connect(new InetSocketAddress("localhost", 42069)).await()){
            var buffer = ByteBuffer.allocate(message.length());
            while(true){
                buffer.limit(message.length()).put(msg_bytes).position(0);
                var wrote = socket.write_all(buffer).await();
                sent++;
                buffer.clear().limit(wrote);
                socket.read_all(buffer).await();
//                if(!buffer.position(0).equals(ByteBuffer.wrap(msg_bytes)))
//                    throw new RuntimeException();
                received++;
                buffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Future.ret(null);
    }
}
