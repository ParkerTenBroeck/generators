package generators.loadtime;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.instruction.LineNumber;
import java.util.Arrays;

public record Frame(FrameTracker.Type[] locals, FrameTracker.Type[] stack, int bci, LineNumber line, FrameTracker.LocalVariableAnnotation[] local_annotations) {

    @Override
    public String toString() {
        return "Frame[l=" + Arrays.toString(locals)
                + ", s=" + Arrays.toString(stack)
                + ", bci=" + bci
                + ", line="+line
                + ", local_annotations=" + Arrays.toString(local_annotations)
                + "]";
    }

    public void save_locals(StateMachineBuilder smb, CodeBuilder cob, SavedStateTracker sst, int loc_off){
        int slot = 0;
        for (var entry : locals) {
            slot++;
            if (slot <= smb.paramSlotOff) continue;

            if(entry.tag() == FrameTracker.Type.TOP_TYPE.tag())continue;
            if (entry.isCategory2_2nd()) continue;

            sst.save_local(smb, cob, entry.toCD(), slot - smb.paramSlotOff + loc_off - 1);
        }
    }
    public void save_stack(StateMachineBuilder smb, CodeBuilder cob, SavedStateTracker sst, int stack_off) {
        for(int i = stack.length-1-stack_off; i >= 0; i --){
            if(stack[i].isCategory2_2nd())continue;
            sst.save_stack(smb, cob, stack[i].toCD());
        }
    }

    public SavedStateTracker save(StateMachineBuilder smb, CodeBuilder cob, SavedStateTracker sst, int loc_off, int stack_off) {
        save_locals(smb, cob, sst, loc_off);
        save_stack(smb, cob, sst, stack_off);
        return sst;
    }

    public SavedStateTracker save(StateMachineBuilder smb, CodeBuilder cob, int loc_off, int stack_off) {
        var sst = new SavedStateTracker();
        return save(smb, cob, sst, loc_off, stack_off);
    }
}
