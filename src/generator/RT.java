package generator;


import generator.runtime.GeneratorClassLoader;

public class RT {
    public static void runWithGeneratorSupport(Class<? extends Runnable> clazz){
        var loader = new GeneratorClassLoader(RT.class.getClassLoader());
        try{
            ((Runnable)loader.loadClass(clazz.getName()).getConstructor().newInstance()).run();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
