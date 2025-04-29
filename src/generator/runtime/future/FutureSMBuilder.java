package generator.runtime.future;

import generator.future.Future;
import generator.future.Waker;
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

    public final static ClassDesc CD_Future = ClassDesc.ofDescriptor(Future.class.descriptorString());
    public final static ClassDesc CD_Waker = ClassDesc.ofDescriptor(Waker.class.descriptorString());
    public final static ClassDesc CD_Pending = ClassDesc.ofDescriptor(Future.Pending.class.descriptorString());

    public final static MethodTypeDesc MTD_Future_Obj = MethodTypeDesc.of(CD_Future, ConstantDescs.CD_Object);
    public final static MethodTypeDesc MTD_Object_Waker = MethodTypeDesc.of(ConstantDescs.CD_Object, CD_Waker);
    public final static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    public final static String AWAITING_FIELD_NAME = "awaiting";

    static class AwaitHandler implements SpecialMethodHandler{
        final int yield_state;
        final Label yield_label;

        public AwaitHandler(StateMachineBuilder smb, CodeBuilder cob) {
            yield_state = smb.add_state(yield_label = cob.newLabel());
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {
            System.out.println("Await");
            cob.aload(0).loadConstant(yield_state).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound());
            var start = cob.newBoundLabel();
            cob.dup()
                    .aload(1).invokeinterface(CD_Future, "poll", MTD_Object_Waker).dup()
                    .instanceOf(CD_Pending);
            cob.ifThenElse(bcb -> {
                bcb.swap().aload(0).swap().putfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                smb.lt.savingLocals(smb.CD_this, bcb, () -> {
                    bcb.areturn().labelBinding(yield_label);
                });
                bcb.aload(0).getfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                bcb.aload(0).aconst_null().putfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                bcb.goto_(start);
            }, bcb -> {
                bcb.swap().pop();
            });

        }
    }

    static class YieldHandler implements SpecialMethodHandler {
        final int resume_state;
        final Label resume_label;

        public YieldHandler(StateMachineBuilder smb, CodeBuilder cob) {
            resume_state = smb.add_state(resume_label = cob.newLabel());
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {

            smb.lt.savingLocals(smb.CD_this, cob, () -> {
                cob.aload(0).loadConstant(resume_state).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound())
                        .getstatic(CD_Pending, "INSTANCE", CD_Pending)
                        .areturn();
                cob.labelBinding(resume_label);
            });
        }
    }

    static class RetHandler implements SpecialMethodHandler{

        public RetHandler() {}

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void handle(StateMachineBuilder smb, CodeBuilder cob) {
            System.out.println("Return");
            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound()).areturn();
        }
    }

    @Override
    protected String uniqueName() {
        return "Fut"+super.uniqueName();
    }

    @Override
    protected void buildStateMachineMethod(ClassBuilder clb){
        clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Future)));
        clb.withMethod("poll", MTD_Object_Waker, ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            cob.localVariable(1, "waker", CD_Waker, cob.startLabel(), cob.endLabel());
            buildStateMachineMethodCode(clb, cob, 2);
        }));
        clb.withMethod("cancel", MethodTypeDesc.of(ConstantDescs.CD_void),  ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            cob.aload(0).getfield(CD_this, AWAITING_FIELD_NAME, CD_Future).dup().ifThen(Opcode.IFNONNULL, boc -> {
                boc.invokeinterface(CD_Future, "cancel", MethodTypeDesc.of(ConstantDescs.CD_void))
                        .aload(0).aconst_null().putfield(CD_this, AWAITING_FIELD_NAME, CD_Future).return_();
            });
            cob.pop().return_();
        }));
        clb.withField(AWAITING_FIELD_NAME, CD_Future, ClassFile.ACC_PRIVATE);
    }

    public FutureSMBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com) {
        super(src_clm, src_mem, src_com);
        smmap.put(new SpecialMethod(CD_Future, "await", MTD_Obj), AwaitHandler::new);
        smmap.put(new SpecialMethod(CD_Future, "ret", MTD_Future_Obj), (_, _) -> new RetHandler());
        smmap.put(new SpecialMethod(CD_Future, "yield", ConstantDescs.MTD_void), YieldHandler::new);
    }
}
