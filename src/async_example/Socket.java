package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

public class Socket implements AutoCloseable{

    private final static Selector SELECTOR;
    private record ToRegister(SocketChannel sc, int ops, Waker waker){}
    private final static ArrayDeque<ToRegister> to_register = new ArrayDeque<>();
    static{
        try {
            SELECTOR = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var thread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()){
                try{
                    synchronized (to_register){
                        while(!to_register.isEmpty()){
                            var to = to_register.poll();
                            to.sc.register(SELECTOR, to.ops, to.waker);
                        }
                    }

                    SELECTOR.select();
                    var keys = SELECTOR.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();
                        var c = (SocketChannel)key.channel();
                        var w = (Waker)key.attachment();

                        System.out.println(key);
                        if (!key.isValid()) {
                        }
                        if(key.isAcceptable()){

                        }else if(key.isConnectable()){
                            c.finishConnect();
                        }else if(key.isReadable()){
                        }else if(key.isWritable()){

                        }
                        w.wake();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        thread.setName("Socket Polling Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static void register(SocketChannel sc, int ops, Waker waker){
        synchronized (to_register){
            to_register.add(new ToRegister(sc, ops, waker));
        }
        SELECTOR.wakeup();
    }

    private final SocketChannel socket;

    private Socket(SocketChannel sc){
        this.socket = sc;
    }

    public static Future<Socket> connect(InetSocketAddress inet) {
        return new Future<>() {
            public SocketChannel socket;
            @Override
            public Object poll(Waker waker) {
                if(socket==null){
                    try{
                        socket = SocketChannel.open();
                        socket.configureBlocking(false);
                        var connected = socket.connect(inet);
                        if(!connected) {
                            register(socket, SelectionKey.OP_CONNECT, waker);
                            return Pending.INSTANCE;
                        }
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }
                if(socket.isConnected()) return new Socket(socket);
                return Pending.INSTANCE;
            }

            @Override
            public void cancel() {
                try {
                    if(socket!=null) socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public Future<Integer> write_all(ByteBuffer buffer){
        return new Future<>() {
            int wrote = 0;
            @Override
            public Object poll(Waker waker) {
                try {
                    wrote += socket.write(buffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if(!buffer.hasRemaining()) return wrote;
                register(socket, SelectionKey.OP_WRITE, waker);
                return Pending.INSTANCE;
            }

            @Override
            public void cancel() {
                try {
                    if(socket!=null) socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public Future<Integer> read_all(ByteBuffer buffer){
        return new Future<>() {
            int read = 0;
            @Override
            public Object poll(Waker waker) {
                try {
                    read += socket.read(buffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if(!buffer.hasRemaining()) return read;
                register(socket, SelectionKey.OP_READ, waker);
                return Pending.INSTANCE;
            }

            @Override
            public void cancel() {
                try {
                    if(socket!=null) socket.close();
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
