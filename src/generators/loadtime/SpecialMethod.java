package generators.loadtime;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public record SpecialMethod(ClassDesc owner, String name, MethodTypeDesc desc) {
}
