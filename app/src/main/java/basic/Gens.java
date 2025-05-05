package basic;

import com.parkertenbroeck.generator.Gen;

public class Gens {
    public static Gen<Long, Void> primes(){
        long number = 1;
        Gen.yield(2L);
        outer: while(true){
            number += 2;
            for(long i=2; i <= Math.sqrt(number); i ++){
                if(number%i==0)continue outer;
            }
            Gen.yield(number);
        }
    }
}
