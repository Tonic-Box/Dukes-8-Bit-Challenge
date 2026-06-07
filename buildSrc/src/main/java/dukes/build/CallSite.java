package dukes.build;

import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** A resolved call to inline: the method that contains it and the call instruction itself. */
record CallSite(MethodNode caller, MethodInsnNode instruction) { }
