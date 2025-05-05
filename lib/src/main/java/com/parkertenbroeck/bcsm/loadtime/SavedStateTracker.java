package com.parkertenbroeck.bcsm.loadtime;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;

public class SavedStateTracker {
    private final ArrayList<SavedState> saved = new ArrayList<>();

    public sealed interface SavedState{
        String name();
    }
    public record StackState(String name, ClassDesc desc) implements SavedState{

    }
    public record LocalState(String name, ClassDesc desc, int slot) implements SavedState{

    }

    private String get_name(StateMachineBuilder<?> smb, ClassDesc desc){
        var value = smb.lstate.stream() // find unused state
                .filter(v -> saved.stream().noneMatch(s -> s.name().equals(v.name())))
                .filter(v -> v.cd().equals(desc)).findFirst();
        if(value.isPresent())
            return value.get().name();
        var name = StateMachineBuilder.LOCAL_PREFIX+smb.lstate.size();
        smb.lstate.add(new StateMachineBuilder.LState(name, desc));
        return name;
    }

    public SavedState save_stack(StateMachineBuilder<?> smb, CodeBuilder cob, ClassDesc desc){
        var name = get_name(smb, desc);

        if(TypeKind.from(desc).slotSize()==2){
            cob.aload(0).dup_x2().pop().putfield(smb.CD_this, name, desc);
        }else{
            cob.aload(0).swap().putfield(smb.CD_this, name, desc);
        }

        var s = new StackState(name, desc);
        saved.add(s);
        return s;
    }

    public SavedState save_local(StateMachineBuilder<?> smb, CodeBuilder cob, ClassDesc desc, int slot){
        var name = get_name(smb, desc);

        cob.aload(0).loadLocal(TypeKind.from(desc), slot).putfield(smb.CD_this, name, desc);

        var s = new LocalState(name, desc, slot);
        saved.add(s);
        return s;
    }


    public LocalState load_param(int slot) {
        for(var saved : saved){
            if (saved instanceof LocalState(var name, var desc, int s) && s == slot) {
                return (LocalState) saved;
            }
        }
        throw new RuntimeException();
    }

    public SavedStateTracker restore(StateMachineBuilder<?> smb, CodeBuilder cob, SavedState s){
        if(!saved.remove(s))throw new IllegalStateException();
        switch(s){
            case LocalState(var name, var desc, int slot) ->
                    cob.aload(0).getfield(smb.CD_this, name, desc).storeLocal(TypeKind.from(desc), slot);
            case StackState(var name, var desc) ->
                    cob.aload(0).getfield(smb.CD_this, name, desc);
        }
        return this;
    }

    public void restore_stack(StateMachineBuilder<?> smb, CodeBuilder cob){
        for(int i = saved.size()-1; i >= 0; i --){
            if(saved.get(i) instanceof StackState)
                restore(smb, cob, saved.get(i));
        }
    }

    public void restore_locals(StateMachineBuilder<?> smb, CodeBuilder cob){
        for(int i = saved.size()-1; i >= 0; i --){
            if(saved.get(i) instanceof StackState)
                restore(smb, cob, saved.get(i));
        }
    }

    public void restore_all(StateMachineBuilder<?> smb, CodeBuilder cob) {
        while(!saved.isEmpty())
            restore(smb, cob, saved.getLast());
    }
}
