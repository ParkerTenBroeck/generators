import async_example.Jokio;
import async_example.Socket;
import generator.future.Future;
import generator.future.Waker;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Examples {
    //    public static Gen<String, Void> parse(String str){
//        for(var gen = parse(str); gen.next() instanceof Gen.Yield(var item);){
//
//        }
//        return Gen.ret();
//    }

//    public static Gen<String, Void> parse(String str){
//        {
//            String meow = "10";
//            meow += "11";
//            Gen.yield(meow);
//            Gen.yield(meow);
//            Gen.yield(meow);
//            Gen.yield(meow);
//        }
//        for(var split : str.split(" ")){
//            Gen.yield(split);
//        }
//        {
//            var str2 = str;
//            while(str2.length()>10){
//                var len = str2.length();
//                Gen.yield(len+" length");
//                str2 = str2.substring(1);
//            }
//        }
//
//        while(str.length()>10){
//            var len = str.length();
//            Gen.yield(len+" length");
//            str = str.substring(1);
//        }
//        return Gen.ret();
//    }

//    public static Gen<String, Void> gen(int times, double mul) {
//        mul -= 0.5;
//        for (int i = 0; i < times; i ++) {
//            Gen.yield("iteration number: " + i*mul);
//        }
//        return Gen.ret();
//    }
//    public static Gen<String, Void> gen(int times, double mul) {
//        mul -= 0.5;
//        for (int i = 0; i < times; i++) {
//            Gen.yield("iteration number: " + i*mul);
//        }
//        return Gen.ret();
//    }

    //    public static Gen<String, Void> gen() {
//        Gen.yield("1");
//        Gen.yield("2");
//        Gen.yield("3");
//        Gen.yield("4");
//        return Gen.ret();
//    }

//    public static <T> Gen<T, Void>test(T val){
//        Gen.yield(val);
//        return Gen.ret();
//    }

//    public static Future<String> awaitTest2(int number){
//        ((Future<?>)new Delay(number)).await();
//        return Future.ret(number+"ms");
//    }
//
//
//    public Future<Integer> number(int value){
//        Waker.waker().wake();
//        Future.yield();
//        return Future.ret(value);
//    }


    public static Future<Void> test(){
        for(int i = 0; i < 1000; i ++){
            Jokio.runtime(Waker.waker()).spawn(echoForever("Message " + i + "\n"));
        }
        return Future.ret(null);
    }

    public static Future<Void> echoForever(String message){
        try(var socket = Socket.connect(new InetSocketAddress("45.79.112.203", 4242)).await()){
            var buffer = ByteBuffer.allocate(500);
            while(true){
                buffer.limit(message.length()).put(message.getBytes(StandardCharsets.UTF_8)).position(0);
                var wrote = socket.write_all(buffer).await();
                buffer.clear().limit(wrote);
                var read = socket.read_all(buffer).await();
                System.out.print(new String(buffer.array(), 0, read));
                buffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Future.ret(null);
    }

//    public Future<String> closing(int number){
//        try(var m = new Meow()){
//            var result = awaitTest2(number).await();
//            return Future.ret(result);
//        }
//    }

    public static class Meow implements AutoCloseable{
        {
            System.out.println("OPen");
        }

        @Override
        public void close() {
            System.out.println("Close");
        }
    }

//    public static Gen<Double, Void> test(double[] nyas){
//
//        var test = 1+switch(nyas[0]){
//            case 1.0 -> {
//                Gen.yield(11);
//                yield 2;
//            }
//            default -> {
//                Gen.yield(12);
//                yield 4;
//            }
//        };
//        for(var d : nyas){
//            Gen.yield(d);
//        }
//        return Gen.ret();
//    }
}
