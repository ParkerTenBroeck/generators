package com.parkertenbroeck.async_runtime;

import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.future.Waker;

import java.util.ArrayDeque;
import java.util.HashSet;

public class Jokio implements Runnable{
    public class TaskHandle<T, E extends Throwable> implements Waker, Future<T, E>{
        private final Future<T, E> future;
        private Object result = Pending.INSTANCE;
        private Throwable err;

        private TaskHandle(Future<T, E> future) {
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

        @Override
        public Object poll(Waker waker) throws E {
            if(err!=null)throw (E)err;
            return result;
        }

        public T blocking() throws E{
            while(result == Pending.INSTANCE) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if(err!=null)throw (E)err;
            }
            return (T) result;
        }

        @Override
        public synchronized void cancel() throws E {
            synchronized (Jokio.this){
                currentSet.remove(this);
            }
            future.cancel();
        }
    }

    public static Future<Jokio, RuntimeException> runtime(){
        return Jokio::runtime;
    }

    public static Jokio runtime(Waker waker){
        return ((TaskHandle<?, ?>)waker).runtime();
    }

    private final ArrayDeque<TaskHandle<?, ?>> wokeQueue = new ArrayDeque<>();
    private final HashSet<TaskHandle<?, ?>> wokeSet = new HashSet<>();
    private final HashSet<TaskHandle<?, ?>> currentSet = new HashSet<>();

    public <T, E extends Throwable> T blocking(Future<T, E> fut) throws E {
        var result = spawn(fut);
        run();
        return result.blocking();
    }

    public <T, E extends Throwable> TaskHandle<T, E> spawn(Future<T, E> future){
        var task = new TaskHandle<>(future);
        synchronized (this){
            currentSet.add(task);
            wokeQueue.add(task);
            wokeSet.add(task);
        }
        return task;
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
            TaskHandle<?, ?> task;
            synchronized (this){
                task = wokeQueue.poll();
                wokeSet.remove(task);
                if(!currentSet.contains(task))continue;
            }
            Object result;
            try{
                synchronized (task){
                    result = task.future.poll(task);
                }
            }catch (Throwable t){
                synchronized (task){
                    task.err = t;
                    task.notify();
                }
                System.out.println("Future " + task.future + " Threw Exception");
                t.printStackTrace();
                synchronized (this){
                    currentSet.remove(task);
                }
                continue;
            }
            synchronized (this){
                if(result!=Future.Pending.INSTANCE) {
                    synchronized (task){
                        task.result = result;
                        task.notify();
                    }
                    currentSet.remove(task);
                    System.out.println(result);
                }
            }

        }
    }
}
