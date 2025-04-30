import async_example.Jokio;
import async_example.Socket;
import generator.future.Future;
import generator.future.Waker;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Examples {

    public static Future<Void> test(){
        for(int i = 0; i < 1000; i ++){
            Jokio.runtime(Waker.waker()).spawn(echoForever("Message " + i + "\n"));
        }
        return Future.ret(null);
    }

    public static Future<Void> echoForever(String message){
        try(var socket = Socket.connect(new InetSocketAddress("45.79.112.203", 4242)).await()){
            var buffer = ByteBuffer.allocate(500);
            while(true){
                buffer.limit(message.length()).put(message.getBytes(StandardCharsets.UTF_8)).position(0);
                var wrote = socket.write_all(buffer).await();
                buffer.clear().limit(wrote);
                var read = socket.read_all(buffer).await();
                System.out.print(new String(buffer.array(), 0, read));
                buffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Future.ret(null);
    }
}
