package async_runtime.io;

import future.Future;

import java.nio.ByteBuffer;

public interface Writable<E extends Throwable> {
    Future<Integer, E> write(ByteBuffer buffer);
    Future<Integer, E> write_all(ByteBuffer buffer);
}
