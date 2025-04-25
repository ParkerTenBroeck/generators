package generator.runtime;

import generator.gen.Gen;

import java.lang.classfile.*;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
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

    public final ClassDesc CD_this;
    public final ClassDesc[] params;
    public final MethodTypeDesc MTD_init;
    public final int paramSlotOff;
    public final ClassModel clm;

    public interface ParamConsumer{
        void consume(String param, int slot, ClassDesc type);
    }

    public GeneratorBuilder(ClassModel clm, ClassDesc cd, ClassDesc[] params){
        this.clm = clm;
        CD_this = cd;
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
        cob.new_(CD_this).dup();
        params(0, (_, slot, type) -> {
            cob.loadLocal(TypeKind.from(type), slot);
        });
        cob.invokespecial(CD_this, ConstantDescs.INIT_NAME, MTD_init).areturn();
    }

    public byte[] buildGenerator(CodeModel com){
        return ClassFile.of(ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED, ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(CD_this, clb -> {

            clm.findAttributes(Attributes.sourceFile()).forEach(clb::with);
            clb.with(InnerClassesAttribute.of(InnerClassInfo.of(CD_this, Optional.of(clm.thisClass().asSymbol()), Optional.of(CD_this.displayName().split("\\$")[1]), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC)));
            clb.with(NestHostAttribute.of(clm.thisClass()));

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
                    cob.aload(0).loadLocal(TypeKind.from(type), slot).putfield(CD_this, param, type);
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
                        blc -> blc.aload(0).loadConstant(-1).putfield(CD_this, STATE_NAME, ConstantDescs.CD_int).athrow()
                )
        ).aconst_null().areturn();
    }
}
