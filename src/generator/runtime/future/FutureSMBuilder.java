package generator.runtime.future;

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

public class FutureSMBuilder extends StateMachineBuilder {

    public final static ClassDesc CD_Gen = ClassDesc.ofDescriptor(Gen.class.descriptorString());
    public final static ClassDesc CD_Res = ClassDesc.ofDescriptor(Gen.Res.class.descriptorString());
    public final static ClassDesc CD_Yield = ClassDesc.ofDescriptor(Gen.Yield.class.descriptorString());
    public final static ClassDesc CD_Ret = ClassDesc.ofDescriptor(Gen.Ret.class.descriptorString());
    public final static MethodTypeDesc MTD_Res = MethodTypeDesc.of(CD_Res);
    public final static MethodTypeDesc MTD_Gen_Obj = MethodTypeDesc.of(CD_Gen, ConstantDescs.CD_Object);
    public final static MethodTypeDesc MTD_Gen = MethodTypeDesc.of(CD_Gen);
    public final static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    static class AwaitHandler implements SpecialMethodHandler{
        final int yield_state;
        final Label yield_label;

        public AwaitHandler(StateMachineBuilder smb, CodeBuilder cob) {
            yield_state = smb.add_state(yield_label = cob.newLabel());
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {
            cob.aload(0).loadConstant(yield_state).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound());
            var start = cob.newBoundLabel();
            cob.dup().dup()
                    .invokeinterface(CD_Gen, "next", MethodTypeDesc.of(CD_Res)).dup()
                    .instanceOf(CD_Ret);
            cob.ifThenElse(bcb -> {
                bcb.checkcast(CD_Ret).invokevirtual(CD_Ret, "v", MethodTypeDesc.of(ConstantDescs.CD_Object)).swap().pop();
            }, bcb -> {
                smb.lt.savingLocals(smb.CD_this, bcb, () -> {
                    bcb.swap().loadLocal(TypeKind.from(smb.CD_this), 0).swap().putfield(smb.CD_this, "meow", CD_Gen);
                    bcb.areturn().labelBinding(yield_label);
                    bcb.loadLocal(TypeKind.from(smb.CD_this), 0).getfield(smb.CD_this, "meow", CD_Gen);
                });
                bcb.goto_(start);
            });

        }
    }

    @Override
    protected void buildStateMachineMethod(ClassBuilder clb){
        clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Gen)));
        clb.withMethod("next", MethodTypeDesc.of(CD_Res), ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            buildStateMachineMethodCode(clb, cob);
        }));
        clb.withField("meow", CD_Gen, ClassFile.ACC_PRIVATE);
    }

    public FutureSMBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com) {
        super(src_clm, src_mem, src_com);
        smmap.put(new SpecialMethod(CD_Gen, "await", MTD_Obj), AwaitHandler::new);
    }
}
