package generator.runtime;

import generator.Fun;
import generator.Gen;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.classfile.*;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo.TOP;

public class GeneratorClassLoader extends ClassLoader {
    private final HashMap<String, byte[]> customClazzDefMap = new HashMap<>();
    private final HashMap<String, Class<?>> customClazzMap = new HashMap<>();

    public GeneratorClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
//        if(customClazzDefMap.containsKey(name))
//            return super.loadClass(name);
        if (customClazzDefMap.get(name) instanceof byte[] bytes)
            customClazzMap.put(name, defineClass(name, bytes, 0, bytes.length));
        if (customClazzMap.get(name) instanceof Class<?> clazz)
            return clazz;
        if (name.startsWith("java"))
            return super.loadClass(name);

        var p = "/" + name.replace('.', '/') + ".class";
        try (var stream = Fun.class.getResourceAsStream(p)) {
            var bytes = Objects.requireNonNull(stream).readAllBytes();
            bytes = searchForGenerators(bytes);
            customClazzDefMap.put(name, bytes);
            customClazzMap.put(name, defineClass(name, bytes, 0, bytes.length));
            return customClazzMap.get(name);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    public byte[] searchForGenerators(byte[] in) {
        var clm = ClassFile.of(ClassFile.DebugElementsOption.PASS_DEBUG, ClassFile.LineNumbersOption.PASS_LINE_NUMBERS, ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).parse(in);
        var isGen = clm.thisClass().asSymbol().descriptorString().equals(Gen.class.descriptorString());
        return ClassFile.of(ClassFile.DebugElementsOption.PASS_DEBUG, ClassFile.LineNumbersOption.PASS_LINE_NUMBERS, ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(clm.thisClass().asSymbol(), cb -> {
            for (var ce : clm) {
                if (ce instanceof MethodModel mem && !isGen) {
                    var methodRetGen = mem.methodTypeSymbol().returnType().descriptorString().equals(Gen.class.descriptorString());
                    if (!methodRetGen) {
                        cb.with(mem);
                    } else{
                        generatorMethod(cb, mem, clm);
                    }
                } else
                    cb.with(ce);
            }
        });
    }

    private void generatorMethod(ClassBuilder cb, MethodModel mem, ClassModel clm) {
        cb.withMethod(mem.methodName(), mem.methodType(), mem.flags().flagsMask(), mb -> {
            for (var me : mem) {
                if (me instanceof CodeModel com) {
                    var mts = mem.methodTypeSymbol();
                    mts = mts.changeReturnType(ConstantDescs.CD_void);
                    if (!mem.flags().has(AccessFlag.STATIC)) {
                        mts = mts.insertParameterTypes(0, clm.thisClass().asSymbol());
                    }
                    var name = "Gen_" + clm.thisClass().name().stringValue() + "_" + mem.methodName().stringValue() + "_" + customClazzDefMap.size();
                    var gb = new GeneratorBuilder(name, mts.parameterArray());
                    mb.withCode(gb::buildGeneratorMethodShim);
                    addGenerator(gb.CD_this_gen.displayName(), gb.buildGenerator(com));
                } else mb.with(me);
            }
        });
    }

    protected void addGenerator(String name, byte[] def){
        try {
            Files.write(Path.of("out/production/generators/" + name + ".class"), def);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        customClazzDefMap.put(name, def);
    }
}
