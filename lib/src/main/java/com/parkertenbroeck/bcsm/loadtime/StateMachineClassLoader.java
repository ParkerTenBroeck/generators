package com.parkertenbroeck.bcsm.loadtime;

import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.generator.Gen;
import com.parkertenbroeck.bcsm.loadtime.future.FutureSMBuilder;
import com.parkertenbroeck.bcsm.loadtime.gen.GenSMBuilder;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StateMachineClassLoader extends ClassLoader {
    private final HashMap<String, Class<?>> customClazzMap = new HashMap<>();
    private final List<String> skip;
    private final HashMap<ClassDesc, SMBB> builders;
    private final String write_classes_path;

    public interface SMBB{
        StateMachineBuilder<?> build(ClassModel src_clm, MethodModel src_mem, CodeModel src_com);
    }
    public static class Config{
        HashSet<String> skip = new HashSet<>();
        HashMap<ClassDesc, SMBB> builders = new HashMap<>();
        String write_classes;

        public static Config empty(){
            return new Config();
        }
        public static Config builtin(){
            return empty()
                    .skip("java", "jdk", "jre", "com.parkertenbroeck.bcsm.loadtime")
                    .with(Future.class, FutureSMBuilder::new)
                    .with(Gen.class, GenSMBuilder::new);
        }

        public Config skip(String... paths){
            skip.addAll(List.of(paths));
            return this;
        }

        public Config with(Class<?> ret, SMBB builder){
            builders.put(ClassDesc.ofDescriptor(ret.descriptorString()), builder);
            return this;
        }

        public Config with(ClassDesc ret, SMBB builder){
            builders.put(ret, builder);
            return this;
        }

        public Config write_classes(String path){
            write_classes = path;
            return this;
        }
    }

    public StateMachineClassLoader(ClassLoader parent) {
        this(parent, Config.builtin());
    }

    public StateMachineClassLoader(ClassLoader parent, Config config) {
        super(parent);
        skip = config.skip.stream().toList();
        builders = new HashMap<>(config.builders);
        write_classes_path = config.write_classes;
    }

    void add(String name, byte[] def){
        if(write_classes_path!=null){
            try {
                Files.createDirectories(Path.of(write_classes_path + name.replace(".", "/")).getParent());
                Files.write(Path.of(write_classes_path + name.replace(".", "/") + ".class"), def);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        customClazzMap.put(name, defineClass(name, def, 0, def.length));
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (customClazzMap.get(name) instanceof Class<?> clazz)
            return clazz;
        for(var item : skip)
            if(name.startsWith(item))
                return super.loadClass(name);


        var p = "/" + name.replace('.', '/') + ".class";
        try (var stream = StateMachineClassLoader.class.getResourceAsStream(p)) {
            var bytes = Objects.requireNonNull(stream).readAllBytes();
            add(name, searchReplaceableMethods(bytes));
            return customClazzMap.get(name);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    public byte[] searchReplaceableMethods(byte[] in) {
        var clm = ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).parse(in);


        var nestMem = new ArrayList<ClassDesc>();
        var innerCl = new ArrayList<InnerClassInfo>();
        clm.findAttributes(Attributes.nestMembers()).forEach(i -> nestMem.addAll(i.nestMembers().stream().map(ClassEntry::asSymbol).toList()));
        clm.findAttributes(Attributes.innerClasses()).forEach(i -> innerCl.addAll(i.classes()));

        return ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES, ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED).build(clm.thisClass().asSymbol(), cb -> {
            for (var ce : clm) {
                if (ce instanceof MethodModel mem && mem.code().isPresent()) {
                    StateMachineBuilder<?> builder = builders
                            .getOrDefault(
                                    mem.methodTypeSymbol().returnType(),
                                    (_, _, _) -> null
                            ).build(clm, mem, mem.code().get());

                    if(builder!=null&&builder.hasAnyHandlers()){
                        add(builder.CD_this.packageName() + "." + builder.CD_this.displayName(), builder.buildStateMachine());
                        cb.withMethod(mem.methodName(), mem.methodType(), mem.flags().flagsMask()&~ClassFile.ACC_SYNCHRONIZED, mb -> {
                            mb.withCode(builder::buildSourceMethodShim);
                        });
                        if(builder.shouldBeInnerClass()){
                            innerCl.add(InnerClassInfo.of(builder.CD_this, Optional.empty(), Optional.empty()));
                            nestMem.add(builder.CD_this);
                        }
                    }else{
                        cb.with(mem);
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
