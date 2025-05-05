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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

public class DatagramSocket implements AutoCloseable, Readable<IOException>, Writable<IOException> {
    private final static SelectorThread<DatagramChannel, Waker> SELECTOR;

    static {
        try {
            SELECTOR = new SelectorThread<>("DatagramSocket") {
                @Override
                public void handle(SelectionKey key, DatagramChannel c, Waker w) {
                    if (!key.isValid()) {
                    }else if(key.isAcceptable()){
                    }else if(key.isConnectable()){
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

    private final DatagramChannel socket;

    protected DatagramSocket(DatagramChannel sc){
        this.socket = sc;
    }

    public static DatagramSocket open(InetSocketAddress inet) throws IOException {
        var socket = DatagramChannel.open();
        socket.configureBlocking(false);
        return new DatagramSocket(socket);
    }

    public DatagramSocket bind(InetSocketAddress inet) throws IOException {
        socket.bind(inet);
        return this;
    }

    public DatagramSocket connect(InetSocketAddress inet) throws IOException {
        socket.connect(inet);
        return this;
    }

    public DatagramSocket disconnect() throws IOException{
        socket.disconnect();
        return this;
    }

    public <T> DatagramSocket set_options(SocketOption<T> option, T value) throws IOException{
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

    public Future<Integer, IOException> send(ByteBuffer buffer, SocketAddress address){
        return waker -> {
            var sent = socket.send(buffer, address);
            if(sent!=0)return sent;
            SELECTOR.register(socket, SelectionKey.OP_WRITE, waker);
            return Future.Pending.INSTANCE;
        };
    }

    public Future<SocketAddress, IOException> receive(ByteBuffer buffer){
        return waker -> {
            var address = socket.receive(buffer);
            if(address!=null)return address;
            SELECTOR.register(socket, SelectionKey.OP_READ, waker);
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
