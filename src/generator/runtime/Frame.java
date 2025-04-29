package generator.runtime;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.util.Arrays;

public record Frame(FrameTracker.Type[] locals, FrameTracker.Type[] stack) {

    @Override
    public String toString() {
        return "Frame[l =" + Arrays.toString(locals) + ", s = " + Arrays.toString(stack) + "]";
    }

    public SavedStateTracker save(StateMachineBuilder smb, CodeBuilder cob, int loc_off, int stack_off) {
        var sst = new SavedStateTracker();
        int slot = 0;
        for (var entry : locals) {
            slot++;
            if (slot <= loc_off) continue;
            if (entry.isCategory2_2nd()) continue;

            sst.save_local(smb, cob, entry.toCD(), slot - smb.paramSlotOff + loc_off - 1);
        }

        return sst;
    }
}
