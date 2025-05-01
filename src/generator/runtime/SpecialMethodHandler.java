package generator.runtime;

import java.lang.classfile.CodeBuilder;

public interface SpecialMethodHandler {

    void build_prelude(StateMachineBuilder smb, CodeBuilder cob, Frame frame);
    default boolean removeCall() {
        return true;
    }
    void build_inline(StateMachineBuilder smb, CodeBuilder cob, Frame frame);
    ReplacementKind replacementKind();
}
