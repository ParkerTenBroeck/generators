package com.parkertenbroeck.bcms.loadtime.future;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE})
public @interface Cancellation {
    String value() default "cancel";
}
