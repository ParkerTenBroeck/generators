package com.parkertenbroeck.async_runtime.io.net;

import com.parkertenbroeck.async_runtime.io.Readable;
import com.parkertenbroeck.async_runtime.io.SelectorThread;
import com.parkertenbroeck.async_runtime.io.Writable;
import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.future.Waker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Socket implements AutoCloseable, Readable<IOException>, Writable<IOException> {
    private final static SelectorThread<SocketChannel, Waker> SELECTOR;

    static {
        try {
            SELECTOR = new SelectorThread<>("Socket") {
                @Override
                public void handle(SelectionKey key, SocketChannel c, Waker w) throws IOException {
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
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                        SELECTOR.register(socket, SelectionKey.OP_CONNECT, waker);
                        return Pending.INSTANCE;
                    }
                }
                if(socket.isConnected()) return new Socket(socket);
                return Pending.INSTANCE;
            }
        };
    }

    public <T> Socket set_options(SocketOption<T> option, T value) throws IOException{
        socket.setOption(option, value);
        return this;
    }

    public SocketAddress local_address() throws IOException {
        return socket.getLocalAddress();
    }

    public SocketAddress remote_address() throws IOException {
        return socket.getRemoteAddress();
    }

    @Override
    public Future<Integer, IOException> write(ByteBuffer buffer){
        return waker -> {
            var wrote = socket.write(buffer);
            if(wrote!=0) return wrote;
            SELECTOR.register(socket, SelectionKey.OP_WRITE, waker);
            return Future.Pending.INSTANCE;
        };
    }

    @Override
    public Future<Integer, IOException> write_all(ByteBuffer buffer){
        var wrote = buffer.remaining();
        return waker -> {
            socket.write(buffer);
            if(!buffer.hasRemaining()) return wrote;
            SELECTOR.register(socket, SelectionKey.OP_WRITE, waker);
            return Future.Pending.INSTANCE;
        };
    }

    @Override
    public Future<Integer, IOException> read(ByteBuffer buffer){
        return waker -> {
            var read = socket.read(buffer);
            if(read!=0) return read;
            SELECTOR.register(socket, SelectionKey.OP_READ, waker);
            return Future.Pending.INSTANCE;
        };
    }

    @Override
    public Future<Integer, IOException> read_all(ByteBuffer buffer){
        int read = buffer.remaining();
        return waker -> {
            var read_now = socket.read(buffer);
            if(read_now ==-1)throw new IOException("Reached EOS while filling buffer");
            if(!buffer.hasRemaining()) return read;
            SELECTOR.register(socket, SelectionKey.OP_READ, waker);
            return Future.Pending.INSTANCE;
        };
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
