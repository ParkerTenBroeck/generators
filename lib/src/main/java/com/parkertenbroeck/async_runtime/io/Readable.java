package com.parkertenbroeck.async_runtime.io;

import com.parkertenbroeck.future.Future;

import java.nio.ByteBuffer;

public interface Readable<E extends Throwable> {
    Future<Integer, E> read(ByteBuffer buffer);
    Future<Integer, E> read_all(ByteBuffer buffer);
}
