package generator.runtime;

import java.lang.classfile.*;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo.TOP;

public class LocalTracker {

    record LocalStore(String name, ClassDesc cd) {
    }

    HashMap<Integer, ClassDesc> parameter_map = new HashMap<>();

    HashMap<Integer, TypeKind> localVarTypes = new HashMap<>();
    HashMap<Label, StackMapFrameInfo> stackMapFrames = new HashMap<>();
    StackMapFrameInfo currentFrame;
    ArrayList<LocalStore> localStore = new ArrayList<>();
    final int local_param_off;


    LocalTracker(StateMachineBuilder smb, CodeModel com, int local_param_off) {
        this.local_param_off = local_param_off;
        int offset = 0;

        for (var param : smb.params) {
            parameter_map.put(offset, param);
            offset += TypeKind.from(param).slotSize();
        }

        for (var attr : com.findAttributes(Attributes.stackMapTable())) {
            var entries = new ArrayList<StackMapFrameInfo>();
            for (var smfi : attr.entries()) {
                var locals = new ArrayList<>(smfi.locals());
                for (int i = 0; i < smb.params.length; i++)
                    locals.removeFirst();
//                locals.addFirst(StackMapFrameInfo.ObjectVerificationTypeInfo.of(smb.CD_this));
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
                name = StateMachineBuilder.LOCAL_PREFIX + localStore.size();
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

    public interface LocalConsumer {
        void consume(int slot, TypeKind tk, ClassDesc desc);
    }

    public void currentLocals(LocalConsumer consumer) {
        var slot = local_param_off;
        if (currentFrame != null) {
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
        }else{
            for (var entry : localVarTypes.entrySet()) {
                if (entry.getKey() < slot) continue;
                ClassDesc cd = entry.getValue().upperBound();
                consumer.consume(entry.getKey(), TypeKind.from(cd), cd);
            }
        }

    }
}
