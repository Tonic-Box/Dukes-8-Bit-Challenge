package dukes.yabr.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link JarPacker} hand-writes the runnable jar's ZIP container. These tests confirm the output is a valid archive
 * the JDK's own {@link ZipFile} reads back entry-for-entry, and that the pack-or-store decision is correct -
 * incompressible payloads (like the already-compressed game blob) must be stored verbatim, not re-deflated.
 */
class JarPackerTest {

    @Test
    void packedJarReadsBackEntryForEntry(@TempDir File dir) throws Exception {
        Map<String, byte[]> expected = new LinkedHashMap<>();
        expected.put("Main.class", "a small stored entry".getBytes());
        expected.put("nested/dir/Resource", filled((byte) 'Z', 20_000));
        byte[] random = new byte[8_000];
        new Random(7).nextBytes(random);
        expected.put("data.bin", random);
        expected.put("empty", new byte[0]);

        List<JarPacker.Entry> entries = new ArrayList<>();
        expected.forEach((name, data) -> entries.add(new JarPacker.Entry(name, data)));

        File jar = new File(dir, "out.jar");
        JarPacker.write(jar, entries);

        try (ZipFile zip = new ZipFile(jar)) {
            assertEquals(expected.size(), zip.size(), "entry count");
            for (Map.Entry<String, byte[]> e : expected.entrySet()) {
                ZipEntry entry = zip.getEntry(e.getKey());
                assertNotNull(entry, "missing entry " + e.getKey());
                try (var in = zip.getInputStream(entry)) {
                    assertArrayEquals(e.getValue(), in.readAllBytes(), "content mismatch for " + e.getKey());
                }
            }
        }
    }

    @Test
    void incompressiblePayloadIsStoredNotDeflated(@TempDir File dir) throws Exception {
        byte[] random = new byte[4_096];
        new Random(99).nextBytes(random);

        File jar = new File(dir, "stored.jar");
        JarPacker.write(jar, List.of(new JarPacker.Entry("blob", random)));

        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("blob");
            assertEquals(ZipEntry.STORED, entry.getMethod(), "incompressible entry should be STORED");
            try (var in = zip.getInputStream(entry)) {
                assertArrayEquals(random, in.readAllBytes());
            }
        }
    }

    @Test
    void compressiblePayloadIsDeflated(@TempDir File dir) throws Exception {
        byte[] compressible = filled((byte) 'A', 20_000);

        File jar = new File(dir, "deflated.jar");
        JarPacker.write(jar, List.of(new JarPacker.Entry("runs", compressible)));

        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("runs");
            assertEquals(ZipEntry.DEFLATED, entry.getMethod(), "highly compressible entry should be DEFLATED");
            try (var in = zip.getInputStream(entry)) {
                assertArrayEquals(compressible, in.readAllBytes());
            }
        }
    }

    private static byte[] filled(byte value, int count) {
        byte[] out = new byte[count];
        Arrays.fill(out, value);
        return out;
    }
}
