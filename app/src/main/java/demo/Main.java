package demo;

import com.parkertenbroeck.async_runtime.Jokio;
import com.parkertenbroeck.async_runtime.io.fs.File;
import com.parkertenbroeck.generators.RT;
import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.gen.Gen;
import com.parkertenbroeck.generators.loadtime.future.Cancellation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main implements Runnable {
    public static void main(String[] args) {
        RT.runWithGeneratorSupport(Main.class);
    }

    @Override
    public void run() {
//        lexer();
        await();
//        try {
//            System.out.println(new Jokio().blocking(files()));
//        } catch (IOException ignore) {}
    }
//
//    static Future<String, IOException> files() throws IOException{
//        try(@Cancellation("close") var file = File.open(Path.of("./src/Main.java"))){
//            var buf = ByteBuffer.allocate((int) file.size());
//            var read = file.read_all(buf).await();
//            return Future.ret(new String(buf.array(), 0, read));
//        }
//    }
//
//    <T, E extends Throwable> T async_lambda(Supplier<Future<T, E>> lambda) throws E{
//        return new Jokio().blocking(lambda.get());
//    }
//
//
    private static int nya = 0;
class Meow{
    void test(){
        nya = 2;
    }
}
    void await(){

        new Meow().test();
        try{
            new Jokio().blocking(AsyncExamples.run());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
//
//
//    void lexer(){
//        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
//        while(gen.next() instanceof Gen.Yield(var tok)) {
//            System.out.println(tok);
//        }
//    }
}