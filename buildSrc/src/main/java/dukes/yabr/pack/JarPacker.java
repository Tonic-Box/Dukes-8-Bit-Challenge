package dukes.yabr.pack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Writes a runnable jar (a ZIP) by hand, compressing each entry with {@link OptimalDeflate} and falling back to
 * stored when deflate would not shrink it. The stock {@code JarOutputStream} always deflates with
 * {@code java.util.zip.Deflater}, which both loses to OptimalDeflate on the class bytes and re-deflates the
 * already-compressed Game blob into something slightly larger; this packer beats it on the former and avoids
 * the latter. The result is an ordinary jar the JVM reads with no special handling - a stored entry is returned
 * verbatim and a deflated entry inflates with the stock inflater.
 */
public final class JarPacker {

    private static final int LOCAL_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int END_SIGNATURE = 0x06054b50;
    private static final int METHOD_STORED = 0;
    private static final int METHOD_DEFLATED = 8;

    /** Fixed DOS date 1980-01-01 00:00, so the jar is byte-reproducible across builds. */
    private static final int DOS_TIME = 0;
    private static final int DOS_DATE = 0x21;

    private JarPacker() {
    }

    /** One jar member: its path within the jar and its uncompressed bytes. */
    public record Entry(String name, byte[] data) {
    }

    /** Per-entry result after choosing stored vs deflated, plus the local-header offset filled in while writing. */
    private static final class Packed {
        final byte[] name;
        final long crc;
        final int method;
        final byte[] payload;
        final int uncompressedSize;
        int localOffset;

        Packed(byte[] name, long crc, int method, byte[] payload, int uncompressedSize) {
            this.name = name;
            this.crc = crc;
            this.method = method;
            this.payload = payload;
            this.uncompressedSize = uncompressedSize;
        }
    }

    /** Writes {@code entries} to {@code jar} as an optimally-compressed ZIP, returning the file's byte size. */
    public static long write(File jar, List<Entry> entries) throws IOException {
        List<Packed> packed = new ArrayList<>();
        for (Entry entry : entries) {
            byte[] raw = entry.data();
            CRC32 crc = new CRC32();
            crc.update(raw);
            byte[] deflated = OptimalDeflate.compress(raw);
            boolean shrinks = deflated.length < raw.length;
            packed.add(new Packed(entry.name().getBytes(StandardCharsets.UTF_8), crc.getValue(),
                    shrinks ? METHOD_DEFLATED : METHOD_STORED, shrinks ? deflated : raw, raw.length));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Packed entry : packed) {
            entry.localOffset = out.size();
            writeLocalHeader(out, entry);
            out.writeBytes(entry.payload);
        }
        int centralOffset = out.size();
        for (Packed entry : packed) {
            writeCentralHeader(out, entry);
        }
        int centralSize = out.size() - centralOffset;
        writeEndRecord(out, packed.size(), centralSize, centralOffset);

        byte[] bytes = out.toByteArray();
        Files.write(jar.toPath(), bytes);
        return bytes.length;
    }

    private static void writeLocalHeader(ByteArrayOutputStream out, Packed entry) {
        writeInt(out, LOCAL_SIGNATURE);
        writeShort(out, entry.method == METHOD_DEFLATED ? 20 : 10);
        writeShort(out, 0);
        writeShort(out, entry.method);
        writeShort(out, DOS_TIME);
        writeShort(out, DOS_DATE);
        writeInt(out, (int) entry.crc);
        writeInt(out, entry.payload.length);
        writeInt(out, entry.uncompressedSize);
        writeShort(out, entry.name.length);
        writeShort(out, 0);
        out.writeBytes(entry.name);
    }

    private static void writeCentralHeader(ByteArrayOutputStream out, Packed entry) {
        writeInt(out, CENTRAL_SIGNATURE);
        writeShort(out, 20);
        writeShort(out, entry.method == METHOD_DEFLATED ? 20 : 10);
        writeShort(out, 0);
        writeShort(out, entry.method);
        writeShort(out, DOS_TIME);
        writeShort(out, DOS_DATE);
        writeInt(out, (int) entry.crc);
        writeInt(out, entry.payload.length);
        writeInt(out, entry.uncompressedSize);
        writeShort(out, entry.name.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, entry.localOffset);
        out.writeBytes(entry.name);
    }

    private static void writeEndRecord(ByteArrayOutputStream out, int count, int centralSize, int centralOffset) {
        writeInt(out, END_SIGNATURE);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, count);
        writeShort(out, count);
        writeInt(out, centralSize);
        writeInt(out, centralOffset);
        writeShort(out, 0);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }
}
