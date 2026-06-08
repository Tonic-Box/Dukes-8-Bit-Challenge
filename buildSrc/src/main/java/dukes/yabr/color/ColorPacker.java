package dukes.yabr.color;

import com.tonic.analysis.CodeWriter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import dukes.yabr.CompiledClasses;

import java.util.List;

/**
 * Orchestrates the colour packer: per class, {@link ColorScanner} finds the constant {@code Color} fields and
 * {@link PalettePacker} folds them into one decoded palette. The source keeps its named colours.
 */
public final class ColorPacker {

    /** Below this many constant colours the palette's decode loop costs more than it saves, so leave them be. */
    private static final int MIN_COLORS = 8;

    private ColorPacker() {
    }

    /** Folds the constant Color fields of every eligible loaded class; returns how many colours were packed. */
    public static int pack(CompiledClasses classes) throws Exception {
        int total = 0;
        for (ClassFile owner : List.copyOf(classes.all())) {
            int packed = packClass(owner);
            if (packed > 0) {
                classes.markModified(owner.getClassName());
                total += packed;
            }
        }
        return total;
    }

    private static int packClass(ClassFile owner) throws Exception {
        MethodEntry staticInit = staticInitializer(owner);
        if (staticInit == null || staticInit.getCodeAttribute() == null) {
            return 0;
        }
        CodeWriter writer = new CodeWriter(staticInit);
        List<PackedColor> colors = ColorScanner.scan(owner, writer);
        if (colors.size() < MIN_COLORS) {
            return 0;
        }
        PalettePacker.pack(owner, writer, colors);
        return colors.size();
    }

    private static MethodEntry staticInitializer(ClassFile owner) {
        for (MethodEntry method : owner.getMethods()) {
            if (method.getName().equals("<clinit>")) {
                return method;
            }
        }
        return null;
    }
}
