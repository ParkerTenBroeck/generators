package com.parkertenbroeck.generator;

public interface Gen<Y, R> {
    Res<Y, R> next();

    static <Y, R> Gen<Y, R> yield(Y y) {
        throw new RuntimeException();
    }
    static <Y, R> Gen<Void, R> yield() {
        throw new RuntimeException();
    }
    static <Y, R> Gen<Y, R> ret(R r) {throw new RuntimeException();}
    static <Y, R> Gen<Y, Void> ret() {
        throw new RuntimeException();
    }

    default R await(){
        while(true){
            var res = next();
            if(res instanceof Ret r)return (R)r.v;
        }
    }

    sealed interface Res<Y, R>{}
    record Yield<Y, R>(Y v) implements Res<Y, R>{}
    record Ret<Y, R>(R v) implements Res<Y, R>{}
}
