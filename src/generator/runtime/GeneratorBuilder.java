package generator.runtime;

import generator.Gen;

import java.lang.classfile.*;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo.TOP;

public class GeneratorBuilder {
    private final static String PARAM_PREFIX = "param_";
    private final static String LOCAL_PREFIX = "local_";
    private final static String STACK_PREFIX = "stack_";
    private final static String STATE_NAME = "state";

    private final static ClassDesc CD_Gen = ClassDesc.ofDescriptor(Gen.class.descriptorString());
    private final static ClassDesc CD_Res = ClassDesc.ofDescriptor(Gen.Res.class.descriptorString());
    private final static ClassDesc CD_Yield = ClassDesc.ofDescriptor(Gen.Yield.class.descriptorString());
    private final static ClassDesc CD_Ret = ClassDesc.ofDescriptor(Gen.Ret.class.descriptorString());
    private final static MethodTypeDesc MTD_Res = MethodTypeDesc.of(CD_Res);
    private final static MethodTypeDesc MTD_Gen_Obj = MethodTypeDesc.of(CD_Gen, ConstantDescs.CD_Object);

    private final String name;
    public final ClassDesc CD_this_gen;
    private final ClassDesc[] params;
    private final MethodTypeDesc MTD_init;
    private final int paramSlotOff;

    public interface ParamConsumer{
        void consume(String param, int slot, ClassDesc type);
    }

    public GeneratorBuilder(String name, ClassDesc[] params){
        this.name = name;
        CD_this_gen = ClassDesc.of(name);
        this.params = params;
        MTD_init = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        paramSlotOff = Arrays.stream(params).mapToInt(p -> TypeKind.from(p).slotSize()).sum();
    }

    public void params(int slot_start, ParamConsumer consumer){
        int offset = 0;
        for (var param : params) {
            consumer.consume(PARAM_PREFIX+offset, offset+slot_start, param);
            offset += TypeKind.from(param).slotSize();
        }
    }

    public void buildGeneratorMethodShim(CodeBuilder cob){
        cob.new_(CD_this_gen).dup();
        params(0, (_, slot, type) -> {
            cob.loadLocal(TypeKind.from(type), slot);
        });
        cob.invokespecial(CD_this_gen, ConstantDescs.INIT_NAME, MTD_init).areturn();
    }

    public byte[] buildGenerator(CodeModel com){
        return ClassFile.of(ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED).build(CD_this_gen, clb -> {
            clb.withInterfaces(List.of(clb.constantPool().classEntry(CD_Gen)));
            // parameter fields
            params(0, (param, _, type) -> {
                clb.withField(param, type, ClassFile.ACC_PRIVATE);
            });
            // fms state
            clb.withField(STATE_NAME, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE);

            // constructor
            clb.withMethod(ConstantDescs.INIT_NAME, MTD_init, ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
                cob.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                params(1, (param, slot, type) -> {
                    cob.aload(0).loadLocal(TypeKind.from(type), slot).putfield(CD_this_gen, param, type);
                });
                cob.return_();
            }));

            // fms method
            clb.withMethod("next", MethodTypeDesc.of(CD_Res), ClassFile.ACC_PUBLIC, mb -> mb.withCode(cob -> {
                buildGeneratorNext(clb, cob, com);
            }));
        });
    }

    private void buildGeneratorNext(ClassBuilder clb, CodeBuilder cob, CodeModel com){
        cob.trying(
                tcob -> generateStateMachine(clb, tcob, com),
                // catch anything set our state to -1 and throw the exception
                ctb -> ctb.catchingAll(
                        blc -> blc.aload(0).loadConstant(-1).putfield(CD_this_gen, STATE_NAME, ConstantDescs.CD_int).athrow()
                )
        ).aconst_null().areturn();
    }

    private void generateStateMachine(ClassBuilder clb, CodeBuilder cob, CodeModel com){

        var stateSwitchCases = new ArrayList<SwitchCase>();
        var invalidState = cob.newLabel();
        stateSwitchCases.add(SwitchCase.of(0, cob.newLabel()));
        int switchCase = 1;
        for (CodeElement coe : com) {
            if (coe instanceof InvokeInstruction is && is.opcode().equals(Opcode.INVOKESTATIC) && is.owner().asSymbol().equals(CD_Gen) && (is.name().equalsString("yield"))) {
                stateSwitchCases.add(SwitchCase.of(switchCase, cob.newLabel()));
                switchCase++;
            }
        }
        cob.aload(0).getfield(CD_this_gen, STATE_NAME, TypeKind.INT.upperBound()).lookupswitch(invalidState, stateSwitchCases);
        var start = cob.startLabel();
        var end = cob.newLabel();
        cob.localVariable(0, "this", CD_this_gen, start, end);

        var localTracker = new LocalTracker(com);

        switchCase = 1;
        cob.labelBinding(stateSwitchCases.removeFirst().target());
        final boolean[] ignore_next_return = {false};
        final boolean[] ignore_next_pop = {false};
        for (CodeElement coe : com) {
            switch (coe) {
                case Instruction ins when ins.opcode() == Opcode.POP && ignore_next_pop[0] -> {
                    ignore_next_pop[0] = false;
                    continue;
                }
                case ReturnInstruction _ when ignore_next_return[0] -> {
                    ignore_next_return[0] = false;
                    continue;
                }
                case Instruction _ when ignore_next_return[0] || ignore_next_pop[0] -> throw new RuntimeException();

                case Label l -> localTracker.encounterLabel(l);

                default -> {}
            }
            if(coe instanceof InvokeInstruction is
                    && is.opcode().equals(Opcode.INVOKESTATIC)
                    && is.owner().asSymbol().equals(CD_Gen)
                    && (is.name().equalsString("yield") || is.name().equalsString("ret"))){
                if (MethodTypeDesc.ofDescriptor(is.method().type().stringValue()).parameterArray().length == 0) {
                    cob.aconst_null();
                }

                if (is.name().equalsString("ret")) {
                    cob.aload(0).loadConstant(-1).putfield(CD_this_gen, STATE_NAME, TypeKind.INT.upperBound())
                            .new_(CD_Ret)
                            .dup_x1()
                            .swap()
                            .invokespecial(CD_Ret, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                            .areturn();
                    ignore_next_return[0] = true;
                } else {
                    int finalSwitchCase = switchCase;
                    switchCase++;
                    localTracker.savingLocals(CD_this_gen, cob, () -> {
                        cob.aload(0).loadConstant(finalSwitchCase).putfield(CD_this_gen, STATE_NAME, TypeKind.INT.upperBound())
                                .new_(CD_Yield)
                                .dup_x1()
                                .swap()
                                .invokespecial(CD_Yield, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                                .areturn();
                        cob.labelBinding(stateSwitchCases.removeFirst().target());
                    });

                    ignore_next_pop[0] = true;
                }
                continue;
            }
            switch (coe) {
                // locals which were once function parameters can be ignored
                case LocalVariable lv when lv.slot() < paramSlotOff -> {}
                case LocalVariable lv -> cob.localVariable(lv.slot() - paramSlotOff + 1, lv.name(), lv.type(), lv.startScope(), lv.endScope());

                // increment indexes into the stack
                case IncrementInstruction ii when ii.slot() < paramSlotOff ->
                        cob.aload(0).dup().getfield(CD_this_gen, PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int)
                                .loadConstant(ii.constant()).iadd()
                                .putfield(CD_this_gen, PARAM_PREFIX + ii.slot(), ConstantDescs.CD_int);
                case IncrementInstruction ii -> cob.iinc(ii.slot() - paramSlotOff + 1, ii.constant());

                // convert local function parameters to class fields and offset regular locals
                case LoadInstruction li when li.slot() < paramSlotOff ->
                        cob.aload(0).getfield(CD_this_gen, PARAM_PREFIX + li.slot(), localTracker.paramType(li.slot()));
                case LoadInstruction li -> cob.loadLocal(li.typeKind(), li.slot() - paramSlotOff + 1);

                // convert local function parameters to class fields and offset regular locals
                case StoreInstruction ls when ls.slot() < paramSlotOff && ls.typeKind().slotSize() == 2 ->
                        cob.aload(0).dup_x2().pop().putfield(CD_this_gen, PARAM_PREFIX + ls.slot(), localTracker.paramType(ls.slot()));
                case StoreInstruction ls when ls.slot() < paramSlotOff ->
                        cob.aload(0).swap().putfield(CD_this_gen, PARAM_PREFIX + ls.slot(), localTracker.paramType(ls.slot()));
                case StoreInstruction ls -> {
                    localTracker.trackLocal(ls.slot() - paramSlotOff + 1, ls.typeKind());
                    cob.storeLocal(ls.typeKind(), ls.slot() - paramSlotOff + 1);
                }

                default -> cob.with(coe);
            }
        }
        cob.labelBinding(invalidState);
        cob.new_(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString())).dup()
                .invokespecial(ClassDesc.ofDescriptor(IllegalStateException.class.descriptorString()), ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .athrow();
        cob.labelBinding(end);

        localTracker.createLocalStoreFields(clb);
    }


    private class LocalTracker {

        record LocalStore(String name, ClassDesc cd) {
        }

        HashMap<Integer, ClassDesc> parameter_map = new HashMap<>();

        HashMap<Integer, TypeKind> localVarTypes = new HashMap<>();
        HashMap<Label, StackMapFrameInfo> stackMapFrames = new HashMap<>();
        StackMapFrameInfo currentFrame;
        ArrayList<LocalStore> localStore = new ArrayList<>();


        private LocalTracker(CodeModel com) {
            int offset = 0;
            for (var param : params) {
                parameter_map.put(offset, param);
                offset += TypeKind.from(param).slotSize();
            }

            for (var attr : com.findAttributes(Attributes.stackMapTable())) {
                var entries = new ArrayList<StackMapFrameInfo>();
                for (var smfi : attr.entries()) {
                    var locals = new ArrayList<>(smfi.locals());
                    for (int i = 0; i < params.length; i++) locals.removeFirst();
                    locals.addFirst(StackMapFrameInfo.ObjectVerificationTypeInfo.of(CD_this_gen));
                    entries.add(StackMapFrameInfo.of(smfi.target(), locals, smfi.stack()));
                    stackMapFrames.put(smfi.target(), entries.getLast());
                }
            }
        }


        //Tries its best to reuse old saved locals field slots, only reuses if types exactly match
        public void savingLocals(ClassDesc cd, CodeBuilder cob, Runnable run) {
            record Saved(int slot, String name, ClassDesc cd) {
            }

            var saved = new ArrayList<Saved>();
            var lls = new ArrayList<>(localStore);
            currentLocals((slot, tk, desc) -> {
                String name = null;
                for (int i = 0; i < lls.size(); i++)
                    if (lls.get(i).cd.equals(desc)) {
                        name = lls.get(i).name;
                        lls.remove(i);
                        break;
                    }
                if (name == null) {
                    name = LOCAL_PREFIX + localStore.size();
                    localStore.add(new LocalStore(name, desc));
                }
                saved.add(new Saved(slot, name, desc));
                cob.aload(0).loadLocal(tk, slot).putfield(cd, name, desc);
            });
            run.run();

            for (var save : saved) {
                cob.aload(0).getfield(cd, save.name, save.cd).storeLocal(TypeKind.from(save.cd), save.slot);
            }
        }

        public void createLocalStoreFields(ClassBuilder clb) {
            for (var local : localStore) {
                clb.withField(local.name, local.cd, ClassFile.ACC_PRIVATE);
            }
        }

        public void encounterLabel(Label l) {
            var tmp = stackMapFrames.get(l);
            if (tmp != null) {
                localVarTypes.clear();
                currentFrame = tmp;
            }
        }

        public ClassDesc paramType(int slot) {
            return parameter_map.get(slot);
        }

        public void trackLocal(int slot, TypeKind typeKind) {
            localVarTypes.put(slot, typeKind);
        }

        interface LocalConsumer {
            void consume(int slot, TypeKind tk, ClassDesc desc);
        }

        void currentLocals(LocalConsumer consumer) {
            var slot = 0;
            if (currentFrame != null)
                for (var kind : currentFrame.locals()) {
                    switch (kind) {
                        case StackMapFrameInfo.ObjectVerificationTypeInfo o -> {
                            if (slot != 0)
                                consumer.consume(slot, o.className().typeKind(), o.classSymbol());
                            slot += 1;
                        }
                        case StackMapFrameInfo.SimpleVerificationTypeInfo ti -> {
                            if (kind == TOP) {
                                slot += 1;
                                if (localVarTypes.get(slot - 1) instanceof TypeKind tk) {
                                    ClassDesc cd = tk.upperBound();
                                    consumer.consume(slot - 1, tk, cd);
                                }
                                continue;
                            }
                            var type = switch (ti) {
                                case INTEGER -> TypeKind.INT;
                                case FLOAT -> TypeKind.FLOAT;
                                case DOUBLE -> TypeKind.DOUBLE;
                                case LONG -> TypeKind.LONG;
                                case NULL -> TypeKind.REFERENCE;
                                default -> throw new IllegalStateException();
                            };
                            consumer.consume(slot, type, type.upperBound());
                            slot += 1;
                        }
                        case StackMapFrameInfo.UninitializedVerificationTypeInfo _ -> throw new IllegalStateException();
                    }
                }
            for (var entry : localVarTypes.entrySet()) {
                if (entry.getKey() < slot) continue;
                ClassDesc cd = entry.getValue().upperBound();
                consumer.consume(entry.getKey(), TypeKind.from(cd), cd);
            }
        }
    }
}
