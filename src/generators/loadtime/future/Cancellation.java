package generators.loadtime.future;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE})
public @interface Cancellation {
    String value() default "cancel";
    Class<?> param() default void.class;
    Class<?> ret() default void.class;
    Class<?> owner() default void.class;
}
