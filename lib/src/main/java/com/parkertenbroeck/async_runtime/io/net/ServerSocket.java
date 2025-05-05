package com.parkertenbroeck.async_runtime.io.net;

import com.parkertenbroeck.async_runtime.io.SelectorThread;
import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class ServerSocket implements AutoCloseable {

    private final static SelectorThread<ServerSocketChannel, Waker> SELECTOR;

    static {
        try {
            SELECTOR = new SelectorThread<>("ServerSocket") {
                @Override
                public void handle(SelectionKey key, ServerSocketChannel c, Waker w) {
                    if (!key.isValid()) {
                    }else if(key.isAcceptable()){
                        w.wake();
                    }
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        return waker -> {
            var accepted = socket.accept();
            if(accepted==null) {
                SELECTOR.register(socket, SelectionKey.OP_ACCEPT, waker);
                return Future.Pending.INSTANCE;
            }
            accepted.configureBlocking(false);
            return new Socket(accepted);
        };
    }

    public <T> void set_options(SocketOption<T> option, T value) throws IOException{
        socket.setOption(option, value);
    }

    public SocketAddress local_address() throws IOException {
        return socket.getLocalAddress();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
