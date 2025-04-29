package generator.runtime;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import static generator.runtime.StateMachineBuilder.LOCAL_PREFIX;
import static java.lang.constant.ConstantDescs.*;

public class LocalTracker {

    record LocalStore(String name, ClassDesc cd) {
    }

    private static final int ITEM_TOP = 0,
            ITEM_INTEGER = 1,
            ITEM_FLOAT = 2,
            ITEM_DOUBLE = 3,
            ITEM_LONG = 4,
            ITEM_NULL = 5,
            ITEM_UNINITIALIZED_THIS = 6,
            ITEM_OBJECT = 7,
            ITEM_UNINITIALIZED = 8,
            ITEM_BOOLEAN = 9,
            ITEM_BYTE = 10,
            ITEM_SHORT = 11,
            ITEM_CHAR = 12,
            ITEM_LONG_2ND = 13,
            ITEM_DOUBLE_2ND = 14;

    private static record Type(int tag, ClassDesc sym, int bci) {

        //singleton types
        static final Type TOP_TYPE = simpleType(ITEM_TOP),
                NULL_TYPE = simpleType(ITEM_NULL),
                INTEGER_TYPE = simpleType(ITEM_INTEGER),
                FLOAT_TYPE = simpleType(ITEM_FLOAT),
                LONG_TYPE = simpleType(ITEM_LONG),
                LONG2_TYPE = simpleType(ITEM_LONG_2ND),
                DOUBLE_TYPE = simpleType(ITEM_DOUBLE),
                BOOLEAN_TYPE = simpleType(ITEM_BOOLEAN),
                BYTE_TYPE = simpleType(ITEM_BYTE),
                CHAR_TYPE = simpleType(ITEM_CHAR),
                SHORT_TYPE = simpleType(ITEM_SHORT),
                DOUBLE2_TYPE = simpleType(ITEM_DOUBLE_2ND),
                UNITIALIZED_THIS_TYPE = simpleType(ITEM_UNINITIALIZED_THIS);

        //frequently used types to reduce footprint
        static final Type OBJECT_TYPE = referenceType(CD_Object),
                THROWABLE_TYPE = referenceType(CD_Throwable),
                INT_ARRAY_TYPE = referenceType(CD_int.arrayType()),
                BOOLEAN_ARRAY_TYPE = referenceType(CD_boolean.arrayType()),
                BYTE_ARRAY_TYPE = referenceType(CD_byte.arrayType()),
                CHAR_ARRAY_TYPE = referenceType(CD_char.arrayType()),
                SHORT_ARRAY_TYPE = referenceType(CD_short.arrayType()),
                LONG_ARRAY_TYPE = referenceType(CD_long.arrayType()),
                DOUBLE_ARRAY_TYPE = referenceType(CD_double.arrayType()),
                FLOAT_ARRAY_TYPE = referenceType(CD_float.arrayType()),
                STRING_TYPE = referenceType(CD_String),
                CLASS_TYPE = referenceType(CD_Class),
                METHOD_HANDLE_TYPE = referenceType(CD_MethodHandle),
                METHOD_TYPE = referenceType(CD_MethodType);

        @Override
        public String toString(){
            return switch(tag){
                case ITEM_TOP -> "TOP";
                case ITEM_INTEGER ->  "int";
                case ITEM_FLOAT -> "float";
                case ITEM_DOUBLE -> "double";
                case ITEM_LONG -> "long";
                case ITEM_NULL -> sym==null?"null":"null("+sym.displayName()+")";
                case ITEM_UNINITIALIZED_THIS -> sym==null?"uninitialized(this)":"uninitialized(this "+sym.displayName()+")";
                case ITEM_OBJECT -> "Object("+sym.displayName()+")";
                case ITEM_UNINITIALIZED -> sym==null?"uninitialized()":"uninitialized("+sym.displayName()+")";
                case ITEM_BOOLEAN -> "boolean";
                case ITEM_BYTE -> "byte";
                case ITEM_SHORT -> "short";
                case ITEM_CHAR -> "char";
                case ITEM_LONG_2ND -> "float2";
                case ITEM_DOUBLE_2ND  -> "double2";
                default -> throw new IllegalStateException("Unexpected value: " + tag);
            };
        }

        private static Type simpleType(int tag) {
            return new Type(tag, null, 0);
        }

        static Type referenceType(ClassDesc desc) {
            return new Type(ITEM_OBJECT, desc, 0);
        }

        static Type uninitializedType(ClassDesc sym) {
            return new Type(ITEM_UNINITIALIZED, sym, 0);
        }

        @Override //mandatory override to avoid use of method reference during JDK bootstrap
        public boolean equals(Object o) {
            return (o instanceof Type t) && t.tag == tag && t.bci == bci && Objects.equals(sym, t.sym);
        }

        boolean isCategory2_2nd() {
            return this == DOUBLE2_TYPE || this == LONG2_TYPE;
        }

        boolean isReference() {
            return tag == ITEM_OBJECT || this == NULL_TYPE;
        }

        boolean isObject() {
            return tag == ITEM_OBJECT && sym.isClassOrInterface();
        }

        boolean isArray() {
            return tag == ITEM_OBJECT && sym.isArray();
        }


        static Type verificationType(StackMapFrameInfo.VerificationTypeInfo v){
            return switch (v){
                case StackMapFrameInfo.ObjectVerificationTypeInfo o -> Type.referenceType(o.classSymbol());
                case StackMapFrameInfo.SimpleVerificationTypeInfo s -> Type.simpleType(s.tag());
                case StackMapFrameInfo.UninitializedVerificationTypeInfo u -> {
                    //Type.simpleType(u.tag());
                    throw new RuntimeException();
                }
            };
        }

        ClassDesc toCD(){
            return switch (tag) {
                case ITEM_BOOLEAN -> CD_boolean;
                case ITEM_BYTE -> CD_byte;
                case ITEM_CHAR -> CD_char;
                case ITEM_SHORT -> CD_short;
                case ITEM_INTEGER -> CD_int;
                case ITEM_LONG -> CD_long;
                case ITEM_FLOAT -> CD_float;
                case ITEM_DOUBLE -> CD_double;
                case ITEM_OBJECT -> sym;
                case ITEM_NULL -> CD_Object;
                default -> throw new RuntimeException();
            };
        }

        Type toArray() {
            return switch (tag) {
                case ITEM_BOOLEAN -> BOOLEAN_ARRAY_TYPE;
                case ITEM_BYTE -> BYTE_ARRAY_TYPE;
                case ITEM_CHAR -> CHAR_ARRAY_TYPE;
                case ITEM_SHORT -> SHORT_ARRAY_TYPE;
                case ITEM_INTEGER -> INT_ARRAY_TYPE;
                case ITEM_LONG -> LONG_ARRAY_TYPE;
                case ITEM_FLOAT -> FLOAT_ARRAY_TYPE;
                case ITEM_DOUBLE -> DOUBLE_ARRAY_TYPE;
                case ITEM_OBJECT -> Type.referenceType(sym.arrayType());
                default -> OBJECT_TYPE;
            };
        }

        Type getComponent() {
            if (isArray()) {
                var comp = sym.componentType();
                if (comp.isPrimitive()) {
                    return switch (comp.descriptorString().charAt(0)) {
                        case 'Z' -> Type.BOOLEAN_TYPE;
                        case 'B' -> Type.BYTE_TYPE;
                        case 'C' -> Type.CHAR_TYPE;
                        case 'S' -> Type.SHORT_TYPE;
                        case 'I' -> Type.INTEGER_TYPE;
                        case 'J' -> Type.LONG_TYPE;
                        case 'F' -> Type.FLOAT_TYPE;
                        case 'D' -> Type.DOUBLE_TYPE;
                        default -> Type.TOP_TYPE;
                    };
                }
                return Type.referenceType(comp);
            }
            return Type.TOP_TYPE;
        }
    }


    final ArrayList<Type> stack = new ArrayList<>();
    final ArrayList<Type> locals = new ArrayList<>();
//    HashMap<Integer, TypeKind> localVarTypes = new HashMap<>();
//    StackMapFrameInfo currentFrame;

    HashMap<Integer, ClassDesc> parameter_map = new HashMap<>();
    HashMap<Label, StackMapFrameInfo> stackMapFrames = new HashMap<>();
    ArrayList<LocalStore> localStore = new ArrayList<>();
    
    final int local_param_off;
    final StateMachineBuilder smb;


    LocalTracker(StateMachineBuilder smb, CodeModel com, int local_param_off) {
        this.local_param_off = local_param_off;
        this.smb = smb;
        int offset = 0;



        for (var param : smb.params) {
            parameter_map.put(offset, param);

            if(param == CD_long){
                setLocal2(offset, Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            }else if(param == CD_double){
                setLocal2(offset, Type.LONG_TYPE, Type.LONG2_TYPE);
            }else if(!param.isPrimitive()){
                setLocal(offset, Type.referenceType(param));
            }else if(param == CD_float){
                setLocal(offset, Type.FLOAT_TYPE);
            }else if(param == CD_char){
                setLocal(offset, Type.CHAR_TYPE);
            }else if(param == CD_boolean){
                setLocal(offset, Type.BOOLEAN_TYPE);
            }else if(param == CD_byte){
                setLocal(offset, Type.BYTE_TYPE);
            }else if(param == CD_short){
                setLocal(offset, Type.SHORT_TYPE);
            }else{
                setLocal(offset, Type.INTEGER_TYPE);
            }
            offset += TypeKind.from(param).slotSize();
        }

        for (var attr : com.findAttributes(Attributes.stackMapTable())) {
            var entries = new ArrayList<StackMapFrameInfo>();
            for (var smfi : attr.entries()) {
                var locals = new ArrayList<>(smfi.locals());
                for (int i = 0; i < smb.params.length; i++)
                    locals.removeFirst();
                entries.add(StackMapFrameInfo.of(smfi.target(), locals, smfi.stack()));
                stackMapFrames.put(smfi.target(), entries.getLast());
            }
        }
    }

    LocalTracker pushStack(ClassDesc desc) {
        if (desc == CD_long)   return pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
        if (desc == CD_double) return pushStack(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
        return desc == CD_void ? this
                : pushStack(
                desc.isPrimitive()
                        ? (desc == CD_float ? Type.FLOAT_TYPE : Type.INTEGER_TYPE)
                        : Type.referenceType(desc));
    }

    LocalTracker pushStack(Type type) {
        stack.add(type);
        return this;
    }

    LocalTracker pushStack(Type type1, Type type2) {
        stack.add(type1);
        stack.add(type2);
        return this;
    }

    Type popStack() {
        return stack.removeLast();
    }

    LocalTracker decStack(int size) {
        for(int i = 0; i < size; i ++)stack.removeLast();
        return this;
    }

    void setLocal(int slot, Type type){
        while(locals.size()<=slot)locals.add(null);

        var old = locals.get(slot);
            if(old == Type.DOUBLE_TYPE || old == Type.LONG_TYPE)
                locals.set(slot+1, Type.TOP_TYPE);
        if(old == Type.DOUBLE2_TYPE || old == Type.LONG2_TYPE)
            locals.set(slot-1, Type.TOP_TYPE);
        locals.set(slot, type);
    }

    void setLocal2(int slot, Type type1, Type type2){
        while(locals.size()<=slot+1)locals.add(null);

        var old = locals.get(slot+1);
        if(old == Type.DOUBLE_TYPE || old == Type.LONG_TYPE)
            locals.set(slot+2, Type.TOP_TYPE);
        old = locals.get(slot);
        if(old == Type.DOUBLE2_TYPE || old == Type.LONG2_TYPE)
            locals.set(slot-1, Type.TOP_TYPE);

        locals.set(slot, type1);
        locals.set(slot+1, type2);
    }

    public void encounter(CodeElement ce){
        switch(ce){
            case Label l -> encounterLabel(l);
            case Instruction ins -> {
                switch (ins) {
                    case ArrayLoadInstruction al -> {
                        popStack();
                        var arr = popStack();
                        pushStack(arr.toCD().componentType());
                    }
                    case ArrayStoreInstruction as -> decStack(1 + as.typeKind().slotSize());
                    case BranchInstruction b when b.opcode() == Opcode.GOTO || b.opcode() == Opcode.GOTO_W -> {}
                    case BranchInstruction b -> popStack();
                    case ConstantInstruction c when ins.opcode() == Opcode.ACONST_NULL -> pushStack(Type.NULL_TYPE);
                    case ConstantInstruction c -> pushStack(c.typeKind().upperBound());
                    case ConvertInstruction c -> decStack(c.fromType().slotSize()).pushStack(c.toType().upperBound());
                    case FieldInstruction f -> {

                    }
                    case IncrementInstruction i -> {}
                    case InvokeDynamicInstruction i -> {
                        for(var param : i.typeSymbol().parameterArray())
                            decStack(TypeKind.from(param).slotSize());
                        pushStack(i.typeSymbol().returnType());
                    }
                    case InvokeInstruction i when i.opcode() == Opcode.INVOKESTATIC -> {
                        for(var param : i.typeSymbol().parameterArray())
                            decStack(TypeKind.from(param).slotSize());
                        pushStack(i.typeSymbol().returnType());
                    }
                    case InvokeInstruction i when i.opcode() == Opcode.INVOKESPECIAL && i.name().equalsString(INIT_NAME) -> {
                        for(var param : i.typeSymbol().parameterArray())
                            decStack(TypeKind.from(param).slotSize());
                        var ty = popStack();
                    }
                    case InvokeInstruction i -> {
                        for(var param : i.typeSymbol().parameterArray())
                            decStack(TypeKind.from(param).slotSize());
                        popStack();
                        pushStack(i.typeSymbol().returnType());
                    }
                    case LoadInstruction l when locals.size()<=l.slot()||locals.get(l.slot())==null ->
                            pushStack(l.typeKind().upperBound());
                    case LoadInstruction l ->
                            pushStack(locals.get(l.slot()).toCD());
                    case LookupSwitchInstruction ls -> popStack();
                    case MonitorInstruction m -> popStack();
                    case NewMultiArrayInstruction nma -> decStack(nma.dimensions()).pushStack(nma.arrayType().asSymbol());
                    case NewObjectInstruction no -> pushStack(no.className().asSymbol());
                    case NewPrimitiveArrayInstruction npa -> decStack(1).pushStack(npa.typeKind().upperBound().arrayType());
                    case NewReferenceArrayInstruction nra -> decStack(1).pushStack(nra.componentType().asSymbol());
                    case NopInstruction n -> {}
                    
                    case OperatorInstruction o -> {
                        switch(o.opcode()){
                            case IADD, ISUB, IMUL, IDIV, IREM, ISHL, ISHR, IUSHR, IOR, IXOR, IAND ->
                                    decStack(2).pushStack(Type.INTEGER_TYPE);
                            case INEG, ARRAYLENGTH, INSTANCEOF ->
                                    decStack(1).pushStack(Type.INTEGER_TYPE);
                            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR ->
                                    decStack(4).pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
                            case LNEG ->
                                    decStack(2).pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
                            case LSHL, LSHR, LUSHR ->
                                    decStack(3).pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
                            case FADD, FSUB, FMUL, FDIV, FREM ->
                                    decStack(2).pushStack(Type.FLOAT_TYPE);
                            case FNEG ->
                                    decStack(1).pushStack(Type.FLOAT_TYPE);
                            case DADD, DSUB, DMUL, DDIV, DREM ->
                                    decStack(4).pushStack(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
                            case DNEG ->
                                    decStack(2).pushStack(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
                            default -> throw new RuntimeException();
                        }
                    }
                    case StackInstruction s -> {
                        Type type1, type2, type3, type4;
                        switch(s.opcode()){
                            case POP ->
                                    decStack(1);
                            case POP2 ->
                                    decStack(2);
                            case DUP ->
                                    pushStack(type1 = popStack()).pushStack(type1);
                            case DUP_X1 -> {
                                type1 = popStack();
                                type2 = popStack();
                                pushStack(type1).pushStack(type2).pushStack(type1);
                            }
                            case DUP_X2 -> {
                                type1 = popStack();
                                type2 = popStack();
                                type3 = popStack();
                                pushStack(type1).pushStack(type3).pushStack(type2).pushStack(type1);
                            }
                            case DUP2 -> {
                                type1 = popStack();
                                type2 = popStack();
                                pushStack(type2).pushStack(type1).pushStack(type2).pushStack(type1);
                            }
                            case DUP2_X1 -> {
                                type1 = popStack();
                                type2 = popStack();
                                type3 = popStack();
                                pushStack(type2).pushStack(type1).pushStack(type3).pushStack(type2).pushStack(type1);
                            }
                            case DUP2_X2 -> {
                                type1 = popStack();
                                type2 = popStack();
                                type3 = popStack();
                                type4 = popStack();
                                pushStack(type2).pushStack(type1).pushStack(type4).pushStack(type3).pushStack(type2).pushStack(type1);
                            }
                            case SWAP -> {
                                type1 = popStack();
                                type2 = popStack();
                                pushStack(type1);
                                pushStack(type2);
                            }
                            default -> throw new RuntimeException();
                        }
                    }

                    case ReturnInstruction r when r.typeKind()==TypeKind.VOID -> {}
                    case ReturnInstruction r -> decStack(r.typeKind().slotSize());

                    case StoreInstruction s when s.typeKind() == TypeKind.DOUBLE -> decStack(2).setLocal2(s.slot(), Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
                    case StoreInstruction s when s.typeKind() == TypeKind.LONG -> decStack(2).setLocal2(s.slot(), Type.LONG_TYPE, Type.LONG2_TYPE);
                    case StoreInstruction s -> setLocal(s.slot(), popStack());

                    case TableSwitchInstruction ts -> popStack();
                    case ThrowInstruction t -> popStack();
                    case TypeCheckInstruction tc -> decStack(1).pushStack(tc.type().asSymbol());
                    case DiscontinuedInstruction d -> throw new IllegalStateException(d.toString());
                    default -> throw new IllegalStateException();
                }
            }
            case PseudoInstruction _ -> {}
            case RuntimeInvisibleTypeAnnotationsAttribute _ -> {}
            case RuntimeVisibleTypeAnnotationsAttribute _ -> {}
            case CustomAttribute<?> _ -> {}
            case StackMapTableAttribute _ -> {}
        }
        if(ce instanceof Instruction)
            System.out.println("loc: " + locals + " stack: " + stack);
    }


    //Tries its best to reuse old saved locals field slots, only reuses if types exactly match
    public void savingLocals(ClassDesc cd, CodeBuilder cob, Runnable run) {
        record Saved(int slot, String name, ClassDesc cd) {
        }

        var saved = new ArrayList<Saved>();
        currentLocals((slot, tk, desc) -> {
            String name = LOCAL_PREFIX + localStore.size();
            localStore.add(new LocalStore(name, desc));
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
            stack.clear();
            locals.clear();
            for( var sl : tmp.stack())
                pushStack(Type.verificationType(sl));
            for( var local : tmp.locals())
                locals.add(Type.verificationType(local));
        }
    }

    public ClassDesc paramType(int slot) {
        return parameter_map.get(slot);
    }

    public interface LocalConsumer {
        void consume(int slot, TypeKind tk, ClassDesc desc);
    }

    public void currentLocals(LocalConsumer consumer) {
        int slot = 0;
        for (var entry : locals) {
            slot++;
            if (slot <= local_param_off) continue;
            if(entry.isCategory2_2nd())continue;
            consumer.consume(slot - smb.paramSlotOff + local_param_off - 1, TypeKind.from(entry.toCD()), entry.toCD());
        }

    }
}
