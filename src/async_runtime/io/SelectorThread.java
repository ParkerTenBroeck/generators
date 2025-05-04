package async_runtime.io;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayDeque;

public abstract class SelectorThread<T extends SelectableChannel, A> extends Thread{

    private final Selector selector;

    private record  ToRegister<T, A>(T sc, int ops, A waker){}
    private final ArrayDeque<ToRegister<T, A>> to_register = new ArrayDeque<>();


    public SelectorThread(String name) throws IOException {
        selector = Selector.open();
        this.setName(name + " Polling Thread");
        this.setDaemon(true);
        this.start();
    }

    public abstract void handle(SelectionKey key, T t, A a) throws IOException;

    public void register(T sc, int ops, A waker){
        synchronized (to_register){
            to_register.add(new ToRegister<>(sc, ops, waker));
        }
        selector.wakeup();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()){
            try{
                synchronized (to_register){
                    while(!to_register.isEmpty()){
                        var to = to_register.poll();
                        to.sc.register(selector, to.ops, to.waker);
                    }
                }

                selector.select();
                var keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    handle(key, (T)key.channel(), (A)key.attachment());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
