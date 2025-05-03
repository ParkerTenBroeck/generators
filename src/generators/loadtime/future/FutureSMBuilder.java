package generators.loadtime.future;

import future.Future;
import future.Waker;
import generators.loadtime.*;

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
    public final static MethodTypeDesc MTD_Future = MethodTypeDesc.of(CD_Future);
    public final static MethodTypeDesc MTD_Object_Waker = MethodTypeDesc.of(ConstantDescs.CD_Object, CD_Waker);
    public final static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    public final static String AWAITING_FIELD_NAME = "awaiting";

    static class AwaitHandler implements SpecialMethodHandler{
        final StateBuilder.State awaiting;
        final Label save_label;
        final Label resume_inline;

        public AwaitHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb) {
            awaiting = sb.create(cob);
            save_label = cob.newLabel();
            resume_inline = cob.newLabel();
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.labelBinding(save_label);
            var sst = new SavedStateTracker();
            frame.save_locals(smb, cob, sst,2);
            cob.storeLocal(TypeKind.REFERENCE, 2);
            frame.save_stack(smb, cob, sst,1);
            cob.loadLocal(TypeKind.REFERENCE, 2);

            smb.resumable_return(cob, awaiting, TypeKind.REFERENCE);

            sst.restore_all(smb, cob);
            cob.goto_(resume_inline);
        }

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            // [... Future]
            var start = cob.newBoundLabel();
            cob.dup().aload(1).invokeinterface(CD_Future, "poll", MTD_Object_Waker).dup().instanceOf(CD_Pending);
            // [... Future Polled is_pending]
            cob.ifThenElse(bcb -> {
                awaiting.setState(smb, cob);
                // [... Future Polled]
                bcb.swap().aload(0).swap().putfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                // [... Polled]
                cob.goto_(save_label).labelBinding(resume_inline);

                bcb.aload(0).getfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                bcb.aload(0).aconst_null().putfield(smb.CD_this, AWAITING_FIELD_NAME, CD_Future);
                bcb.goto_(start);
            }, bcb -> {
                bcb.swap().pop();
            });
            // [... Polled]
        }
    }

    static class YieldHandler implements SpecialMethodHandler {
        final StateBuilder.State resume;
        final Label save_ret;
        final Label end;

        public YieldHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb) {
            resume = sb.create(cob);
            save_ret = cob.newLabel();
            end = cob.newLabel();
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.labelBinding(save_ret);
            var saved = frame.save(smb, cob, 2, 0);

            resume.setState(smb, cob);
            cob.getstatic(CD_Pending, "INSTANCE", CD_Pending);

            smb.resumable_return(cob, resume, TypeKind.REFERENCE);

            saved.restore_all(smb, cob);
            cob.goto_(end);
        }

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.goto_(save_ret);
            cob.labelBinding(end);
        }
    }

    static class WakerHandler implements SpecialMethodHandler{

        protected WakerHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb) {}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {}

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(1);
        }

        @Override
        public ReplacementKind replacementKind() {
            return ReplacementKind.Immediate;
        }
    }

    static class RetHandler implements SpecialMethodHandler{
        protected RetHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb) {}

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {}

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound());
            smb.nonresumable_return(cob, TypeKind.REFERENCE);
        }
    }

    static class RetVoidHandler implements SpecialMethodHandler{
        protected RetVoidHandler(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb) {}

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
        }

        @Override
        public void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound()).aconst_null();
            smb.nonresumable_return(cob, TypeKind.REFERENCE);
        }
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
        super(src_clm, src_mem, src_com, "Fut");
        smmap.put(new SpecialMethod(CD_Future, "await", MTD_Obj), AwaitHandler::new);
        smmap.put(new SpecialMethod(CD_Future, "ret", MTD_Future_Obj), RetHandler::new);
        smmap.put(new SpecialMethod(CD_Future, "ret", MTD_Future), RetVoidHandler::new);
        smmap.put(new SpecialMethod(CD_Future, "yield", ConstantDescs.MTD_void), YieldHandler::new);
        smmap.put(new SpecialMethod(CD_Waker, "waker", MethodTypeDesc.of(CD_Waker)), WakerHandler::new);
    }
}
