package com.parkertenbroeck.bcsm.loadtime;

import java.lang.classfile.CodeBuilder;

public interface SpecialMethodHandler<T extends StateMachineBuilder> {

    void build_prelude(T smb, CodeBuilder cob, Frame frame);
    default boolean removeCall() {
        return true;
    }
    void build_inline(T smb, CodeBuilder cob, Frame frame);
    ReplacementKind replacementKind();
}
