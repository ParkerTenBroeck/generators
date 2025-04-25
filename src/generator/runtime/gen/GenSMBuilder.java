package generator.runtime.gen;

import generator.gen.Gen;
import generator.runtime.ReplacementKind;
import generator.runtime.SpecialMethod;
import generator.runtime.SpecialMethodHandler;
import generator.runtime.StateMachineBuilder;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

public class GenSMBuilder extends StateMachineBuilder {

    public final static ClassDesc CD_Gen = ClassDesc.ofDescriptor(Gen.class.descriptorString());
    public final static ClassDesc CD_Res = ClassDesc.ofDescriptor(Gen.Res.class.descriptorString());
    public final static ClassDesc CD_Yield = ClassDesc.ofDescriptor(Gen.Yield.class.descriptorString());
    public final static ClassDesc CD_Ret = ClassDesc.ofDescriptor(Gen.Ret.class.descriptorString());
    public final static MethodTypeDesc MTD_Res = MethodTypeDesc.of(CD_Res);
    public final static MethodTypeDesc MTD_Gen_Obj = MethodTypeDesc.of(CD_Gen, ConstantDescs.CD_Object);
    public final static MethodTypeDesc MTD_Gen = MethodTypeDesc.of(CD_Gen);
    public final static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    static class YieldHandler implements SpecialMethodHandler {
        final int resume_state;
        final Label resume_label;
        final boolean is_void;

        public YieldHandler(StateMachineBuilder smb, CodeBuilder cob, boolean is_void) {
            resume_state = smb.add_state(resume_label = cob.newLabel());
            this.is_void = is_void;
        }

        public ReplacementKind replacementKind(){return ReplacementKind.ImmediateReplacingPop;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {
            if(is_void)cob.aconst_null();

            smb.lt.savingLocals(smb.CD_this, cob, () -> {
                cob.aload(0).loadConstant(resume_state).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound())
                        .new_(CD_Yield)
                        .dup_x1()
                        .swap()
                        .invokespecial(CD_Yield, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                        .areturn();
                cob.labelBinding(resume_label);
            });
        }
    }

    static class RetHandler implements SpecialMethodHandler {
        final boolean is_void;

        public RetHandler(boolean is_void) {
            this.is_void = is_void;
        }

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {
            if(is_void)cob.aconst_null();

            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound())
                    .new_(CD_Ret)
                    .dup_x1()
                    .swap()
                    .invokespecial(CD_Ret, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                    .areturn();
        }
    }

    public GenSMBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com) {
        super(src_clm, src_mem, src_com);
        smmap.put(new SpecialMethod(CD_Gen, "yield", MTD_Gen_Obj),(smb, cob) -> new YieldHandler(smb, cob, false));
        smmap.put(new SpecialMethod(CD_Gen, "yield", MTD_Gen),(smb, cob) -> new YieldHandler(smb, cob, true));
        smmap.put(new SpecialMethod(CD_Gen, "ret", MTD_Gen_Obj),(_, _) -> new RetHandler(false));
        smmap.put(new SpecialMethod(CD_Gen, "ret", MTD_Gen),(_, _) -> new RetHandler(true));
    }

    @Override
    protected void buildStateMachineMethod(ClassBuilder clb){
        clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Gen)));
        clb.withMethod("next", MethodTypeDesc.of(CD_Res), ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            buildStateMachineMethodCode(clb, cob);
        }));
    }
}
