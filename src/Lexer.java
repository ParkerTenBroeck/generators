import generator.Gen;

public class Lexer {


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
}
