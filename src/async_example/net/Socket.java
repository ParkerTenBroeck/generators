package async_example.net;

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

                        if (!key.isValid()) {
                        }else if(key.isAcceptable()){
                        }else if(key.isConnectable()){
                            c.finishConnect();
                            w.wake();
                        }else if(key.isReadable()){
                            w.wake();
                        }else if(key.isWritable()){
                            w.wake();
                        }
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

    protected Socket(SocketChannel sc){
        this.socket = sc;
    }

    public static Future<Socket, IOException> connect(InetSocketAddress inet) {
        return new Future<>() {
            public SocketChannel socket;
            @Override
            public Object poll(Waker waker) throws IOException {
                if(socket==null){
                    socket = SocketChannel.open();
                    socket.configureBlocking(false);
                    var connected = socket.connect(inet);
                    if(!connected) {
                        register(socket, SelectionKey.OP_CONNECT, waker);
                        return Pending.INSTANCE;
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

    public Future<Integer, IOException> write_all(ByteBuffer buffer){
        return new Future<>() {
            int wrote = 0;
            @Override
            public Object poll(Waker waker) throws IOException {
                wrote += socket.write(buffer);
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

    public Future<Integer, IOException> read(ByteBuffer buffer){
        return new Future<>() {
            int read = 0;
            @Override
            public Object poll(Waker waker) throws IOException {
                read += socket.read(buffer);
                if(read>0) return read;
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

    public Future<Integer, IOException> read_all(ByteBuffer buffer){
        return new Future<>() {
            int read = 0;
            @Override
            public Object poll(Waker waker) throws IOException {
                read += socket.read(buffer);
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
