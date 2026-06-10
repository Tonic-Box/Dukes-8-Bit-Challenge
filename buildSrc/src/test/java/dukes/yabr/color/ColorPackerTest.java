package dukes.yabr.color;

import dukes.yabr.CompiledClasses;
import dukes.yabr.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ColorPacker} rewrites inline {@code new Color(...)} constants into one load-decoded palette (only above its
 * {@code MIN_COLORS} threshold). The decode is verified end to end: the packed class is written out and loaded
 * normally so its {@code <clinit>} runs, and every resulting field value must equal the original, alpha included.
 */
class ColorPackerTest {

    @Test
    void packedPaletteDecodesToTheOriginalColors(@TempDir Path dir) throws Exception {
        Map<String, Color> expected = new LinkedHashMap<>();
        expected.put("RED", new Color(200, 50, 50));
        expected.put("BLUE", new Color(30, 40, 200));
        expected.put("GREEN", new Color(20, 180, 90));
        expected.put("YELLOW", new Color(240, 230, 40));
        expected.put("PURPLE", new Color(150, 60, 200));
        expected.put("GRAY", new Color(128, 128, 128));
        expected.put("DARK", new Color(5, 5, 8));
        expected.put("TRANSLUCENT", new Color(10, 20, 30, 120));

        CompiledClasses classes = Fixtures.compile(dir, Map.of("Sample", fixtureSource(expected)));

        int packed = ColorPacker.pack(classes);
        assertEquals(expected.size(), packed, "every Color constant should be packed");

        Path out = Files.createDirectories(dir.resolve("packed"));
        Files.write(out.resolve("Sample.class"), classes.get("Sample").write());

        // Platform parent so java.awt.Color resolves but the test's own classpath stays invisible. The named
        // fields are folded into the decoded $PALETTE array, so verify that (order-independent: colours are distinct).
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {out.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> sample = Class.forName("Sample", true, loader);
            Field paletteField = sample.getDeclaredField("$PALETTE");
            paletteField.setAccessible(true);
            Color[] palette = (Color[]) paletteField.get(null);
            assertEquals(new HashSet<>(expected.values()), new HashSet<>(Arrays.asList(palette)),
                    "decoded palette colours");
        }
    }

    private static String fixtureSource(Map<String, Color> colors) {
        StringBuilder source = new StringBuilder("import java.awt.Color;\npublic class Sample {\n");
        colors.forEach((name, c) -> {
            source.append("    static final Color ").append(name).append(" = new Color(")
                    .append(c.getRed()).append(", ").append(c.getGreen()).append(", ").append(c.getBlue());
            if (c.getAlpha() != 255) {
                source.append(", ").append(c.getAlpha());
            }
            source.append(");\n");
        });
        return source.append("}\n").toString();
    }
}
