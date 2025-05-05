package sockets;

import com.parkertenbroeck.async_runtime.Jokio;
import com.parkertenbroeck.bcsm.RT;
import com.parkertenbroeck.bcsm.loadtime.StateMachineClassLoader;

public class Main {
    public static void main(String[] args) {
        RT.runWithStateMachines(StateMachineClassLoader.Config.builtin().write_classes("build/modified/generators/"), (Object) args);

        await();
    }

    static void await(){
        try{
            new Jokio().blocking(Sockets.run());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}