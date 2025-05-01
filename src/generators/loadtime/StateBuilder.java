package generators.loadtime;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;

public class StateBuilder {
    public record State(Label label, int id){
        public void bind(CodeBuilder cob){
            cob.labelBinding(label);
        }

        public void setState(StateMachineBuilder smb, CodeBuilder cob){
            cob.aload(0).loadConstant(id).putfield(smb.CD_this, StateMachineBuilder.STATE_NAME, ConstantDescs.CD_int);
        }
    }

    private final ArrayList<State> states = new ArrayList<>();

    public State create(CodeBuilder cob){
        var state = new State(cob.newLabel(), states.size());
        states.add(state);
        return state;
    }

    public void buildSwitch(CodeBuilder cob, Label default_label){
        cob.lookupswitch(default_label, states.stream().map(l -> SwitchCase.of(l.id, l.label)).toList());
    }
}
