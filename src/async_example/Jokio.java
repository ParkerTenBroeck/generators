package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;

public class Jokio implements Runnable{

    private class Task<T> implements Waker{
        public final Future<T> future;

        private Task(Future<T> future) {
            this.future = future;
        }

        @Override
        public void wake() {
            synchronized (Jokio.this){
                woke.add(this);
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

    private final HashSet<Task<?>> current = new HashSet<>();
    private final Queue<Task<?>> woke = new ArrayDeque<>();

    public void blocking(Future<?> fut){
        spawn(fut).run();
    }

    public synchronized Jokio spawn(Future<?> future){
        var task = new Task<>(future);
        current.add(task);
        woke.add(task);
        return this;
    }

    @Override
    public synchronized void run(){
        while(!current.isEmpty()) {
            while(woke.isEmpty()) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            var task = woke.poll();
            var result = task.future.poll(task);
            if(result!=Future.Pending.INSTANCE) {
                current.remove(task);
                System.out.println(result);
            }
        }
    }
}
