package generator.runtime;

import generator.gen.Gen;
import generator.runtime.gen.GenSMBuilder;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GeneratorClassLoader extends ClassLoader {
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
        customClazzMap.put(name, defineClass(name, def, 0, def.length));
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (customClazzMap.get(name) instanceof Class<?> clazz)
            return clazz;
        if (name.startsWith("java"))
            return super.loadClass(name);

        var p = "/" + name.replace('.', '/') + ".class";
        try (var stream = GeneratorClassLoader.class.getResourceAsStream(p)) {
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
//                        innerCl.add(InnerClassInfo.of(gcd, Optional.of(clm.thisClass().asSymbol()), Optional.of(gcd.displayName()), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC));
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

    private ClassDesc generatorMethod(ClassBuilder cb, MethodModel src_mem, ClassModel src_clm) {
        var com = src_mem.code().get();
        var gb = new GenSMBuilder(src_clm, src_mem, com);
        add(gb.CD_this.displayName(), gb.buildStateMachine());
        cb.withMethod(src_mem.methodName(), src_mem.methodType(), src_mem.flags().flagsMask(), mb -> {
            mb.withCode(gb::buildSourceMethodShim);
        });
        return gb.CD_this;
    }
}
