package dukes.build.color;

import dukes.build.CompiledClasses;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * Orchestrates the build-time colour packer: per class, {@link ColorScanner} finds the constant {@code Color}
 * fields and {@link PalettePacker} folds them into one decoded palette. The source keeps its named colours.
 */
public final class ColorPacker {

    /** Below this many constant colours the palette's decode loop costs more than it saves, so leave them be. */
    private static final int MIN_COLORS = 8;

    private ColorPacker() {
    }

    /** Folds the constant Color fields of every eligible loaded class; returns how many colours were packed. */
    public static int pack(CompiledClasses classes) {
        int total = 0;
        for (ClassNode owner : List.copyOf(classes.all())) {
            int packed = packClass(owner);
            if (packed > 0) {
                classes.markModified(owner.name);
                total += packed;
            }
        }
        return total;
    }

    private static int packClass(ClassNode owner) {
        MethodNode staticInit = staticInitializer(owner);
        if (staticInit == null) {
            return 0;
        }
        List<PackedColor> colors = ColorScanner.scan(owner, staticInit);
        if (colors.size() < MIN_COLORS) {
            return 0;
        }
        PalettePacker.pack(owner, staticInit, colors);
        return colors.size();
    }

    private static MethodNode staticInitializer(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals("<clinit>")) {
                return method;
            }
        }
        return null;
    }
}
