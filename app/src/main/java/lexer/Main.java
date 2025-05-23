package lexer;

import com.parkertenbroeck.generator.Gen;
import com.parkertenbroeck.bcsm.RT;
import com.parkertenbroeck.bcsm.loadtime.StateMachineClassLoader;

public class Main {
    public static void main(String[] args) {
        RT.runWithStateMachines(StateMachineClassLoader.Config.builtin().write_classes("build/modified/generators/"), (Object) args);

       lexer();
    }

    static void lexer(){
        var gen = Lexer.parse("f7(x,y,z,w, u,v, othersIg) = v-(x*y+y+ln(z)^2*sin(z*pi/2))/(w*u)+sqrt(othersIg*120e-1)");
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}