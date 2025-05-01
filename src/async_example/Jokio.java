package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.util.ArrayDeque;
import java.util.HashSet;

public class Jokio implements Runnable{

    public static long polled = 0;
    private class Task<T, E extends Throwable> implements Waker{
        public final Future<T, E> future;

        private Task(Future<T, E> future) {
            this.future = future;
        }

        @Override
        public void wake() {
            synchronized (Jokio.this){
                if(wokeSet.add(this))
                    wokeQueue.add(this);
                Jokio.this.notifyAll();
            }
        }

        public Jokio runtime(){
            return Jokio.this;
        }
    }

    public static Future<Jokio, RuntimeException> runtime(){
        return new Future<>() {
            @Override
            public Jokio poll(Waker waker) {
                return ((Task<?, ?>)waker).runtime();
            }
        };
    }

    public static Jokio runtime(Waker waker){
        return ((Task<?, ?>)waker).runtime();
    }

    private volatile long current = 0;
    private final ArrayDeque<Task<?, ?>> wokeQueue = new ArrayDeque<>();
    private final HashSet<Task<?, ?>> wokeSet = new HashSet<>();

    public void blocking(Future<?, RuntimeException> fut){
        spawn(fut).run();
    }

    public Jokio spawn(Future<?, ?> future){
        var task = new Task<>(future);
        synchronized (this){
            current++;
            wokeQueue.add(task);
            wokeSet.add(task);
        }
        return this;
    }

    @Override
    public void run(){
        while(current > 0) {
            synchronized (this) {
                while (wokeQueue.isEmpty()) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            Task<?, ?> task;
            synchronized (this){
                task = wokeQueue.poll();
                wokeSet.remove(task);
            }
            Object result;
            try{
                result = task.future.poll(task);
            }catch (Throwable t){
                throw new RuntimeException(t);
////                System.out.println("Future " + task.future + " Threw Exception");
////                t.printStackTrace();
//                synchronized (this){
//                    current--;
//                    polled++;
//                }
//                continue;
            }
            synchronized (this){
                if(result!=Future.Pending.INSTANCE) {
                    current--;
                    System.out.println(result);
                }
                polled++;
            }

        }
    }
}
