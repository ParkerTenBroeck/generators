package com.parkertenbroeck.future;

public interface Waker {

    static Waker waker() {
        throw new RuntimeException("NO!");
    }

    void wake();
}
