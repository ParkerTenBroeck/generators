package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Socket implements AutoCloseable{

    private final static Selector selector;

    static{
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()){
                try{
                    selector.select();
                    var keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) {

                        }else if(key.isAcceptable()){

                        }else if(key.isConnectable()){

                        }else if(key.isReadable()){

                        }else if(key.isWritable()){

                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private final SocketChannel socket;

    private Socket(SocketChannel sc){
        this.socket = sc;
    }

    public static Future<Socket> bind(InetSocketAddress inet) throws IOException{
        var socket = SocketChannel.open();
        return new Future<>() {
            @Override
            public Socket poll(Waker waker) {
                try{
                    socket.configureBlocking(false);
                    socket.socket().bind(new InetSocketAddress("localhost", 8080));
                    socket.register(selector, SelectionKey.OP_ACCEPT | SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    return new Socket(socket);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void cancel() {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
}
