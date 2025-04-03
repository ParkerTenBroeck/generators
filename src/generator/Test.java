package generator;


public class Test {
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

    public static Gen<Double, Void>test(double[] nyas){
        for(var d : nyas){
            Gen.yield(d);
        }
        return Gen.ret();
    }

    public sealed interface Token{}
    public enum Punc implements Token {
        LPar,
        RPar,
        Add,
        Sub,
        Div,
        Mul,
        Equals,
        Carrot,
        Comma
    }

    public record Ident(String ident) implements Token {}
    public record Numeric(double val) implements Token {}

    public static Gen<Token, Void> parse(String input) {
        int start;
        int pos = 0;
        while(true){
            if (input.length() <= pos)
                return Gen.ret();
            start = pos;
            char curr = input.charAt(pos++);

            switch (curr){
                case '(' -> Gen.yield(Punc.LPar);
                case ')' -> Gen.yield(Punc.RPar);
                case '+' -> Gen.yield(Punc.Add);
                case '-' -> Gen.yield(Punc.Sub);
                case '/' -> Gen.yield(Punc.Div);
                case '*' -> Gen.yield(Punc.Mul);
                case ',' -> Gen.yield(Punc.Comma);
                case '^' -> Gen.yield(Punc.Carrot);
                case '=' -> Gen.yield(Punc.Equals);
                case char c when Character.isWhitespace(c) -> {}
                case char c when Character.isAlphabetic(c) -> {
                    while((Character.isLetter(curr) || Character.isLetterOrDigit(curr))){
                        if(pos>=input.length())break;
                        curr = input.charAt(pos++);
                    }
                    Gen.yield(new Ident(input.substring(start, pos-1)));
                }
                case char c when '0' <= c && c <= '9' -> {
                    boolean exp = false;
                    while(('0' <= curr && curr <= '9') || curr == '_' || curr == '.'){
                        if(pos>=input.length())break;
                        curr = input.charAt(pos++);
                        if(curr=='e'||curr=='E'){
                            if(exp) throw new RuntimeException("Exponent Already included");
                            exp = true;
                            if(pos+2>=input.length())
                                throw new RuntimeException("Expected another character after exponent");
                            curr = input.charAt(++pos); pos++;
                        }
                    }

                    Gen.yield(new Numeric(Double.parseDouble(input.substring(start, pos-1).replace("_", ""))));
                }
                default -> throw new RuntimeException("Invalid char " + curr);
            }
        }
    }

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
}
