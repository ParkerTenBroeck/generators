package async_runtime.io.fs;

import async_runtime.io.Readable;
import async_runtime.io.Writable;
import future.Future;
import future.Waker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class File implements AutoCloseable, Readable<IOException>, Writable<IOException> {
    private final AsynchronousFileChannel channel;

    protected File(AsynchronousFileChannel sc){
        this.channel = sc;
    }

    public static File open(Path path) throws IOException {
        return new File(AsynchronousFileChannel.open(path, StandardOpenOption.READ));
    }

    public long size() throws IOException {
        return channel.size();
    }


    @Override
    public Future<Integer, IOException> read(ByteBuffer buffer) {
        return read(buffer, 0);
    }

    @Override
    public Future<Integer, IOException> read_all(ByteBuffer buffer) {
        return read_all(buffer, 0);
    }

    @Override
    public Future<Integer, IOException> write(ByteBuffer buffer) {
        return write(buffer, 0);
    }

    @Override
    public Future<Integer, IOException> write_all(ByteBuffer buffer) {
        return write_all(buffer, 0);
    }

    public Future<Integer, IOException> write_all(ByteBuffer buffer, long position){
        return new Future<>() {
            int written = 0;
            Throwable t;
            @Override
            public Object poll(Waker waker) throws IOException {
                if(t!=null)throw (IOException) t;
                if(!buffer.hasRemaining()) return written;
                channel.write(buffer, written+position, waker, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Waker attachment) {
                        written = result;
                        waker.wake();
                    }

                    @Override
                    public void failed(Throwable exc, Waker attachment) {
                        t = exc;
                        attachment.wake();;
                    }
                });
                return Pending.INSTANCE;
            }
        };
    }

    public Future<Integer, IOException> write(ByteBuffer buffer, long position){
        return new Future<>() {
            int written = 0;
            Throwable t;
            @Override
            public Object poll(Waker waker) throws IOException {
                if(t!=null)throw (IOException) t;
                if(written!=0) return written;
                channel.write(buffer, written+position, waker, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Waker attachment) {
                        written = result;
                        waker.wake();
                    }

                    @Override
                    public void failed(Throwable exc, Waker attachment) {
                        t = exc;
                        attachment.wake();;
                    }
                });
                return Pending.INSTANCE;
            }
        };
    }

    public Future<Integer, IOException> read(ByteBuffer buffer, long position){
        return new Future<>() {
            int read = 0;
            boolean eos = false;
            Throwable t;
            @Override
            public Object poll(Waker waker) throws IOException {
                if(t!=null)throw (IOException) t;
                if(eos) return read;
                if(read!=0) return read;
                channel.read(buffer, read+position, waker, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Waker attachment) {
                        if(result==-1)eos = true;
                        else read += result;
                        waker.wake();
                        waker.wake();
                    }

                    @Override
                    public void failed(Throwable exc, Waker attachment) {
                        t = exc;
                        attachment.wake();;
                    }
                });
                return Pending.INSTANCE;
            }
        };
    }

    public Future<Integer, IOException> read_all(ByteBuffer buffer, long position){
        return new Future<>() {
            int read = 0;
            boolean eos = false;
            Throwable t;
            @Override
            public Object poll(Waker waker) throws IOException {
                if(t!=null)throw (IOException) t;
                if(eos) return read;
                if(!buffer.hasRemaining()) return read;
                channel.read(buffer, read+position, waker, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Waker attachment) {
                        if(result==-1)eos = true;
                        else read += result;
                        waker.wake();
                    }

                    @Override
                    public void failed(Throwable exc, Waker attachment) {
                        t = exc;
                        attachment.wake();;
                    }
                });
                return Pending.INSTANCE;
            }
        };
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
