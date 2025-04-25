package generator.runtime;

import generator.Gen;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.*;

public class GeneratorBuilder {
    public final static String PARAM_PREFIX = "param_";
    public final static String LOCAL_PREFIX = "local_";
    public final static String STACK_PREFIX = "stack_";
    public final static String STATE_NAME = "state";

    public final static ClassDesc CD_Gen = ClassDesc.ofDescriptor(Gen.class.descriptorString());
    public final static ClassDesc CD_Res = ClassDesc.ofDescriptor(Gen.Res.class.descriptorString());
    public final static ClassDesc CD_Yield = ClassDesc.ofDescriptor(Gen.Yield.class.descriptorString());
    public final static ClassDesc CD_Ret = ClassDesc.ofDescriptor(Gen.Ret.class.descriptorString());
    public final static MethodTypeDesc MTD_Res = MethodTypeDesc.of(CD_Res);
    public final static MethodTypeDesc MTD_Gen_Obj = MethodTypeDesc.of(CD_Gen, ConstantDescs.CD_Object);
    public final static MethodTypeDesc MTD_Gen = MethodTypeDesc.of(CD_Gen);
    public static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    public final String name;
    public final ClassDesc CD_this_gen;
    public final ClassDesc[] params;
    public final MethodTypeDesc MTD_init;
    public final int paramSlotOff;

    public interface ParamConsumer{
        void consume(String param, int slot, ClassDesc type);
    }

    public GeneratorBuilder(String name, ClassDesc[] params){
        this.name = name;
        CD_this_gen = ClassDesc.of(name);
        this.params = params;
        MTD_init = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        paramSlotOff = Arrays.stream(params).mapToInt(p -> TypeKind.from(p).slotSize()).sum();
    }

    public void params(int slot_start, ParamConsumer consumer){
        int offset = 0;
        for (var param : params) {
            consumer.consume(PARAM_PREFIX+offset, offset+slot_start, param);
            offset += TypeKind.from(param).slotSize();
        }
    }

    public void buildGeneratorMethodShim(CodeBuilder cob){
        cob.new_(CD_this_gen).dup();
        params(0, (_, slot, type) -> {
            cob.loadLocal(TypeKind.from(type), slot);
        });
        cob.invokespecial(CD_this_gen, ConstantDescs.INIT_NAME, MTD_init).areturn();
    }

    public byte[] buildGenerator(CodeModel com){
        return ClassFile.of(ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED, ClassFile.DebugElementsOption.PASS_DEBUG, ClassFile.LineNumbersOption.PASS_LINE_NUMBERS, ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(CD_this_gen, clb -> {
            clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Gen)));
            // parameter fields
            params(0, (param, _, type) -> {
                clb.withField(param, type, ClassFile.ACC_PRIVATE);
            });
            // fms state
            clb.withField(STATE_NAME, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE);

            // constructor
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_init, ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
                cob.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                params(1, (param, slot, type) -> {
                    cob.aload(0).loadLocal(TypeKind.from(type), slot).putfield(CD_this_gen, param, type);
                });
                cob.return_();
            }));

            // fms method
            clb.withMethod("next", MethodTypeDesc.of(CD_Res), ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
                buildGeneratorNext(clb, cob, com);
            }));
        });
    }

    private void buildGeneratorNext(ClassBuilder clb, CodeBuilder cob, CodeModel com){
        cob.trying(
                tcob -> new StateMachineBuilder(this, clb, tcob, com).generateStateMachine(),
                // catch anything set our state to -1 and throw the exception
                ctb -> ctb.catchingAll(
                        blc -> blc.aload(0).loadConstant(-1).putfield(CD_this_gen, STATE_NAME, ConstantDescs.CD_int).athrow()
                )
        ).aconst_null().areturn();
    }
}
