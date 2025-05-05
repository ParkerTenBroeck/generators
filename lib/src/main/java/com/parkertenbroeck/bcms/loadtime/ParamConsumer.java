package com.parkertenbroeck.bcms.loadtime;

import java.lang.constant.ClassDesc;

public interface ParamConsumer {
    void consume(String param, int slot, ClassDesc type);
}
