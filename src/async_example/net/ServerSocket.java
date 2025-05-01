package async_example.net;

import generator.future.Future;
import generator.future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

public class ServerSocket implements AutoCloseable{

    private final static Selector SELECTOR;
    private record ToRegister(ServerSocketChannel sc, int ops, Waker waker){}
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
                        var c = (ServerSocketChannel)key.channel();
                        var w = (Waker)key.attachment();

                        if (!key.isValid()) {
                        }else if(key.isAcceptable()){
                            w.wake();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        thread.setName("ServerSocket Polling Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static void register(ServerSocketChannel sc, int ops, Waker waker){
        synchronized (to_register){
            to_register.add(new ToRegister(sc, ops, waker));
        }
        SELECTOR.wakeup();
    }

    private final ServerSocketChannel socket;

    private ServerSocket(ServerSocketChannel sc){
        this.socket = sc;
    }

    public static ServerSocket bind(InetSocketAddress inet) throws IOException {
        var socket = ServerSocketChannel.open();
        socket.configureBlocking(false);
        socket.bind(inet);
        return new ServerSocket(socket);
    }

    public Future<Socket, IOException> accept(){
        return new Future<>() {
            @Override
            public Object poll(Waker waker) throws IOException {
                var socc = socket.accept();
                if(socc==null) {
                    register(socket, SelectionKey.OP_ACCEPT, waker);
                    return Pending.INSTANCE;
                }
                socc.configureBlocking(false);
                return new Socket(socc);
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
