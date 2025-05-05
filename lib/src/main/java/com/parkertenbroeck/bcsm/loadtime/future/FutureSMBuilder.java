package com.parkertenbroeck.bcsm.loadtime.future;

import com.parkertenbroeck.future.Future;
import com.parkertenbroeck.future.Waker;
import com.parkertenbroeck.bcsm.loadtime.*;

import java.lang.classfile.*;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.lang.reflect.Method;
import java.util.*;
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

    private String annotationStringValue(AnnotationValue value){
        if (value instanceof AnnotationValue.OfString ofString) {
            return ofString.stringValue();
        }
        throw new RuntimeException();
    }

    private ClassDesc annotationClassValue(AnnotationValue value){
        if (value instanceof AnnotationValue.OfClass ofConstant) {
            return ofConstant.classSymbol();
        }
        throw new RuntimeException();
    }

    private void yielding_state(StateBuilder.State state, Frame frame, SavedStateTracker sst){
        if(frame.local_annotations().length==0)return;

        ArrayList<Consumer<CodeBuilder>> field_cancellation_behavior = new ArrayList<>();
        for(var ann : frame.local_annotations()){
            if(ann.annotation().classSymbol().descriptorString().equals(Cancellation.class.descriptorString())){
                var param = sst.load_param(ann.slot()-paramSlotOff+2);
                ClassDesc owner = frame.locals()[ann.slot()].sym();
                String name = "cancel";
                for(var el : ann.annotation().elements()){
                    switch(el.name().stringValue()){
                        case "value" -> name = annotationStringValue(el.value());
                        case "owner" -> owner = annotationClassValue(el.value());
                    }
                }

                if(!owner.isClassOrInterface()){
                    throw new RuntimeException("Owner " + owner + " is not a class/interface cannot be used here");
                }
                boolean is_interface;
                TypeKind ret;
                try{
                    Class<?> clazz = getClass().getClassLoader().loadClass(owner.descriptorString().replace("/", ".").replace(";", "").substring(1));

                    var method = findMethod(clazz, name);
                    if(method==null){
                        throw new RuntimeException("Cannot find method '"+name+"' for class " + clazz);
                    }
                    clazz = method.getDeclaringClass();
                    owner = ClassDesc.ofDescriptor(clazz.descriptorString());
                    is_interface = clazz.isInterface();
                    ret = TypeKind.from(ClassDesc.ofDescriptor(method.getReturnType().descriptorString()));
                }catch (Exception e){
                    throw new RuntimeException(e);
                }

                String final_name = name;
                ClassDesc final_owner = owner;

                field_cancellation_behavior.add(cob -> {
                    cob.trying(tcob -> {
                        tcob.aload(0).getfield(CD_this, param.name(), param.desc());
                        if(is_interface){
                            tcob.invokeinterface(final_owner, final_name, MethodTypeDesc.of(ConstantDescs.CD_void));
                        }else{
                            tcob.invokevirtual(final_owner, final_name, MethodTypeDesc.of(ConstantDescs.CD_void));
                        }
                        if(ret.slotSize()==1)tcob.pop();
                        if(ret.slotSize()==2)tcob.pop2();
                    }, cb -> cb.catchingAll(ccob -> {
                        ccob.aload(2).ifThenElse(Opcode.IFNONNULL, bcob -> {
                            bcob.aload(2).swap();
                            addSuppressed(bcob);
                        }, bcob -> {
                            bcob.astore(2);
                        });
                    }));
                });
            }
        }
        cancellation_behavior.put(state.id(), cob -> {
            for(var fcb : field_cancellation_behavior) fcb.accept(cob);
        });
    }

    private static Method findMethod(Class<?> owner, String name){
        try{
            return owner.getDeclaredMethod(name);
        }catch (Exception ignore){}

        if(owner.equals(Object.class))return null;

        for(var iface : owner.getInterfaces()){
            var method = findMethod(iface, name);
            if(method!=null)return method;
        }

        if(!owner.isInterface()){
            Class<?> sup = owner.getSuperclass();
            if(sup!=null){
                return findMethod(sup, name);
            }
        }

        return findMethod(Object.class, name);
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
            cob.aload(0).getfield(CD_this, STATE_NAME, ConstantDescs.CD_int).istore(1);// state
            cob.aconst_null().astore(2);// exception
            cob.aload(0).loadConstant(-1).putfield(CD_this, STATE_NAME, ConstantDescs.CD_int);

            cob.aload(0).getfield(CD_this, AWAITING_FIELD_NAME, CD_Future).ifThen(Opcode.IFNONNULL, boc -> {
                boc.trying(tcob -> {
                    tcob.aload(0).getfield(CD_this, AWAITING_FIELD_NAME, CD_Future)
                            .aload(0).aconst_null().putfield(CD_this, AWAITING_FIELD_NAME, CD_Future)
                            .invokeinterface(CD_Future, "cancel", MethodTypeDesc.of(ConstantDescs.CD_void));

                }, cb -> {
                    cb.catchingAll(ccob -> {
                        ccob.aload(2).ifThenElse(Opcode.IFNONNULL, bcob -> {
                            bcob.aload(2).swap();
                            addSuppressed(bcob);
                        }, bcob -> {
                            bcob.astore(2);
                        });
                    });
                });
            });

            if(!cancellation_behavior.isEmpty()){
                var states = cancellation_behavior.entrySet().stream().toList();
                var cases = states.stream().map(v -> SwitchCase.of(v.getKey(), cob.newLabel())).toList();
                cob.iload(1);
                var end = cob.newLabel();
                cob.tableswitch(end, cases);
                for(int i = 0; i < states.size(); i ++){
                    cob.labelBinding(cases.get(i).target());
                    states.get(i).getValue().accept(cob);
                    cob.goto_(end);
                }
                cob.labelBinding(end);
            }


            this.synchronized_exit(cob);
            cob.aload(2).ifThen(Opcode.IFNONNULL, bcob -> {
               bcob.aload(2).athrow();
            });
            cob.return_();
        }));
        clb.withField(AWAITING_FIELD_NAME, CD_Future, ClassFile.ACC_PRIVATE);
    }

    private void addSuppressed(CodeBuilder cob) {
        cob.invokevirtual(ClassDesc.ofDescriptor(Throwable.class.descriptorString()), "addSuppressed", MethodTypeDesc.of(ConstantDescs.CD_void, ClassDesc.ofDescriptor(Throwable.class.descriptorString())));
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
