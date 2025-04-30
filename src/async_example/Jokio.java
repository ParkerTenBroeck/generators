package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class Jokio implements Runnable{

    private class Task<T> implements Waker{
        public final Future<T> future;

        private Task(Future<T> future) {
            this.future = future;
        }

        @Override
        public void wake() {
            woke.add(this);
            synchronized (Jokio.this){
                Jokio.this.notifyAll();
            }
        }

        public Jokio runtime(){
            return Jokio.this;
        }
    }

    public static Future<Jokio> runtime(){
        return new Future<>() {
            @Override
            public Jokio poll(Waker waker) {
                return ((Task<?>)waker).runtime();
            }
        };
    }

    public static Jokio runtime(Waker waker){
        return ((Task<?>)waker).runtime();
    }

    private final AtomicInteger current = new AtomicInteger(0);
    private final ConcurrentLinkedDeque<Task<?>> woke = new ConcurrentLinkedDeque<>();

    public void blocking(Future<?> fut){
        spawn(fut).run();
    }

    public Jokio spawn(Future<?> future){
        var task = new Task<>(future);
        current.getAndIncrement();
        woke.add(task);
        return this;
    }

    @Override
    public void run(){
        while(current.get() > 0) {
            synchronized (this) {
                while (woke.isEmpty()) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            var task = woke.poll();
            var result = task.future.poll(task);
            if(result!=Future.Pending.INSTANCE) {
                current.getAndDecrement();
                System.out.println(result);
            }
        }
    }
}
