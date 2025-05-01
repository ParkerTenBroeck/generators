package async_runtime;

import future.Future;
import future.Waker;

import java.util.Timer;
import java.util.TimerTask;

public class Delay implements Future<Void, RuntimeException> {
    private final static Timer timer;
    private TimerTask task;

    static {
        timer = new Timer(true);
    }

    private int delay;
    private boolean ready;

    protected Delay(int ms) {
        if (ms < 0) throw new IllegalArgumentException("async_example.Delay cannot be negative");
        delay = ms;
    }

    public static Future<Void, RuntimeException> delay(int ms){
        return new Delay(ms);
    }

    @Override
    public void cancel() {
        if (task != null) task.cancel();
    }

    @Override
    public synchronized Object poll(Waker waker) {
        if (delay == 0) {
            ready = true;
            delay = -1;
            return null;
        }
        if (delay != -1) {
            task = new TimerTask() {
                @Override
                public void run() {
                    ready = true;
                    waker.wake();
                }
            };
            timer.schedule(task, delay);
            delay = -1;
        }

        if (ready) return null;
        return Pending.INSTANCE;
    }
}
