package com.parkertenbroeck.bcsm.loadtime.future;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
public @interface Cancellation {
    String value() default "cancel";
}
