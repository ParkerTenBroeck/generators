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

    @Override
    public void run() {
        lexer();
        await();
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