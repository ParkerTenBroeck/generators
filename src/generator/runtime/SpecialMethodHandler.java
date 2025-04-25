package generator.runtime;

import java.lang.classfile.CodeBuilder;

public interface SpecialMethodHandler {
    void handle(StateMachineBuilder smb, CodeBuilder cob);

    default boolean removeCall() {
        return true;
    }

    ReplacementKind replacementKind();
}
