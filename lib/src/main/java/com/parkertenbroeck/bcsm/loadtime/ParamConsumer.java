package com.parkertenbroeck.bcsm.loadtime;

import java.lang.constant.ClassDesc;

public interface ParamConsumer {
    void consume(String param, int slot, ClassDesc type);
}
