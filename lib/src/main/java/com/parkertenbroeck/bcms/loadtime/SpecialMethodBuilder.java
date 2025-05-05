package com.parkertenbroeck.bcms.loadtime;

import java.lang.classfile.CodeBuilder;

public interface SpecialMethodBuilder<T extends StateMachineBuilder<T>> {
    SpecialMethodHandler<T> build(T smb, CodeBuilder cob, Frame frame, StateBuilder sb);
}
