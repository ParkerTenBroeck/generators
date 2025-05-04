import async_runtime.Delay;
import async_runtime.Jokio;
import async_runtime.Util;
import generators.RT;
import future.Future;
import gen.Gen;
import generators.loadtime.future.Cancellation;

import java.util.function.Supplier;

public class Main implements Runnable {
    public static void main(String[] args) {
        RT.runWithGeneratorSupport(Main.class);
    }


    static class Meow implements AutoCloseable{
        {System.out.println("CREATED");}
        @Override
        public void close() {
            System.out.println("CLOSED");
        }
        public static AutoCloseable test(){
            return new Meow();
        }
    }
    synchronized Future<Void, RuntimeException> nya(){

        try(@Cancellation("close") var nya = Meow.test()){
            System.out.println("POLLED");
            Future.yield();
            System.out.println("POLLED");
            Future.yield();
            int beep = 1;
            System.out.println("POLLED");
            Future.yield();
            System.out.println("POLLED");
            Future.yield();
            return Future.ret(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public void run() {
//        new Jokio().blocking();
        var future = nya();
        future.poll(null);
        future.poll(null);
        future.cancel();
//        new Jokio().blocking(AsyncExamples.meow());
//        new Jokio().blocking(new AsyncExamples().meow2());
//        async_lambda(() -> {
//            System.out.println("START");
//            Delay.delay(100).await();
//            System.out.println("END");
//            return Future.ret();
//        });
//        async_lambda(() -> {
//            var start = System.currentTimeMillis();
//            while(true){
//                Util.first(Delay.delay(500), Delay.delay(1000)).await();
//                var end = System.currentTimeMillis();
//                System.out.println(end-start);
//                start = end;
//            }
//        });
//        lexer();
//        await();
    }

    void async_lambda(Supplier<Future<?, ?>> lambda){
        new Jokio().blocking(lambda.get());
    }


    void await(){
        new Jokio().blocking(AsyncExamples.test());
    }


    void lexer(){
        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}