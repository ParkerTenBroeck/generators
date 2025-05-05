package com.parkertenbroeck.async_runtime.io;

import com.parkertenbroeck.future.Future;

import java.nio.ByteBuffer;

public interface Writable<E extends Throwable> {
    Future<Integer, E> write(ByteBuffer buffer);
    Future<Integer, E> write_all(ByteBuffer buffer);
}
