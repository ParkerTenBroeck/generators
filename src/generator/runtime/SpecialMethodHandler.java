package generator.runtime;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

public abstract class SpecialMethodHandler {
    public final Label handler_start;
    public final Label handler_resume;

    protected SpecialMethodHandler(StateMachineBuilder smb, CodeBuilder cob) {
        handler_start = cob.newLabel();
        handler_resume = cob.newLabel();
    }

    public abstract void buildHandler(StateMachineBuilder smb, CodeBuilder cob, Frame frame);

    public boolean removeCall() {
        return true;
    }

    public void insertShim(StateMachineBuilder smb, CodeBuilder cob){
        cob.goto_(handler_start).labelBinding(handler_resume);
    }

    public abstract ReplacementKind replacementKind();
}
