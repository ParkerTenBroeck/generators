package generators.loadtime.gen;

import gen.Gen;
import generators.loadtime.*;

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
        final StateBuilder.State yielded;
        final Label save;
        final Label resume;
        final boolean is_void;

        public YieldHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb, boolean is_void) {
            yielded = sb.create(cob);
            save = cob.newLabel();
            resume = cob.newLabel();
            this.is_void = is_void;
        }

        public ReplacementKind replacementKind(){return ReplacementKind.ImmediateReplacingPop;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {


            cob.labelBinding(save);
            var sst = new SavedStateTracker();
            frame.save_locals(smb, cob, sst,1);
            cob.storeLocal(TypeKind.REFERENCE, 1);
            frame.save_stack(smb, cob, sst,1);
            cob.loadLocal(TypeKind.REFERENCE, 1);

            smb.resumable_return(cob, yielded, TypeKind.REFERENCE);

            sst.restore_all(smb, cob);
            cob.goto_(resume);
        }

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            if(is_void)cob.aconst_null();

            yielded.setState(smb, cob);

            cob.new_(CD_Yield)
                .dup_x1()
                .swap()
                .invokespecial(CD_Yield, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                .goto_(save);

            cob.labelBinding(resume);
        }
    }

    static class RetHandler implements SpecialMethodHandler {
        final boolean is_void;

        public RetHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb, boolean is_void) {
            this.is_void = is_void;
        }

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {}

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            if(is_void)cob.aconst_null();

            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound())
                    .new_(CD_Ret)
                    .dup_x1()
                    .swap()
                    .invokespecial(CD_Ret, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object));
            smb.nonresumable_return(cob, TypeKind.REFERENCE);
        }
    }

    public GenSMBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com) {
        super(src_clm, src_mem, src_com, "Gen");
        smmap.put(new SpecialMethod(CD_Gen, "yield", MTD_Gen_Obj),(smb, cob, sb) -> new YieldHandler(smb, cob, sb, false));
        smmap.put(new SpecialMethod(CD_Gen, "yield", MTD_Gen),(smb, cob, sb) -> new YieldHandler(smb, cob, sb,true));
        smmap.put(new SpecialMethod(CD_Gen, "ret", MTD_Gen_Obj),(smb, cob, sb) -> new RetHandler(smb, cob, sb,false));
        smmap.put(new SpecialMethod(CD_Gen, "ret", MTD_Gen),(smb, cob, sb) -> new RetHandler(smb, cob, sb, true));
    }

    @Override
    protected void buildStateMachineMethod(ClassBuilder clb){
        clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Gen)));
        clb.withMethod("next", MethodTypeDesc.of(CD_Res), ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            buildStateMachineMethodCode(clb, cob, 1);
        }));
    }
}
