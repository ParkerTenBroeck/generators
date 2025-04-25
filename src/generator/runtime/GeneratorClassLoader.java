package generator.runtime;

import generator.Fun;
import generator.Gen;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class GeneratorClassLoader extends ClassLoader {
    private final HashMap<String, byte[]> customClazzDefMap = new HashMap<>();
    private final HashMap<String, Class<?>> customClazzMap = new HashMap<>();

    public GeneratorClassLoader(ClassLoader parent) {
        super(parent);
    }

    void add(String name, byte[] def){
            try {
                Files.createDirectories(Path.of("out/modified/generators/" + name.replace(".", "/")).getParent());
                Files.write(Path.of("out/modified/generators/" + name.replace(".", "/") + ".class"), def);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        customClazzDefMap.put(name, def);
        customClazzMap.put(name, defineClass(name, def, 0, def.length));
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (customClazzMap.get(name) instanceof Class<?> clazz)
            return clazz;
        if (name.startsWith("java"))
            return super.loadClass(name);

        var p = "/" + name.replace('.', '/') + ".class";
        try (var stream = Fun.class.getResourceAsStream(p)) {
            var bytes = Objects.requireNonNull(stream).readAllBytes();
            add(name, searchForGenerators(bytes));
            return customClazzMap.get(name);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    public byte[] searchForGenerators(byte[] in) {
        var clm = ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).parse(in);
        var isGen = clm.thisClass().asSymbol().descriptorString().equals(Gen.class.descriptorString());


        var nestMem = new ArrayList<ClassDesc>();
        var innerCl = new ArrayList<InnerClassInfo>();
        clm.findAttributes(Attributes.nestMembers()).forEach(i -> nestMem.addAll(i.nestMembers().stream().map(ClassEntry::asSymbol).toList()));
        clm.findAttributes(Attributes.innerClasses()).forEach(i -> innerCl.addAll(i.classes()));

        return ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(clm.thisClass().asSymbol(), cb -> {
            for (var ce : clm) {
                if (ce instanceof MethodModel mem && !isGen) {
                    var methodRetGen = mem.methodTypeSymbol().returnType().descriptorString().equals(Gen.class.descriptorString());
                    if (!methodRetGen) {
                        cb.with(mem);
                    } else{
                        var gcd = generatorMethod(cb, mem, clm);
                        innerCl.add(InnerClassInfo.of(gcd, Optional.of(clm.thisClass().asSymbol()), Optional.of(gcd.displayName()), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC));
//                        nestMem.add(ClassDesc.of(gcd.displayName()));
                    }
                }
                else if (ce instanceof Attribute<?> e){
                    if (e.attributeMapper() != Attributes.nestMembers() && e.attributeMapper() != Attributes.innerClasses())
                        cb.with(ce);
                }
                else cb.with(ce);
            }
            if(!innerCl.isEmpty())
                cb.with(InnerClassesAttribute.of(innerCl));
            if(!nestMem.isEmpty())
                cb.with(NestMembersAttribute.ofSymbols(nestMem));
        });
    }

    private ClassDesc generatorMethod(ClassBuilder cb, MethodModel mem, ClassModel clm) {

        AtomicReference<ClassDesc> gcd = new AtomicReference<>();

        cb.withMethod(mem.methodName(), mem.methodType(), mem.flags().flagsMask(), mb -> {
            for (var me : mem) {
                if (me instanceof CodeModel com) {
                    var mts = mem.methodTypeSymbol();
                    mts = mts.changeReturnType(ConstantDescs.CD_void);
                    if (!mem.flags().has(AccessFlag.STATIC)) {
                        mts = mts.insertParameterTypes(0, clm.thisClass().asSymbol());
                    }
                    var name = clm.thisClass().name().stringValue()+"$Gen_" + mem.methodName().stringValue() + "_" + customClazzDefMap.size();

                    gcd.set(ClassDesc.of(clm.thisClass().asSymbol().packageName(), name));
                    var gb = new GeneratorBuilder(clm, gcd.get(), mts.parameterArray());

                    mb.withCode(gb::buildGeneratorMethodShim);
                    add(gb.CD_this.displayName(), gb.buildGenerator(com));
                } else mb.with(me);
            }
        });

        return gcd.get();
    }
}
