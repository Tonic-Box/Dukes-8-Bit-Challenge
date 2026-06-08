package dukes.yabr.color;

import com.tonic.analysis.instruction.Instruction;

/**
 * A constant {@code new Color(...)} stored into a static field: its RGBA value plus the {@code NEW} and
 * {@code PUTSTATIC} instruction handles, so the construction can be cut out once it is folded into the palette.
 */
record PackedColor(String field, int red, int green, int blue, int alpha, Instruction construction, Instruction store) {
    static final String TYPE = "java/awt/Color";
    static final String DESCRIPTOR = "Ljava/awt/Color;";
}
