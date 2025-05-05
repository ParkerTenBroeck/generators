package com.parkertenbroeck.bcms;


import com.parkertenbroeck.bcms.loadtime.StateMachineClassLoader;

import java.lang.reflect.Modifier;
import java.util.Set;

import static java.lang.StackWalker.Option.*;

public class RT {
    public static void runWithStateMachines(StateMachineClassLoader.Config config, Object... params){
        if(RT.class.getClassLoader() instanceof StateMachineClassLoader)return;

        var loader = new StateMachineClassLoader(RT.class.getClassLoader(), config);
        try{
            StackWalker walker = StackWalker.getInstance(Set.of(RETAIN_CLASS_REFERENCE, SHOW_REFLECT_FRAMES, SHOW_HIDDEN_FRAMES));
            var frame = walker.walk(s -> s.skip(1).findFirst()).get();
            var clazzL = loader.loadClass(frame.getClassName());
            var method = clazzL.getDeclaredMethod(frame.getMethodName(), frame.getMethodType().parameterArray());
            if((method.getModifiers() & Modifier.STATIC) == 0){
                method.invoke(clazzL.getConstructor().newInstance(), params);
            }else{
                method.invoke(null, params);
            }
            System.exit(0);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
