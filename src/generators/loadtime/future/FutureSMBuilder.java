package generators.loadtime.future;

import future.Future;
import future.Waker;
import generators.loadtime.*;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class FutureSMBuilder extends StateMachineBuilder<FutureSMBuilder> {

    public final static ClassDesc CD_Future = ClassDesc.ofDescriptor(Future.class.descriptorString());
    public final static ClassDesc CD_Waker = ClassDesc.ofDescriptor(Waker.class.descriptorString());
    public final static ClassDesc CD_Pending = ClassDesc.ofDescriptor(Future.Pending.class.descriptorString());

    public final static MethodTypeDesc MTD_Future_Obj = MethodTypeDesc.of(CD_Future, ConstantDescs.CD_Object);
    public final static MethodTypeDesc MTD_Future = MethodTypeDesc.of(CD_Future);
    public final static MethodTypeDesc MTD_Object_Waker = MethodTypeDesc.of(ConstantDescs.CD_Object, CD_Waker);
    public final static MethodTypeDesc MTD_Obj = MethodTypeDesc.of(ConstantDescs.CD_Object);

    public final static String AWAITING_FIELD_NAME = "awaiting";

    private final HashMap<Integer, Consumer<CodeBuilder>> cancellation_behavior = new HashMap<>();

    static class AwaitHandler implements SpecialMethodHandler<FutureSMBuilder>{
        final StateBuilder.State awaiting;
        final Label save_label;
        final Label resume_inline;

        public AwaitHandler(FutureSMBuilder smb, CodeBuilder cob, Frame frame, StateBuilder sb) {
            awaiting = sb.create(cob);
            save_label = cob.newLabel();
            resume_inline = cob.newLabel();
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void build_prelude(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.labelBinding(save_label);
            var sst = new SavedStateTracker();
            frame.save_locals(smb, cob, sst,2);
            cob.storeLocal(TypeKind.REFERENCE, 2);
            frame.save_stack(smb, cob, sst,1);
            cob.loadLocal(TypeKind.REFERENCE, 2);


            smb.yielding_state(awaiting, frame, sst);
            smb.resumable_return(cob, awaiting, TypeKind.REFERENCE);

            sst.restore_all(smb, cob);
            cob.goto_(resume_inline);
        }

        @Override
        public void build_inline(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
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

    static class YieldHandler implements SpecialMethodHandler<FutureSMBuilder> {
        final StateBuilder.State resume;
        final Label save_ret;
        final Label end;

        public YieldHandler(FutureSMBuilder smb, CodeBuilder cob, Frame frame, StateBuilder sb) {
            resume = sb.create(cob);
            save_ret = cob.newLabel();
            end = cob.newLabel();
        }

        public ReplacementKind replacementKind(){return ReplacementKind.Immediate;}

        @Override
        public void build_prelude(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.labelBinding(save_ret);
            var sst = frame.save(smb, cob, 2, 0);

            resume.setState(smb, cob);
            cob.getstatic(CD_Pending, "INSTANCE", CD_Pending);

            smb.yielding_state(resume, frame, sst);
            smb.resumable_return(cob, resume, TypeKind.REFERENCE);

            sst.restore_all(smb, cob);
            cob.goto_(end);
        }

        @Override
        public void build_inline(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.goto_(save_ret);
            cob.labelBinding(end);
        }
    }

    static class WakerHandler implements SpecialMethodHandler<FutureSMBuilder>{

        protected WakerHandler(FutureSMBuilder smb, CodeBuilder cob, Frame frame, StateBuilder sb) {}

        @Override
        public void build_prelude(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {}

        @Override
        public void build_inline(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(1);
        }

        @Override
        public ReplacementKind replacementKind() {
            return ReplacementKind.Immediate;
        }
    }

    static class RetHandler implements SpecialMethodHandler<FutureSMBuilder>{
        protected RetHandler(FutureSMBuilder smb, CodeBuilder cob, Frame frame, StateBuilder sb) {}

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void build_prelude(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {}

        @Override
        public void build_inline(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound());
            smb.nonresumable_return(cob, TypeKind.REFERENCE);
        }
    }

    static class RetVoidHandler implements SpecialMethodHandler<FutureSMBuilder>{
        protected RetVoidHandler(FutureSMBuilder smb, CodeBuilder cob, Frame frame, StateBuilder sb) {}

        public ReplacementKind replacementKind(){return ReplacementKind.ReplacingNextReturn;}

        @Override
        public void build_prelude(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
        }

        @Override
        public void build_inline(FutureSMBuilder smb, CodeBuilder cob, Frame frame) {
            cob.aload(0).loadConstant(-1).putfield(smb.CD_this, STATE_NAME, TypeKind.INT.upperBound()).aconst_null();
            smb.nonresumable_return(cob, TypeKind.REFERENCE);
        }
    }

    private String fromAnnValue(AnnotationValue value){
        switch(value){
            case AnnotationValue.OfAnnotation ofAnnotation -> {
            }
            case AnnotationValue.OfArray ofArray -> {
            }
            case AnnotationValue.OfClass ofClass -> {
            }
            case AnnotationValue.OfConstant ofConstant -> {
                switch(ofConstant){
                    case AnnotationValue.OfBoolean ofBoolean -> {
                    }
                    case AnnotationValue.OfByte ofByte -> {
                    }
                    case AnnotationValue.OfChar ofChar -> {
                    }
                    case AnnotationValue.OfDouble ofDouble -> {
                    }
                    case AnnotationValue.OfFloat ofFloat -> {
                    }
                    case AnnotationValue.OfInt ofInt -> {
                    }
                    case AnnotationValue.OfLong ofLong -> {
                    }
                    case AnnotationValue.OfShort ofShort -> {
                    }
                    case AnnotationValue.OfString ofString -> {
                        return ofString.stringValue();
                    }
                }
            }
            case AnnotationValue.OfEnum ofEnum -> {
            }
        }
        throw new RuntimeException();
    }

    private void yielding_state(StateBuilder.State state, Frame frame, SavedStateTracker sst){
        if(frame.local_annotations().length==0)return;

        ArrayList<Consumer<CodeBuilder>> stuff = new ArrayList<>();
        for(var ann : frame.local_annotations()){
            if(ann.annotation().classSymbol().descriptorString().equals(Cancellation.class.descriptorString())){
                var param = sst.load_param(ann.slot()-paramSlotOff+2);
                ClassDesc owner = frame.locals()[ann.slot()].sym();
                String name = "cancel";
                for(var el : ann.annotation().elements()){
                    switch(el.name().stringValue()){
                        case "value" -> name = fromAnnValue(el.value());
                        case "owner" -> {}
                        case "ret" -> {}
                    }
                    el.name().equalsString("value");
                }
                String final_name = name;
                stuff.add(cob -> {
                    cob.trying(tcob -> {
                        tcob.aload(0).getfield(CD_this, param.name(), param.desc());
                        try{
                            if(FutureSMBuilder.class.getClassLoader().loadClass(owner.displayName()).isInterface()){
                                tcob.invokeinterface(owner, final_name, MethodTypeDesc.of(ConstantDescs.CD_void));
                            }else{
                                tcob.invokevirtual(owner, final_name, MethodTypeDesc.of(ConstantDescs.CD_void));
                            }
                        }catch (Exception e){
                            throw new RuntimeException(e);
                        }
                    }, cb -> cb.catchingAll(ccob -> ccob.pop()));
                });
            }
        }
        cancellation_behavior.put(state.id(), cob -> {
            for(var thing : stuff) thing.accept(cob);
        });
    }

    @Override
    protected void buildStateMachineMethod(ClassBuilder clb){
        clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Future)));
        clb.withMethod("poll", MTD_Object_Waker, ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
            cob.localVariable(1, "waker", CD_Waker, cob.startLabel(), cob.endLabel());
            buildStateMachineMethodCode(clb, cob, 2);
        }));
        clb.withMethod("cancel", MethodTypeDesc.of(ConstantDescs.CD_void),  ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {

            this.synchronized_start(cob);
            cob.trying(tcob -> {
                tcob.aload(0).getfield(CD_this, STATE_NAME, ConstantDescs.CD_int).istore(1);
                tcob.aload(0).loadConstant(-1).putfield(CD_this, STATE_NAME, ConstantDescs.CD_int);

                tcob.aload(0).getfield(CD_this, AWAITING_FIELD_NAME, CD_Future).ifThen(Opcode.IFNONNULL, boc -> {
                    boc.aload(0).getfield(CD_this, AWAITING_FIELD_NAME, CD_Future)
                            .invokeinterface(CD_Future, "cancel", MethodTypeDesc.of(ConstantDescs.CD_void))
                            .aload(0).aconst_null().putfield(CD_this, AWAITING_FIELD_NAME, CD_Future);
                });

                if(!cancellation_behavior.isEmpty()){
                    var states = cancellation_behavior.entrySet().stream().toList();
                    var cases = states.stream().map(v -> SwitchCase.of(v.getKey(), tcob.newLabel())).toList();
                    tcob.iload(1);
                    var end = tcob.newLabel();
                    tcob.tableswitch(end, cases);
                    for(int i = 0; i < states.size(); i ++){
                        tcob.labelBinding(cases.get(i).target());
                        states.get(i).getValue().accept(tcob);
                        tcob.goto_(end);
                    }
                    tcob.labelBinding(end);
                }


                this.synchronized_exit(tcob);
                tcob.return_();
            }, cb -> {
                cb.catchingAll(ccob -> {
                    ccob.pop();
                    this.synchronized_exit(ccob);
                    ccob.return_();
                });
            });

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
