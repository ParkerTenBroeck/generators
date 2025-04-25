package generator.runtime;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

public class StateMachineBuilder {
    final ClassBuilder clb;
    final CodeBuilder cob;
    final CodeModel com;
    final GeneratorBuilder gb;
    final LocalTracker lt;

    private HashMap<SpecialMethod, Function<StateMachineBuilder, SpecialMethodHandler>> smmap = new HashMap<>();
    private boolean ignore_next_pop = false;
    final ArrayList<SwitchCase> stateSwitchCases = new ArrayList<>();
    final Label invalidState;


    public enum HandlerRan{
        ImmediateRemovePop,
        Immediate,
        ReplacingNextReturn,
    }
    public interface SpecialMethodHandler {
        void handle(StateMachineBuilder smb);
        default boolean removeCall(){return true;}
        default HandlerRan handlerRan(){return HandlerRan.ImmediateRemovePop;}
    }

    public record SpecialMethod(ClassDesc owner, String name, MethodTypeDesc desc) {
    }

    static class YieldHandler implements SpecialMethodHandler {
        final int resume_state;
        final Label resume_label;
        final boolean is_void;

        public YieldHandler(StateMachineBuilder smb, boolean is_void) {
            resume_state = smb.add_state(resume_label = smb.cob.newLabel());
            this.is_void = is_void;
        }

        @Override
        public void handle(StateMachineBuilder smb) {
            if(is_void)smb.cob.aconst_null();

            smb.lt.savingLocals(smb.gb.CD_this, smb.cob, () -> {
                smb.cob.aload(0).loadConstant(resume_state).putfield(smb.gb.CD_this, GeneratorBuilder.STATE_NAME, TypeKind.INT.upperBound())
                        .new_(GeneratorBuilder.CD_Yield)
                        .dup_x1()
                        .swap()
                        .invokespecial(GeneratorBuilder.CD_Yield, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                        .areturn();
                smb.cob.labelBinding(resume_label);
            });
            smb.ignore_next_pop = true;
        }
    }

    static class RetHandler implements SpecialMethodHandler {
        final boolean is_void;

        public RetHandler(boolean is_void) {
            this.is_void = is_void;
        }

        public HandlerRan handlerRan(){return HandlerRan.ReplacingNextReturn;}

        @Override
        public void handle(StateMachineBuilder smb) {
            if(is_void)smb.cob.aconst_null();

            smb.cob.aload(0).loadConstant(-1).putfield(smb.gb.CD_this, GeneratorBuilder.STATE_NAME, TypeKind.INT.upperBound())
                    .new_(GeneratorBuilder.CD_Ret)
                    .dup_x1()
                    .swap()
                    .invokespecial(GeneratorBuilder.CD_Ret, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                    .areturn();
        }
    }

    static class AwaitHandler implements SpecialMethodHandler{
        final int yield_state;
        final Label yield_label;

        public AwaitHandler(StateMachineBuilder smb) {
            yield_state = smb.add_state(yield_label = smb.cob.newLabel());
        }

        public HandlerRan handlerRan(){return HandlerRan.Immediate;}

        @Override
        public void handle(StateMachineBuilder smb) {
            smb.cob.aload(0).loadConstant(yield_state).putfield(smb.gb.CD_this, GeneratorBuilder.STATE_NAME, TypeKind.INT.upperBound());
            var start = smb.cob.newBoundLabel();
            smb.cob.dup().dup()
                    .invokeinterface(GeneratorBuilder.CD_Gen, "next", MethodTypeDesc.of(GeneratorBuilder.CD_Res)).dup()
                    .instanceOf(GeneratorBuilder.CD_Ret);
            smb.cob.ifThenElse(bcb -> {
                bcb.checkcast(GeneratorBuilder.CD_Ret).invokevirtual(GeneratorBuilder.CD_Ret, "v", MethodTypeDesc.of(ConstantDescs.CD_Object)).swap().pop();
            }, bcb -> {
                smb.lt.savingLocals(smb.gb.CD_this, bcb, () -> {
                    bcb.swap().loadLocal(TypeKind.from(smb.gb.CD_this), 0).swap().putfield(smb.gb.CD_this, "meow", GeneratorBuilder.CD_Gen);
                    bcb.areturn().labelBinding(yield_label);
                    bcb.loadLocal(TypeKind.from(smb.gb.CD_this), 0).getfield(smb.gb.CD_this, "meow", GeneratorBuilder.CD_Gen);
                });
                bcb.goto_(start);
            });

        }
    }

    StateMachineBuilder(GeneratorBuilder gb, ClassBuilder clb, CodeBuilder cob, CodeModel com) {
        this.gb = gb;
        this.clb = clb;
        this.cob = cob;
        this.com = com;
        this.lt = new LocalTracker(this, com);
        invalidState = cob.newLabel();

        smmap.put(new SpecialMethod(GeneratorBuilder.CD_Gen, "yield", GeneratorBuilder.MTD_Gen_Obj),smb -> new YieldHandler(smb, false));
        smmap.put(new SpecialMethod(GeneratorBuilder.CD_Gen, "yield", GeneratorBuilder.MTD_Gen),smb -> new YieldHandler(smb, true));
        smmap.put(new SpecialMethod(GeneratorBuilder.CD_Gen, "ret", GeneratorBuilder.MTD_Gen_Obj),_ -> new RetHandler(false));
        smmap.put(new SpecialMethod(GeneratorBuilder.CD_Gen, "ret", GeneratorBuilder.MTD_Gen),_ -> new RetHandler(true));
        smmap.put(new SpecialMethod(GeneratorBuilder.CD_Gen, "await", GeneratorBuilder.MTD_Obj), AwaitHandler::new);
    }

    int add_state(Label label) {
        stateSwitchCases.add(SwitchCase.of(stateSwitchCases.size(), label));
        return stateSwitchCases.size() - 1;
    }

    public void generateStateMachine() {
        var start_label = cob.newLabel();
        add_state(start_label);

        var handlers = new ArrayList<SpecialMethodHandler>();
        for (CodeElement coe : com) {
            if (coe instanceof InvokeInstruction is){
                var handler = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                if(handler != null)
                    handlers.add(handler.apply(this));
            }
        }
        cob.aload(0).getfield(gb.CD_this, GeneratorBuilder.STATE_NAME, TypeKind.INT.upperBound()).lookupswitch(invalidState, stateSwitchCases);
        var start = cob.startLabel();
        var end = cob.newLabel();
        cob.localVariable(0, "this", gb.CD_this, start, end);

        SpecialMethodHandler currentHandler = null;

        cob.labelBinding(start_label);
        for (CodeElement coe : com) {
            if (coe instanceof Instruction i) {
                if (ignore_next_pop)
                    if (i.opcode() == Opcode.POP) {
                        ignore_next_pop = false;
                        continue;
                    }else throw new RuntimeException("Expected Pop Instruction");
                if (i.opcode() == Opcode.ARETURN){
                    if (currentHandler !=null && currentHandler.handlerRan() == HandlerRan.ReplacingNextReturn){
                        currentHandler.handle(this);
                        currentHandler = null;
                        continue;
                    }
                }
            }
            if (coe instanceof Label l) {
                lt.encounterLabel(l);
            }
            if (coe instanceof InvokeInstruction is){
                if(smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol())) != null){
                    if(currentHandler!=null)throw new RuntimeException("Multiple method handlers at once not supported");
                    var handler = handlers.removeFirst();
                    if(!handler.removeCall()) cob.with(coe);
                    if(handler.handlerRan() == HandlerRan.Immediate) handler.handle(this);
                    else if(handler.handlerRan() == HandlerRan.ImmediateRemovePop) {
                        handler.handle(this);
                        ignore_next_pop = true;
                    }else
                        currentHandler = handler;
                    continue;
                }
            }

//            System.out.println(coe);

            switch (coe) {
                // locals which were once function parameters can be ignored
                case LocalVariable lv when lv.slot() < gb.paramSlotOff -> {
                }
                case LocalVariable lv ->
                        cob.localVariable(lv.slot() - gb.paramSlotOff + 1, lv.name(), lv.type(), lv.startScope(), lv.endScope());

                // increment indexes into the stack
                case IncrementInstruction ii when ii.slot() < gb.paramSlotOff ->
                        cob.aload(0).dup().getfield(gb.CD_this, GeneratorBuilder.PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int)
                                .loadConstant(ii.constant()).iadd()
                                .putfield(gb.CD_this, GeneratorBuilder.PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int);
                case IncrementInstruction ii -> cob.iinc(ii.slot() - gb.paramSlotOff + 1, ii.constant());

                // convert local function parameters to class fields and offset regular locals
                case LoadInstruction li when li.slot() < gb.paramSlotOff ->
                        cob.aload(0).getfield(gb.CD_this, GeneratorBuilder.PARAM_PREFIX + li.slot(), lt.paramType(li.slot()));
                case LoadInstruction li -> cob.loadLocal(li.typeKind(), li.slot() - gb.paramSlotOff + 1);

                // convert local function parameters to class fields and offset regular locals
                case StoreInstruction ls when ls.slot() < gb.paramSlotOff && ls.typeKind().slotSize() == 2 ->
                        cob.aload(0).dup_x2().pop().putfield(gb.CD_this, GeneratorBuilder.PARAM_PREFIX + ls.slot(), lt.paramType(ls.slot()));
                case StoreInstruction ls when ls.slot() < gb.paramSlotOff ->
                        cob.aload(0).swap().putfield(gb.CD_this, GeneratorBuilder.PARAM_PREFIX + ls.slot(), lt.paramType(ls.slot()));
                case StoreInstruction ls -> {
                    lt.trackLocal(ls.slot() - gb.paramSlotOff + 1, ls.typeKind());
                    cob.storeLocal(ls.typeKind(), ls.slot() - gb.paramSlotOff + 1);
                }

                default -> cob.with(coe);
            }
        }
        cob.labelBinding(invalidState);
        cob.new_(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString())).dup()
                .invokespecial(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString()), ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .athrow();
        cob.labelBinding(end);

        lt.createLocalStoreFields(clb);
        clb.withField("meow", GeneratorBuilder.CD_Gen, ClassFile.ACC_PRIVATE);
    }
}
