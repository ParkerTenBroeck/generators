import async_runtime.Jokio;
import async_runtime.io.fs.File;
import generators.RT;
import future.Future;
import gen.Gen;
import generators.loadtime.future.Cancellation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Supplier;

public class Main implements Runnable {
    public static void main(String[] args) {
        RT.runWithGeneratorSupport(Main.class);
    }

    @Override
    public void run() {
//        lexer();
//        await();
        try {
            new Jokio().blocking(files());
        } catch (IOException ignore) {}
    }

    static Future<Void, Throwable> files() throws IOException{
        try(@Cancellation("close") var file = File.open(Path.of("./src/Main.java"))){
            var buf = ByteBuffer.allocate((int) file.size());
            var read = file.read_all(buf).await();
            System.out.println(new String(buf.array(), 0, read));
        }
        return Future.ret();
    }

    void async_lambda(Supplier<Future<?, ?>> lambda){
        new Jokio().blocking(lambda.get());
    }


    void await(){
        try{
            new Jokio().blocking(AsyncExamples.run());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }


    void lexer(){
        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}