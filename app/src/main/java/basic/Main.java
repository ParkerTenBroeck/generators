package basic;

import com.parkertenbroeck.bcsm.RT;
import com.parkertenbroeck.bcsm.loadtime.StateMachineClassLoader;
import com.parkertenbroeck.generator.Gen;

public class Main {
    public static void main(String[] args) {
        RT.runWithStateMachines(StateMachineClassLoader.Config.builtin().write_classes("build/modified/generators/"), (Object) args);

       primes();
    }

    static void primes(){
        var gen = Gens.primes();
        while(gen.next() instanceof Gen.Yield(var tok)) {
            System.out.println(tok);
        }
    }
}