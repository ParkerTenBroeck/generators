import generator.Fun;
import generator.Gen;

public class Main implements Runnable {
    public static void main(String[] args) {
        Fun.runWithGeneratorSupport(Main.class);
    }

    @Override
    public void run() {
//        await();
        lexer();
    }

    void await(){
        var gen = Examples.awaitTest(10);
        while(true) {
            var next = gen.next();
            if(next instanceof Gen.Yield(var e)) System.out.println(e);
            else if(next instanceof Gen.Ret(var ret)){
                System.out.println(ret);
                break;
            }
        }
    }

    void lexer(){
        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
//        var gen = Examples.test(new double[]{1,2,3,4});
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}