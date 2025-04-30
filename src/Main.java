import async_example.Jokio;
import generator.RT;
import generator.future.Future;
import generator.future.Waker;
import generator.gen.Gen;

public class Main implements Runnable {
    public static void main(String[] args) {
        RT.runWithGeneratorSupport(Main.class);
    }

    @Override
    public void run() {
        await();
//        lexer();
//        lambda();
    }

//    void lambda(){
//        var gen = ((Supplier<Gen<Integer, String>>)() -> {
//            Gen.yield(12);
//            return Gen.ret("hello");
//        }).get();
//
//        while(true) {
//            var next = gen.next();
//            if(next instanceof Gen.Yield(var e)) System.out.println(e);
//            else if(next instanceof Gen.Ret(var ret)){
//                System.out.println(ret);
//                break;
//            }
//        }
//    }

    Object simple_async_rt(Future<?> fut){
        final var waker = new Waker(){
            @Override
            public void wake() {
                synchronized (this){
                    this.notifyAll();
                }
            }
        };
        while(true) {
            var next = fut.poll(waker);
            if(!(next instanceof Future.Pending)){
                return next;
            }
            System.out.println("Pending");
            synchronized (waker){
                try {
                    waker.wait();
                } catch (InterruptedException ignore) {}
            }
            System.out.println("Woke");
        }
    }

    void await(){
        new Jokio().blocking(Examples.test());
    }


    void lexer(){
        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
//        var gen = Examples.test(new double[]{1,2,3,4});
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}