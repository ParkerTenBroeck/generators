package async_example;

import generator.future.Future;
import generator.future.Waker;

import java.util.Timer;
import java.util.TimerTask;

public class Delay implements Future<String> {
    private final static Timer timer;
    private TimerTask task;

    static {
        timer = new Timer(true);
    }

    private int delay;
    private boolean ready;

    public Delay(int ms) {
        if (ms < 0) throw new IllegalArgumentException("async_example.Delay cannot be negative");
        delay = ms;
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
