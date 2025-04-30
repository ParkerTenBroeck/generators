package generator.runtime;

import java.lang.classfile.*;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.function.BiFunction;

public abstract class StateMachineBuilder {
    public final static String PARAM_PREFIX = "param_";
    public final static String LOCAL_PREFIX = "local_";
    public final static String STATE_NAME = "state";

    private static int sequence;

    public final ClassDesc CD_this;
    public final ClassDesc[] params;
    public final MethodTypeDesc MTD_init;
    public final int paramSlotOff;

    public final ClassModel src_clm;
    public final MethodModel src_mem;
    public final CodeModel src_com;

    ArrayList<LState> lstate = new ArrayList<>();

    private final ArrayList<Frame> frames = new ArrayList<>();
    private final ArrayList<SpecialMethodHandler> handlers = new ArrayList<>();

    record LState(String name, ClassDesc cd) {
    }

    protected HashMap<SpecialMethod, BiFunction<StateMachineBuilder, CodeBuilder, SpecialMethodHandler>> smmap = new HashMap<>();

    private final ArrayList<SwitchCase> stateSwitchCases = new ArrayList<>();

    protected String uniqueName(){
        return sequence+++"";
    }

    public void params(int slot_start, ParamConsumer consumer){
        int offset = 0;
        for (var param : params) {
            consumer.consume(PARAM_PREFIX+offset, offset+slot_start, param);
            offset += TypeKind.from(param).slotSize();
        }
    }

    public StateMachineBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com){
        this.src_clm = src_clm;
        this.src_mem = src_mem;
        this.src_com = src_com;

        var mts = src_mem.methodTypeSymbol();
        mts = mts.changeReturnType(ConstantDescs.CD_void);
        if (!src_mem.flags().has(AccessFlag.STATIC)) {
            mts = mts.insertParameterTypes(0, src_clm.thisClass().asSymbol());
        }
        var name = src_clm.thisClass().asSymbol().displayName() + "$" + src_mem.methodName().stringValue() + "$" + uniqueName();

        this.CD_this = ClassDesc.of(src_clm.thisClass().asSymbol().packageName(), name);
        this.params = mts.parameterArray();
        this.MTD_init = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        this.paramSlotOff = Arrays.stream(params).mapToInt(p -> TypeKind.from(p).slotSize()).sum();


        System.out.println("FRAME");
        var lt = new FrameTracker(this, src_com);
        int bco = 0;
        for(var coe : src_com){
            if(coe instanceof Instruction i) {
                frames.add(new Frame(lt.locals(), lt.stack()));
                System.out.println(bco + " " + frames.getLast() + " " + coe);
                bco += i.sizeInBytes();
            }
            lt.encounter(coe);

        }
        frames.add(new Frame(lt.locals(), lt.stack()));
    }

    public record WithFrame(CodeElement coe, Frame frame){}
    public Iterable<WithFrame> with_frames(){
        return () -> {
            Iterator<CodeElement> coes = src_com.iterator();
            return new Iterator<>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return coes.hasNext();
                }

                @Override
                public WithFrame next() {
                    var coe = coes.next();
                    var frame = frames.get(i);
                    if (coe instanceof Instruction) i++;
                    return new WithFrame(coe, frame);
                }
            };
        };
    }

    public int add_state(Label label) {
        stateSwitchCases.add(SwitchCase.of(stateSwitchCases.size(), label));
        return stateSwitchCases.size() - 1;
    }

    public void buildSourceMethodShim(CodeBuilder cob){
        cob.new_(CD_this).dup();
        params(0, (_, slot, type) -> {
            cob.loadLocal(TypeKind.from(type), slot);
        });
        cob.invokespecial(CD_this, ConstantDescs.INIT_NAME, MTD_init).areturn();
    }

    public boolean shouldBeInnerClass(){
        return false;
    }

    public byte[] buildStateMachine(){
        return ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(CD_this, clb -> {

            if(shouldBeInnerClass()){
                src_clm.findAttributes(Attributes.sourceFile()).forEach(clb::with);
                clb.with(InnerClassesAttribute.of(InnerClassInfo.of(CD_this, Optional.of(src_clm.thisClass().asSymbol()), Optional.of(CD_this.displayName().split("\\$")[1]), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC)));
                clb.with(NestHostAttribute.of(src_clm.thisClass()));
            }

            // parameter fields
            params(0, (param, _, type) -> {
                clb.withField(param, type, ClassFile.ACC_PRIVATE);
            });

            clb.withField(STATE_NAME, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE);

            // constructor
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_init, ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
                cob.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                params(1, (param, slot, type) -> {
                    cob.aload(0).loadLocal(TypeKind.from(type), slot).putfield(CD_this, param, type);
                });
                cob.return_();
            }));

            buildStateMachineMethod(clb);
        });
    }

    public boolean hasAnyHandlers(){
        for(var wf : with_frames()){
            if (wf.coe() instanceof InvokeInstruction is){
                var handler = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                if(handler != null) return true;
            }
        }
        return false;
    }

    protected abstract void buildStateMachineMethod(ClassBuilder clb);

    public void buildStateMachineMethodCode(ClassBuilder clb, CodeBuilder cob, int loc_param_off){
        cob.trying(
                tcob -> buildStateMachineCode(clb, tcob, loc_param_off),
                // catch anything set our state to -1 and throw the exception
                ctb -> ctb.catchingAll(
                        blc ->
                                blc.aload(0).loadConstant(-1).putfield(CD_this, STATE_NAME, ConstantDescs.CD_int)
                                        .athrow()
                )
        ).aconst_null().areturn();
    }

    public void buildStateMachineCode(ClassBuilder clb, CodeBuilder cob, int loc_param_off) {
        boolean ignore_next_pop = false;

        var invalid_state = cob.newLabel();
        var start_label = cob.newLabel();
        add_state(start_label);

        for(var wf : with_frames()){
            if (wf.coe() instanceof InvokeInstruction is){
                var handler = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                if(handler != null)
                    handlers.add(handler.apply(this, cob));
            }
        }

        cob.aload(0).getfield(CD_this, STATE_NAME, TypeKind.INT.upperBound()).lookupswitch(invalid_state, stateSwitchCases);
        var start = cob.startLabel();
        var end = cob.newLabel();
        cob.localVariable(0, "this", CD_this, start, end);

        {
            int i = 0;
            for (var wf : with_frames()) {
                if (wf.coe() instanceof InvokeInstruction is) {
                    var h = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                    if(h!=null){
                        var handler = handlers.get(i++);
                        cob.labelBinding(handler.handler_start);
                        handler.buildHandler(this, cob, wf.frame());
                        cob.goto_(handler.handler_resume);
                    }
                }
            }
        }

        SpecialMethodHandler currentHandler = null;

        cob.labelBinding(start_label);
        for (var wf : with_frames()) {
            var coe = wf.coe();
            var frame = wf.frame();
            if (coe instanceof Instruction i) {
                if (ignore_next_pop)
                    if (i.opcode() == Opcode.POP) {
                        ignore_next_pop = false;
                        continue;
                    }else throw new RuntimeException("Expected Pop Instruction");
                if (i.opcode() == Opcode.ARETURN){
                    if (currentHandler !=null && currentHandler.replacementKind() == ReplacementKind.ReplacingNextReturn){
                        currentHandler.insertShim(this, cob);
                        currentHandler = null;
                        continue;
                    }
                }
            }
            if (coe instanceof InvokeInstruction is){
                if(smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol())) != null){
                    if(currentHandler!=null)throw new RuntimeException("Multiple method handlers at once not supported");
                    var handler = handlers.removeFirst();
                    if(!handler.removeCall()) cob.with(coe);
                    if(handler.replacementKind() == ReplacementKind.Immediate) handler.insertShim(this, cob);
                    else if(handler.replacementKind() == ReplacementKind.ImmediateReplacingPop) {
                        handler.insertShim(this, cob);
                        ignore_next_pop = true;
                    }else
                        currentHandler = handler;
                    continue;
                }
            }



            switch (coe) {
                // locals which were once function parameters can be ignored
                case LocalVariable lv when lv.slot() < paramSlotOff -> {
                }
                case LocalVariable lv ->
                        cob.localVariable(lv.slot() - paramSlotOff + loc_param_off, lv.name(), lv.type(), lv.startScope(), lv.endScope());

                // increment indexes into the stack
                case IncrementInstruction ii when ii.slot() < paramSlotOff ->
                        cob.aload(0).dup().getfield(CD_this, PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int)
                                .loadConstant(ii.constant()).iadd()
                                .putfield(CD_this, PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int);
                case IncrementInstruction ii -> cob.iinc(ii.slot() - paramSlotOff + loc_param_off, ii.constant());

                // convert local function parameters to class fields and offset regular locals
                case LoadInstruction li when li.slot() < paramSlotOff ->
                        cob.aload(0).getfield(CD_this, PARAM_PREFIX + li.slot(), frame.locals()[li.slot()].toCD());
                case LoadInstruction li ->
                        cob.loadLocal(li.typeKind(), li.slot() - paramSlotOff + loc_param_off);

                // convert local function parameters to class fields and offset regular locals
                case StoreInstruction ls when ls.slot() < paramSlotOff && ls.typeKind().slotSize() == 2 ->
                        cob.aload(0).dup_x2().pop().putfield(CD_this, PARAM_PREFIX + ls.slot(), frame.locals()[ls.slot()].toCD());
                case StoreInstruction ls when ls.slot() < paramSlotOff ->
                        cob.aload(0).swap().putfield(CD_this, PARAM_PREFIX + ls.slot(), frame.locals()[ls.slot()].toCD());
                case StoreInstruction ls ->
                        cob.storeLocal(ls.typeKind(), ls.slot() - paramSlotOff + loc_param_off);

                default -> cob.with(coe);
            }
        }
        cob.labelBinding(invalid_state);
        cob.new_(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString())).dup()
                .invokespecial(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString()), ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .athrow();
        cob.labelBinding(end);

        for (var lstate : lstate) {
            clb.withField(lstate.name(), lstate.cd(), ClassFile.ACC_PRIVATE);
        }
    }
}
