package generators.loadtime;

import future.Future;
import gen.Gen;
import generators.loadtime.future.FutureSMBuilder;
import generators.loadtime.gen.GenSMBuilder;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
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
            add(name, searchReplaceMethods(bytes));
            return customClazzMap.get(name);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    public byte[] searchReplaceMethods(byte[] in) {
        var clm = ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).parse(in);
        var isGen = clm.thisClass().asSymbol().descriptorString().equals(Gen.class.descriptorString());
        var isFuture = clm.thisClass().asSymbol().descriptorString().equals(Future.class.descriptorString());


        var nestMem = new ArrayList<ClassDesc>();
        var innerCl = new ArrayList<InnerClassInfo>();
        clm.findAttributes(Attributes.nestMembers()).forEach(i -> nestMem.addAll(i.nestMembers().stream().map(ClassEntry::asSymbol).toList()));
        clm.findAttributes(Attributes.innerClasses()).forEach(i -> innerCl.addAll(i.classes()));

        return ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES, ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED).build(clm.thisClass().asSymbol(), cb -> {
            for (var ce : clm) {
                if (ce instanceof MethodModel mem && !isGen && !isFuture) {
                    StateMachineBuilder builder;
                    if(mem.methodTypeSymbol().returnType().descriptorString().equals(Gen.class.descriptorString())){
                        builder = new GenSMBuilder(clm, mem, mem.code().get());
                    }else if(mem.methodTypeSymbol().returnType().descriptorString().equals(Future.class.descriptorString())){
                        builder = new FutureSMBuilder(clm, mem, mem.code().get());
                    }else{
                        builder= null;
                    }
                    if(builder!=null&&builder.hasAnyHandlers()){
                        add(builder.CD_this.displayName(), builder.buildStateMachine());
                        cb.withMethod(mem.methodName(), mem.methodType(), mem.flags().flagsMask(), mb -> {
                            mb.withCode(builder::buildSourceMethodShim);
                        });
                    }else{
                        cb.with(mem);
                    }
                    if(builder != null && builder.shouldBeInnerClass()){
                        innerCl.add(InnerClassInfo.of(builder.CD_this, Optional.of(clm.thisClass().asSymbol()), Optional.of(builder.CD_this.displayName()), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC));
                        nestMem.add(ClassDesc.of(builder.CD_this.displayName()));
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
}
