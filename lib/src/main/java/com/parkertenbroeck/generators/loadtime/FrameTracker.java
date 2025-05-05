package com.parkertenbroeck.generators.loadtime;


import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;

import static java.lang.constant.ConstantDescs.*;

public class FrameTracker {

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

    public static ArrayList<Frame> frames(StateMachineBuilder smb, CodeModel src_com) {
        var ft = new FrameTracker(smb, src_com);
        var frames = new ArrayList<Frame>();
        for(var coe : src_com){
            if(coe instanceof Instruction) {
                frames.add(new Frame(ft.locals(), ft.stack(), ft.bci, ft.current_line_number, ft.local_annotations()));
            }
            ft.encounter(coe);
        }
        frames.add(new Frame(ft.locals(), ft.stack(), ft.bci, null, ft.local_annotations()));

        return frames;
    }

    private LocalVariableAnnotation[] local_annotations() {
        return this.activeAnnotations.toArray(LocalVariableAnnotation[]::new);
    }

    public Type[] locals() {
        return locals.toArray(Type[]::new);
    }
    public Type[] stack() {
        return stack.toArray(Type[]::new);
    }

    public record Type(int tag, ClassDesc sym, int bci) {

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

        static final Type STRING_TYPE = referenceType(CD_String);
        static final Type METHOD_HANDLE_TYPE = referenceType(CD_MethodHandle);
        static final Type METHOD_TYPE = referenceType(CD_MethodType);

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
                case ITEM_LONG_2ND -> "long2";
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

        static Type uninitializedType(ClassDesc sym, int bci) {
            return new Type(ITEM_UNINITIALIZED, sym, bci);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Type(int tag, ClassDesc sym, int bci))
                    && tag == this.tag && bci == this.bci && Objects.equals(this.sym, sym);
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


        static Type verificationType(StackMapFrameInfo.VerificationTypeInfo v, FrameTracker t){
            return switch (v){
                case StackMapFrameInfo.ObjectVerificationTypeInfo o ->
                        Type.referenceType(o.classSymbol());
                case StackMapFrameInfo.SimpleVerificationTypeInfo s ->
                        Type.simpleType(s.tag());
                case StackMapFrameInfo.UninitializedVerificationTypeInfo u ->
                        Type.uninitializedType(null, t.bciMap.get(u.newTarget()));
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
    }


    final ArrayList<Type> stack = new ArrayList<>();
    final ArrayList<Type> locals = new ArrayList<>();
    final StateMachineBuilder smb;

    LineNumber current_line_number = null;
    int bci = 0;
    HashMap<Label, StackMapFrameInfo> stackMapFrames = new HashMap<>();
    HashMap<Label, Integer> bciMap = new HashMap<>();

    public record LocalVariableAnnotation(Annotation annotation, int slot){}
    final HashSet<LocalVariableAnnotation> activeAnnotations = new HashSet<>();
    final HashMap<Label, List<LocalVariableAnnotation>> annotationStartMap = new HashMap<>();
    final HashMap<Label, List<LocalVariableAnnotation>> annotationEndMap = new HashMap<>();


    FrameTracker(StateMachineBuilder smb, CodeModel com) {
        this.smb = smb;
        int offset = 0;

        for (var param : smb.params) {
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

        int bci = 0;
        for(var ce : com){
            if(ce instanceof Instruction i)
                bci += i.sizeInBytes();
            if(ce instanceof Label l)
                bciMap.put(l, bci);
            if(ce instanceof RuntimeVisibleTypeAnnotationsAttribute tas){
                for(var ta : tas.annotations()){
                    switch(ta.targetInfo()){
                        case TypeAnnotation.CatchTarget ct -> {}
                        case TypeAnnotation.EmptyTarget et -> {}
                        case TypeAnnotation.FormalParameterTarget fpt -> {}
                        case TypeAnnotation.LocalVarTarget lvt -> {
                            for(var el : lvt.table()){
                                var lva = new LocalVariableAnnotation(ta.annotation(), el.index());
                                annotationStartMap
                                        .computeIfAbsent(el.startLabel(), k -> new ArrayList<>())
                                        .add(lva);
                                annotationEndMap
                                        .computeIfAbsent(el.endLabel(), k -> new ArrayList<>())
                                        .add(lva);
                            }
                        }
                        case TypeAnnotation.OffsetTarget ot -> {}
                        case TypeAnnotation.SupertypeTarget stt -> {}
                        case TypeAnnotation.ThrowsTarget tt -> {}
                        case TypeAnnotation.TypeArgumentTarget tat -> {}
                        case TypeAnnotation.TypeParameterBoundTarget tpbt -> {}
                        case TypeAnnotation.TypeParameterTarget tpt -> {}
                    }
                }
            }
        }

        for (var attr : com.findAttributes(Attributes.stackMapTable())) {
            for (var smfi : attr.entries()) {
                stackMapFrames.put(smfi.target(), smfi);
            }
        }
    }

    FrameTracker pushStack(ClassDesc desc) {
        if (desc == CD_long)   return pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
        if (desc == CD_double) return pushStack(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
        return desc == CD_void ? this
                : pushStack(
                desc.isPrimitive()
                        ? (desc == CD_float ? Type.FLOAT_TYPE : Type.INTEGER_TYPE)
                        : Type.referenceType(desc));
    }

    FrameTracker pushStack(Type... types) {
        stack.addAll(Arrays.asList(types));
        return this;
    }

    FrameTracker pushStack(Type type) {
        stack.add(type);
        return this;
    }

    FrameTracker pushStack(Type type1, Type type2) {
        stack.add(type1);
        stack.add(type2);
        return this;
    }

    Type popStack() {
        return stack.removeLast();
    }

    FrameTracker decStack(int size) {
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
                        popStack();// array
                        popStack();//index
                        var arr = popStack();//type
                        pushStack(arr.toCD().componentType());
                    }
                    case ArrayStoreInstruction as -> decStack(2 + as.typeKind().slotSize());
                    case BranchInstruction b when b.opcode() == Opcode.GOTO || b.opcode() == Opcode.GOTO_W -> {}
                    case BranchInstruction b -> popStack();
                    case ConstantInstruction c when ins.opcode() == Opcode.ACONST_NULL -> pushStack(Type.NULL_TYPE);
                    case ConstantInstruction c -> {
                        switch(c.constantValue()){
                            case Double _ -> pushStack(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
                            case Float _ -> pushStack(Type.FLOAT_TYPE);
                            case Integer _ -> pushStack(Type.INTEGER_TYPE);
                            case Long _ -> pushStack(Type.LONG_TYPE, Type.LONG2_TYPE);
                            case String _ -> pushStack(Type.STRING_TYPE);
                            case ClassDesc desc -> pushStack(desc);
                            case DynamicConstantDesc dynamicConstantDesc -> pushStack(dynamicConstantDesc.constantType());
                            case MethodHandleDesc _ -> pushStack(Type.METHOD_HANDLE_TYPE);
                            case MethodTypeDesc _ -> pushStack(Type.METHOD_TYPE);
                        }
                    }
                    case ConvertInstruction c -> decStack(c.fromType().slotSize()).pushStack(c.toType().upperBound());
                    case FieldInstruction f -> {
                        switch(f.opcode()){
                            case GETFIELD -> decStack(1).pushStack(f.typeSymbol());
                            case GETSTATIC -> pushStack(f.typeSymbol());
                            case PUTFIELD -> decStack(1 + TypeKind.from(f.typeSymbol()).slotSize());
                            case PUTSTATIC -> decStack(TypeKind.from(f.typeSymbol()).slotSize());
                        }
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
                    case InvokeInstruction ii when ii.opcode() == Opcode.INVOKESPECIAL && ii.name().equalsString(INIT_NAME) -> {
                        for(var param : ii.typeSymbol().parameterArray())
                            decStack(TypeKind.from(param).slotSize());
                        var ty = popStack();
                        if(ty.tag == ITEM_UNINITIALIZED){
                            if(ty.sym!=null&&!ty.sym.equals(ii.owner().asSymbol()))
                                throw new RuntimeException();
                            var init_type = ii.owner().asSymbol();
                            for(int i = 0; i < stack.size(); i ++){
                                if(stack.get(i).bci==ty.bci&&stack.get(i).tag==ITEM_UNINITIALIZED){
                                    stack.set(i, Type.referenceType(init_type));
                                }
                            }
                            for(int i = 0; i < locals.size(); i ++){
                                if(locals.get(i).bci==ty.bci&&locals.get(i).tag==ITEM_UNINITIALIZED){
                                    locals.set(i, Type.referenceType(init_type));
                                }
                            }
                        }else{
                            throw new RuntimeException();
                        }
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
                    case NewObjectInstruction no -> pushStack(Type.uninitializedType(no.className().asSymbol(), bci));
                    case NewPrimitiveArrayInstruction npa -> decStack(1).pushStack(npa.typeKind().upperBound().arrayType());
                    case NewReferenceArrayInstruction nra -> decStack(1).pushStack(nra.componentType().asSymbol().arrayType());
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
                        switch(s.opcode()){
                            case POP ->
                                    decStack(1);
                            case POP2 ->
                                    decStack(2);
                            case DUP -> {
                                var type1 = popStack();
                                pushStack(type1, type1);
                            }
                            case DUP_X1 -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                pushStack(type1, type2, type1);
                            }
                            case DUP_X2 -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                var type3 = popStack();
                                pushStack(type1, type3, type2, type1);
                            }
                            case DUP2 -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                pushStack(type2, type1, type2, type1);
                            }
                            case DUP2_X1 -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                var type3 = popStack();
                                pushStack(type2, type1, type3, type2, type1);
                            }
                            case DUP2_X2 -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                var type3 = popStack();
                                var type4 = popStack();
                                pushStack(type2, type1, type4, type3, type2, type1);
                            }
                            case SWAP -> {
                                var type1 = popStack();
                                var type2 = popStack();
                                pushStack(type1, type2);
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
                bci += ins.sizeInBytes();
            }
            case PseudoInstruction p -> {
                switch(p){
                    case LineNumber ln -> current_line_number = ln;
                    case CharacterRange cr -> {}
                    case ExceptionCatch ec -> {}
                    case LabelTarget lt -> {}
                    case LocalVariable lv -> {}
                    case LocalVariableType lvt -> {}
                    default -> {}
                }
            }
            case RuntimeInvisibleTypeAnnotationsAttribute _ -> {}
            case RuntimeVisibleTypeAnnotationsAttribute _ -> {}
            case CustomAttribute<?> _ -> {}
            case StackMapTableAttribute _ -> {}
        }
    }

    public void encounterLabel(Label l) {
        if(annotationStartMap.get(l) instanceof ArrayList<LocalVariableAnnotation> list)
            activeAnnotations.addAll(list);

        if(annotationEndMap.get(l) instanceof ArrayList<LocalVariableAnnotation> list)
            activeAnnotations.removeAll(list);

        var tmp = stackMapFrames.get(l);
        if (tmp != null) {
            stack.clear();
            locals.clear();
            for( var sl : tmp.stack())
                pushStack(Type.verificationType(sl, this));

            for( var sl : tmp.locals())
                locals.add(Type.verificationType(sl, this));
        }
    }
}
