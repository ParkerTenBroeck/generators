package async_runtime;

import future.Future;
import future.Waker;

import java.util.ArrayDeque;
import java.util.HashSet;

public class Jokio implements Runnable{
    private class Task<T, E extends Throwable> implements Waker{
        public final Future<T, E> future;

        private Task(Future<T, E> future) {
            this.future = future;
        }

        @Override
        public void wake() {
            synchronized (Jokio.this){
                if(currentSet.contains(this)&&wokeSet.add(this))
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

    private final ArrayDeque<Task<?, ?>> wokeQueue = new ArrayDeque<>();
    private final HashSet<Task<?, ?>> wokeSet = new HashSet<>();
    private final HashSet<Task<?, ?>> currentSet = new HashSet<>();

    public void blocking(Future<?, RuntimeException> fut){
        spawn(fut).run();
    }

    public Jokio spawn(Future<?, ?> future){
        var task = new Task<>(future);
        synchronized (this){
            currentSet.add(task);
            wokeQueue.add(task);
            wokeSet.add(task);
        }
        return this;
    }

    @Override
    public void run(){
        while(true) {
            synchronized (this) {
                if(currentSet.isEmpty())break;
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
                if(!currentSet.contains(task))continue;
            }
            Object result;
            try{
                result = task.future.poll(task);
            }catch (Throwable t){
                System.out.println("Future " + task.future + " Threw Exception");
                t.printStackTrace();
                synchronized (this){
                    currentSet.remove(task);
                }
                continue;
            }
            synchronized (this){
                if(result!=Future.Pending.INSTANCE) {
                    currentSet.remove(task);
                    System.out.println(result);
                }
            }

        }
    }
}
