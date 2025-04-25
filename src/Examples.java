import generator.gen.Gen;

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

    public static Gen<Void, String> awaitTest2(int number){
        for(int i = 0; i < number; i ++)Gen.yield();
        return Gen.ret(number+"");
    }

    public static class Meow implements AutoCloseable{
        {
            System.out.println("Opened");
        }
        @Override
        public void close() {
            System.out.println("Closed");
        }
    }

    public static Gen<Void, String> awaitTest(int number){
        try(var m = new Meow()){
            return Gen.ret(awaitTest2(number).await());
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
