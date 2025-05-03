package generators.loadtime;

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
import java.util.stream.Collectors;

public abstract class StateMachineBuilder {
    public final static String PARAM_PREFIX = "param_";
    public final static String LOCAL_PREFIX = "local_";
    public final static String STATE_NAME = "state";

    public final ClassDesc CD_this;
    public final String innerClassName;
    public final ClassDesc[] params;
    public final MethodTypeDesc MTD_init;
    public final int paramSlotOff;

    public final ClassModel src_clm;
    public final MethodModel src_mem;
    public final CodeModel src_com;

    ArrayList<LState> lstate = new ArrayList<>();

    private final ArrayList<Frame> frames = new ArrayList<>();

    protected final HashMap<SpecialMethod, SpecialMethodBuilder> smmap = new HashMap<>();



    record LState(String name, ClassDesc cd) {
    }

    public interface SpecialMethodBuilder{
        SpecialMethodHandler build(StateMachineBuilder smb, CodeBuilder cob, StateBuilder sb);
    }

    public void params(int slot_start, ParamConsumer consumer){
        int offset = 0;
        for (var param : params) {
            consumer.consume(PARAM_PREFIX+offset, offset+slot_start, param);
            offset += TypeKind.from(param).slotSize();
        }
    }

    public StateMachineBuilder(ClassModel src_clm, MethodModel src_mem, CodeModel src_com, String namePostfix){
        this.src_clm = src_clm;
        this.src_mem = src_mem;
        this.src_com = src_com;

        var mts = src_mem.methodTypeSymbol();
        mts = mts.changeReturnType(ConstantDescs.CD_void);
        if (!src_mem.flags().has(AccessFlag.STATIC)) {
            mts = mts.insertParameterTypes(0, src_clm.thisClass().asSymbol());
        }
        var cdn = src_clm.thisClass().asSymbol().displayName();
        var method_name = src_mem.methodName().stringValue();
        var param_cnd = src_mem.methodTypeSymbol().parameterList().stream().map(
                desc -> desc.descriptorString()
                        .replace("/", "__")
                        .replace(";", "__")
                        .replace("[", "_$_$_")
                )
                .collect(Collectors.joining("$"));
        innerClassName = method_name + "$$" + param_cnd + "$$";
        var name = cdn + "$" +innerClassName;

        this.CD_this = ClassDesc.of(src_clm.thisClass().asSymbol().packageName(), name);
        this.params = mts.parameterArray();
        this.MTD_init = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        this.paramSlotOff = Arrays.stream(params).mapToInt(p -> TypeKind.from(p).slotSize()).sum();

        var lt = new FrameTracker(this, src_com);
        for(var coe : src_com){
            if(coe instanceof Instruction) {
                frames.add(new Frame(lt.locals(), lt.stack()));
//                System.out.println(frames.getLast() + " " + coe);
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

    public void buildSourceMethodShim(CodeBuilder cob){
        cob.new_(CD_this).dup();
        params(0, (_, slot, type) -> {
            cob.loadLocal(TypeKind.from(type), slot);
        });
        cob.invokespecial(CD_this, ConstantDescs.INIT_NAME, MTD_init).areturn();
    }

    public boolean shouldBeInnerClass(){
        return true;
    }

    public byte[] buildStateMachine(){
        return ClassFile.of(ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES).build(CD_this, clb -> {

            if(shouldBeInnerClass()){
                src_clm.findAttributes(Attributes.sourceFile()).forEach(clb::with);
                clb.with(InnerClassesAttribute.of(InnerClassInfo.of(CD_this, Optional.of(src_clm.thisClass().asSymbol()), Optional.of(innerClassName), AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC)));
                clb.with(NestHostAttribute.of(src_clm.thisClass()));
            }

            // parameter fields
            params(0, (param, _, type) -> {
                clb.withField(param, type, ClassFile.ACC_PRIVATE);
            });

            clb.withField(STATE_NAME, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE);

            // constructor
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_init, ClassFile.ACC_PRIVATE, mb -> mb.withCode(cob -> {
                cob.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                params(1, (param, slot, type) -> {
                    cob.aload(0).loadLocal(TypeKind.from(type), slot).putfield(CD_this, param, type);
                });
                cob.return_();
            }));

            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);

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

    public void nonresumable_return(CodeBuilder cob, TypeKind kind){
        this.synchronized_exit(cob);
        cob.return_(kind);
    }

    public void resumable_return(CodeBuilder cob, StateBuilder.State resume, TypeKind kind) {
        this.synchronized_exit(cob);
        cob.return_(kind);
        resume.bind(cob);
    }

    protected void synchronized_start(CodeBuilder cob){
        if(src_mem.flags().has(AccessFlag.SYNCHRONIZED)){
            if(src_mem.flags().has(AccessFlag.STATIC)) cob.loadConstant(src_clm.thisClass().asSymbol());
            else cob.aload(0).getfield(CD_this, PARAM_PREFIX+0, src_clm.thisClass().asSymbol());
            cob.monitorenter();
        }
    }

    public void synchronized_exit(CodeBuilder cob){
        if(src_mem.flags().has(AccessFlag.SYNCHRONIZED)){
            if(src_mem.flags().has(AccessFlag.STATIC)) cob.loadConstant(src_clm.thisClass().asSymbol());
            else cob.aload(0).getfield(CD_this, PARAM_PREFIX+0, src_clm.thisClass().asSymbol());
            cob.monitorexit();
        }
    }

    protected abstract void buildStateMachineMethod(ClassBuilder clb);

    public void buildStateMachineMethodCode(ClassBuilder clb, CodeBuilder cob, int loc_param_off){
        this.synchronized_start(cob);
        cob.trying(
                tcob -> buildStateMachineCode(clb, tcob, loc_param_off),
                // catch anything set our state to -1 and throw the exception
                ctb -> ctb.catchingAll(
                        blc -> {
                            blc.aload(0).loadConstant(-1).putfield(CD_this, STATE_NAME, ConstantDescs.CD_int);
                            this.synchronized_exit(blc);
                            cob.athrow();
                        }
                )
        );
    }

    public void buildStateMachineCode(ClassBuilder clb, CodeBuilder cob, int loc_param_off) {
        var stateBuilder = new StateBuilder();
        var handlers = new ArrayList<SpecialMethodHandler>();


        boolean ignore_next_pop = false;

        var invalid_state = cob.newLabel();
        var start_state = stateBuilder.create(cob);

        for(var wf : with_frames()){
            if (wf.coe() instanceof InvokeInstruction is){
                var handler = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                if(handler != null)
                    handlers.add(handler.build(this, cob, stateBuilder));
            }
        }

        cob.aload(0).getfield(CD_this, STATE_NAME, TypeKind.INT.upperBound());
        stateBuilder.buildSwitch(cob, invalid_state);

        cob.localVariable(0, "this", CD_this, cob.startLabel(), cob.endLabel());

        {
            int i = 0;
            for (var wf : with_frames()) {
                if (wf.coe() instanceof InvokeInstruction is) {
                    var h = smmap.get(new SpecialMethod(is.owner().asSymbol(), is.name().stringValue(), is.typeSymbol()));
                    if(h!=null) handlers.get(i++).build_prelude(this, cob, wf.frame());
                }
            }
        }

        SpecialMethodHandler currentHandler = null;

        start_state.bind(cob);
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
                        currentHandler.build_inline(this, cob, frame);
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
                    if(handler.replacementKind() == ReplacementKind.Immediate) handler.build_inline(this, cob, frame);
                    else if(handler.replacementKind() == ReplacementKind.ImmediateReplacingPop) {
                        handler.build_inline(this, cob, frame);
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

        for (var lstate : lstate) {
            clb.withField(lstate.name(), lstate.cd(), ClassFile.ACC_PRIVATE);
        }
    }
}
