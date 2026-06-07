package dukes.build.color;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * A constant {@code new Color(...)} stored into a static field: its RGBA value plus the {@code NEW} and
 * {@code PUTSTATIC} bytecode landmarks, so the construction can be cut out once it is folded into the palette.
 */
record PackedColor(String field, int red, int green, int blue, int alpha, AbstractInsnNode construction, AbstractInsnNode store) {
    static final String TYPE = "java/awt/Color";
    static final String DESCRIPTOR = "Ljava/awt/Color;";
}
