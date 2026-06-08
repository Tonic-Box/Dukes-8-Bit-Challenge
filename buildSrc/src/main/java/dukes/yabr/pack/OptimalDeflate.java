package dukes.yabr.pack;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * A from-scratch DEFLATE encoder that beats {@code java.util.zip.Deflater} at the same format, so the output
 * still inflates with a stock {@link java.util.zip.Inflater}. It applies the two ideas behind Zopfli: a
 * shortest-path optimal LZ77 parse whose cost model is iterated against the resulting Huffman code lengths,
 * and adaptive block splitting so regions with different statistics get their own Huffman trees. Build-time
 * only; nothing here ships.
 *
 * <p>A token is either a literal byte (0..255) or a back-reference packed as {@code -((length << 16) | distance)}.
 */
final class OptimalDeflate {

    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;
    private static final int WINDOW = 32768;
    private static final int MAX_CHAIN = 1 << 28;
    private static final int LENGTH_LIMIT = 15;

    private static final int[] LENGTH_BASE = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43,
            51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
    private static final int[] LENGTH_EXTRA = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4,
            4, 4, 4, 5, 5, 5, 5, 0};
    private static final int[] DISTANCE_BASE = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257,
            385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
    private static final int[] DISTANCE_EXTRA = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9,
            9, 10, 10, 11, 11, 12, 12, 13, 13};
    private static final int[] CODE_LENGTH_ORDER = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
    private static final int[] LENGTH_CODE = new int[MAX_MATCH + 1];

    static {
        for (int code = 0; code < 29; code++) {
            int upper = (code < 28) ? LENGTH_BASE[code + 1] : MAX_MATCH + 1;
            for (int length = LENGTH_BASE[code]; length < upper; length++) {
                LENGTH_CODE[length] = code;
            }
        }
        LENGTH_CODE[MAX_MATCH] = 28;
    }

    private static int distanceCode(int distance) {
        for (int code = 29; code >= 0; code--) {
            if (distance >= DISTANCE_BASE[code]) {
                return code;
            }
        }
        return 0;
    }

    private final byte[] data;
    private final int length;
    private final int[] hashHead = new int[65536];
    private final int[] hashPrev;
    private int[] matchLength;
    private int[][] matchDistanceByLength;

    private OptimalDeflate(byte[] data) {
        this.data = data;
        this.length = data.length;
        this.hashPrev = new int[Math.max(1, length)];
        Arrays.fill(hashHead, -1);
    }

    /** Compresses {@code data} to a raw DEFLATE stream (no zlib/gzip wrapper). */
    public static byte[] compress(byte[] data) {
        return new OptimalDeflate(data).run();
    }

    private int hash(int position) {
        return ((data[position] & 0xff) << 10 ^ (data[position + 1] & 0xff) << 5 ^ (data[position + 2] & 0xff)) & 0xffff;
    }

    /**
     * For every position records its longest match and, per achievable length, the nearest distance reaching
     * it. Found once and reused across cost-model iterations since the matches never change, only their cost.
     */
    private void findMatches() {
        matchLength = new int[length];
        matchDistanceByLength = new int[length][];
        for (int position = 0; position < length; position++) {
            int longest = MIN_MATCH - 1;
            if (position + MIN_MATCH <= length) {
                int[] distanceByLength = new int[MAX_MATCH + 1];
                int candidate = hashHead[hash(position)];
                int remaining = MAX_CHAIN;
                int max = Math.min(MAX_MATCH, length - position);
                while (candidate >= 0 && (position - candidate) <= WINDOW && remaining-- > 0) {
                    int matched = 0;
                    while (matched < max && data[position + matched] == data[candidate + matched]) {
                        matched++;
                    }
                    if (matched >= MIN_MATCH && matched > longest) {
                        for (int len = longest + 1; len <= matched; len++) {
                            distanceByLength[len] = position - candidate;
                        }
                        longest = matched;
                        if (matched >= max) {
                            break;
                        }
                    }
                    candidate = hashPrev[candidate];
                }
                if (longest >= MIN_MATCH) {
                    matchDistanceByLength[position] = distanceByLength;
                }
            }
            matchLength[position] = longest;
            if (position + MIN_MATCH <= length) {
                int h = hash(position);
                hashPrev[position] = hashHead[h];
                hashHead[h] = position;
            }
        }
    }

    /** Length-limited Huffman code lengths for the given symbol frequencies (0 for unused symbols). */
    private static int[] huffmanCodeLengths(int[] frequency, int maxSymbol, int limit) {
        int[] result = new int[maxSymbol + 1];
        List<Integer> symbols = new ArrayList<>();
        for (int i = 0; i <= maxSymbol; i++) {
            if (frequency[i] > 0) {
                symbols.add(i);
            }
        }
        int count = symbols.size();
        if (count == 0) {
            return result;
        }
        if (count == 1) {
            result[symbols.getFirst()] = 1;
            return result;
        }
        int capacity = 2 * count;
        long[] weight = new long[capacity];
        int[] left = new int[capacity];
        int[] right = new int[capacity];
        int[] leafSymbol = new int[capacity];
        int next = 0;
        PriorityQueue<Integer> queue = new PriorityQueue<>(Comparator.comparingLong(a -> weight[a]));
        for (int symbol : symbols) {
            weight[next] = frequency[symbol];
            left[next] = -1;
            right[next] = -1;
            leafSymbol[next] = symbol;
            queue.add(next++);
        }
        while (queue.size() > 1) {
            int a = Objects.requireNonNull(queue.poll());
            int b = Objects.requireNonNull(queue.poll());
            weight[next] = weight[a] + weight[b];
            left[next] = a;
            right[next] = b;
            leafSymbol[next] = -1;
            queue.add(next++);
        }
        int[] natural = new int[maxSymbol + 1];
        ArrayDeque<long[]> stack = new ArrayDeque<>();
        stack.push(new long[]{Objects.requireNonNull(queue.poll()), 0});
        int deepest = 0;
        while (!stack.isEmpty()) {
            long[] entry = stack.pop();
            int node = (int) entry[0];
            int depth = (int) entry[1];
            if (left[node] == -1) {
                natural[leafSymbol[node]] = Math.max(depth, 1);
                deepest = Math.max(deepest, natural[leafSymbol[node]]);
            } else {
                stack.push(new long[]{left[node], depth + 1});
                stack.push(new long[]{right[node], depth + 1});
            }
        }
        if (deepest <= limit) {
            return natural;
        }
        return limitLengths(natural, frequency, symbols, maxSymbol, limit);
    }

    /** Redistributes over-long codes down to {@code limit} while preserving a complete prefix code. */
    private static int[] limitLengths(int[] natural, int[] frequency, List<Integer> symbols, int maxSymbol, int limit) {
        int[] countAtLength = new int[limit + 1];
        int overflow = 0;
        for (int i = 0; i <= maxSymbol; i++) {
            if (natural[i] > 0) {
                int l = natural[i];
                if (l > limit) {
                    l = limit;
                    overflow++;
                }
                countAtLength[l]++;
            }
        }
        while (overflow > 0) {
            int bits = limit - 1;
            while (countAtLength[bits] == 0) {
                bits--;
            }
            countAtLength[bits]--;
            countAtLength[bits + 1] += 2;
            countAtLength[limit]--;
            overflow -= 2;
        }
        Integer[] order = symbols.toArray(new Integer[0]);
        Arrays.sort(order, (a, b) -> {
            int c = Integer.compare(frequency[a], frequency[b]);
            return c != 0 ? c : Integer.compare(b, a);
        });
        int[] result = new int[maxSymbol + 1];
        int index = 0;
        for (int l = limit; l >= 1; l--) {
            for (int k = countAtLength[l]; k > 0; k--) {
                result[order[index++]] = l;
            }
        }
        return result;
    }

    /** Canonical Huffman codes (most-significant-bit-first values) for the given code lengths. */
    private static int[] canonicalCodes(int[] codeLength, int maxSymbol) {
        int[] countAtLength = new int[16];
        for (int i = 0; i <= maxSymbol; i++) {
            if (codeLength[i] > 0) {
                countAtLength[codeLength[i]]++;
            }
        }
        int[] nextCode = new int[16];
        int code = 0;
        for (int bits = 1; bits <= 15; bits++) {
            code = (code + countAtLength[bits - 1]) << 1;
            nextCode[bits] = code;
        }
        int[] codes = new int[maxSymbol + 1];
        for (int i = 0; i <= maxSymbol; i++) {
            if (codeLength[i] > 0) {
                codes[i] = nextCode[codeLength[i]]++;
            }
        }
        return codes;
    }

    /** Least-significant-bit-first bit sink, as DEFLATE requires for the byte stream. */
    private static final class BitWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int buffer;
        private int count;

        void write(int value, int bits) {
            buffer |= (value & ((1 << bits) - 1)) << count;
            count += bits;
            while (count >= 8) {
                out.write(buffer & 0xff);
                buffer >>>= 8;
                count -= 8;
            }
        }

        void writeCode(int code, int bits) {
            int reversed = 0;
            for (int i = 0; i < bits; i++) {
                reversed |= ((code >>> (bits - 1 - i)) & 1) << i;
            }
            write(reversed, bits);
        }

        byte[] toByteArray() {
            if (count > 0) {
                out.write(buffer & 0xff);
                buffer = 0;
                count = 0;
            }
            return out.toByteArray();
        }
    }

    private void encodeBlock(int[] tokens, int from, int to, BitWriter writer, boolean finalBlock) {
        int[] litlenFreq = new int[286];
        int[] distFreq = new int[30];
        litlenFreq[256] = 1;
        for (int i = from; i < to; i++) {
            int token = tokens[i];
            if (token >= 0) {
                litlenFreq[token]++;
            } else {
                int len = (-token) >>> 16;
                int dist = (-token) & 0xffff;
                litlenFreq[257 + LENGTH_CODE[len]]++;
                distFreq[distanceCode(dist)]++;
            }
        }
        int[] litlenLength = huffmanCodeLengths(litlenFreq, 285, LENGTH_LIMIT);
        int[] distLength = huffmanCodeLengths(distFreq, 29, LENGTH_LIMIT);
        boolean anyDistance = false;
        for (int l : distLength) {
            anyDistance |= l > 0;
        }
        if (!anyDistance) {
            distLength[0] = 1;
        }
        int hlit = 286;
        while (hlit > 257 && litlenLength[hlit - 1] == 0) {
            hlit--;
        }
        int hdist = 30;
        while (hdist > 1 && distLength[hdist - 1] == 0) {
            hdist--;
        }
        int[] combined = new int[hlit + hdist];
        System.arraycopy(litlenLength, 0, combined, 0, hlit);
        System.arraycopy(distLength, 0, combined, hlit, hdist);
        List<int[]> runLength = runLengthEncode(combined);
        int[] codeLengthFreq = new int[19];
        for (int[] symbol : runLength) {
            codeLengthFreq[symbol[0]]++;
        }
        int[] codeLengthLength = huffmanCodeLengths(codeLengthFreq, 18, 7);
        int[] codeLengthCode = canonicalCodes(codeLengthLength, 18);
        int hclen = 19;
        while (hclen > 4 && codeLengthLength[CODE_LENGTH_ORDER[hclen - 1]] == 0) {
            hclen--;
        }
        int[] litlenCode = canonicalCodes(litlenLength, 285);
        int[] distCode = canonicalCodes(distLength, 29);

        writer.write(finalBlock ? 1 : 0, 1);
        writer.write(2, 2);
        writer.write(hlit - 257, 5);
        writer.write(hdist - 1, 5);
        writer.write(hclen - 4, 4);
        for (int k = 0; k < hclen; k++) {
            writer.write(codeLengthLength[CODE_LENGTH_ORDER[k]], 3);
        }
        for (int[] symbol : runLength) {
            writer.writeCode(codeLengthCode[symbol[0]], codeLengthLength[symbol[0]]);
            if (symbol[2] > 0) {
                writer.write(symbol[1], symbol[2]);
            }
        }
        for (int i = from; i < to; i++) {
            int token = tokens[i];
            if (token >= 0) {
                writer.writeCode(litlenCode[token], litlenLength[token]);
            } else {
                int len = (-token) >>> 16;
                int dist = (-token) & 0xffff;
                int lc = LENGTH_CODE[len];
                writer.writeCode(litlenCode[257 + lc], litlenLength[257 + lc]);
                if (LENGTH_EXTRA[lc] > 0) {
                    writer.write(len - LENGTH_BASE[lc], LENGTH_EXTRA[lc]);
                }
                int dc = distanceCode(dist);
                writer.writeCode(distCode[dc], distLength[dc]);
                if (DISTANCE_EXTRA[dc] > 0) {
                    writer.write(dist - DISTANCE_BASE[dc], DISTANCE_EXTRA[dc]);
                }
            }
        }
        writer.writeCode(litlenCode[256], litlenLength[256]);
    }

    /** Encodes a code-length sequence with the 16/17/18 repeat symbols; each entry is {symbol, extra, extraBits}. */
    private static List<int[]> runLengthEncode(int[] lengths) {
        List<int[]> output = new ArrayList<>();
        int i = 0;
        while (i < lengths.length) {
            int current = lengths[i];
            int run = 1;
            while (i + run < lengths.length && lengths[i + run] == current) {
                run++;
            }
            if (current == 0) {
                int remaining = run;
                while (remaining >= 11) {
                    int take = Math.min(remaining, 138);
                    output.add(new int[]{18, take - 11, 7});
                    remaining -= take;
                }
                while (remaining >= 3) {
                    int take = remaining;
                    output.add(new int[]{17, take - 3, 3});
                    remaining -= take;
                }
                while (remaining-- > 0) {
                    output.add(new int[]{0, 0, 0});
                }
            } else {
                output.add(new int[]{current, 0, 0});
                int remaining = run - 1;
                while (remaining >= 3) {
                    int take = Math.min(remaining, 6);
                    output.add(new int[]{16, take - 3, 2});
                    remaining -= take;
                }
                while (remaining-- > 0) {
                    output.add(new int[]{current, 0, 0});
                }
            }
            i += run;
        }
        return output;
    }

    private byte[] encodeWithSplits(int[] tokens, int count, List<Integer> splitPoints) {
        BitWriter writer = new BitWriter();
        int previous = 0;
        for (int s = 0; s <= splitPoints.size(); s++) {
            int end = (s < splitPoints.size()) ? splitPoints.get(s) : count;
            encodeBlock(tokens, previous, end, writer, end == count);
            previous = end;
        }
        return writer.toByteArray();
    }

    private static double entropyBits(int[] counts) {
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        if (total == 0) {
            return 0;
        }
        double logTotal = Math.log(total);
        double inverseLog2 = 1.0 / Math.log(2);
        double bits = 0;
        for (int c : counts) {
            if (c > 0) {
                bits += c * (logTotal - Math.log(c)) * inverseLog2;
            }
        }
        return bits;
    }

    /** Cheap entropy estimate of encoding a token range as one block, used only to choose split points. */
    private double estimateBits(int[] tokens, int from, int to, double treeOverhead) {
        int[] litlen = new int[286];
        int[] dist = new int[30];
        litlen[256] = 1;
        double extra = 0;
        for (int i = from; i < to; i++) {
            int token = tokens[i];
            if (token >= 0) {
                litlen[token]++;
            } else {
                int len = (-token) >>> 16;
                int d = (-token) & 0xffff;
                int lc = LENGTH_CODE[len];
                litlen[257 + lc]++;
                extra += LENGTH_EXTRA[lc];
                int dc = distanceCode(d);
                dist[dc]++;
                extra += DISTANCE_EXTRA[dc];
            }
        }
        return entropyBits(litlen) + entropyBits(dist) + extra + treeOverhead;
    }

    private void chooseSplits(int[] tokens, int from, int to, List<Integer> points, int depth, double treeOverhead) {
        if (depth <= 0 || to - from < 512) {
            return;
        }
        double whole = estimateBits(tokens, from, to, treeOverhead);
        int bestMid = -1;
        double bestCost = whole;
        for (int mid = from + 64; mid < to - 64; mid += 24) {
            double cost = estimateBits(tokens, from, mid, treeOverhead) + estimateBits(tokens, mid, to, treeOverhead);
            if (cost < bestCost) {
                bestCost = cost;
                bestMid = mid;
            }
        }
        if (bestMid > 0) {
            points.add(bestMid);
            chooseSplits(tokens, from, bestMid, points, depth - 1, treeOverhead);
            chooseSplits(tokens, bestMid, to, points, depth - 1, treeOverhead);
        }
    }

    /** Encodes as a single block and as several split configurations, returning the smallest. */
    private byte[] bestEncoding(int[] tokens, int count) {
        BitWriter single = new BitWriter();
        encodeBlock(tokens, 0, count, single, true);
        byte[] best = single.toByteArray();
        for (double treeOverhead : new double[]{60, 100, 140, 180, 220, 280, 340, 420, 500, 620, 750, 900}) {
            List<Integer> points = new ArrayList<>();
            chooseSplits(tokens, 0, count, points, 8, treeOverhead);
            Collections.sort(points);
            byte[] candidate = encodeWithSplits(tokens, count, points);
            if (candidate.length < best.length) {
                best = candidate;
            }
        }
        return best;
    }

    private static int symbolCost(int symbol, int[] codeLength) {
        int c = codeLength[symbol];
        return c > 0 ? c : 13;
    }

    /** Shortest-path optimal parse: the cheapest literal/match sequence under the current cost model. */
    private int[] parse(int[] litlenBits, int[] distBits, int[] tokenCount) {
        long[] cost = new long[length + 1];
        Arrays.fill(cost, Long.MAX_VALUE / 4);
        cost[0] = 0;
        int[] arriveLength = new int[length + 1];
        int[] arriveDistance = new int[length + 1];
        for (int i = 0; i < length; i++) {
            long here = cost[i];
            if (here >= Long.MAX_VALUE / 8) {
                continue;
            }
            long literal = here + symbolCost(data[i] & 0xff, litlenBits);
            if (literal < cost[i + 1]) {
                cost[i + 1] = literal;
                arriveLength[i + 1] = 1;
                arriveDistance[i + 1] = 0;
            }
            int longest = matchLength[i];
            if (longest >= MIN_MATCH) {
                int[] distanceByLength = matchDistanceByLength[i];
                for (int len = MIN_MATCH; len <= longest; len++) {
                    int dist = distanceByLength[len];
                    int lc = LENGTH_CODE[len];
                    int dc = distanceCode(dist);
                    long matchCost = here + symbolCost(257 + lc, litlenBits) + LENGTH_EXTRA[lc]
                            + symbolCost(dc, distBits) + DISTANCE_EXTRA[dc];
                    if (matchCost < cost[i + len]) {
                        cost[i + len] = matchCost;
                        arriveLength[i + len] = len;
                        arriveDistance[i + len] = dist;
                    }
                }
            }
        }
        int[] reversed = new int[length];
        int produced = 0;
        int position = length;
        while (position > 0) {
            int len = arriveLength[position];
            if (len == 1) {
                reversed[produced++] = data[position - 1] & 0xff;
            } else {
                reversed[produced++] = -(len * 65536 + arriveDistance[position]);
            }
            position -= len;
        }
        int[] tokens = new int[produced];
        for (int k = 0; k < produced; k++) {
            tokens[k] = reversed[produced - 1 - k];
        }
        tokenCount[0] = produced;
        return tokens;
    }

    private byte[] run() {
        if (length < MIN_MATCH) {
            return storedBlock();
        }
        findMatches();
        int[] litlenBits = new int[286];
        int[] distBits = new int[30];
        for (int i = 0; i <= 143; i++) {
            litlenBits[i] = 8;
        }
        for (int i = 144; i <= 255; i++) {
            litlenBits[i] = 9;
        }
        for (int i = 256; i <= 279; i++) {
            litlenBits[i] = 7;
        }
        for (int i = 280; i <= 285; i++) {
            litlenBits[i] = 8;
        }
        Arrays.fill(distBits, 5);

        int[] bestTokens = null;
        int bestCount = 0;
        int bestSize = Integer.MAX_VALUE;
        for (int iteration = 0; iteration < 40; iteration++) {
            int[] count = new int[1];
            int[] tokens = parse(litlenBits, distBits, count);
            BitWriter probe = new BitWriter();
            encodeBlock(tokens, 0, count[0], probe, true);
            int size = probe.toByteArray().length;
            if (size < bestSize) {
                bestSize = size;
                bestTokens = Arrays.copyOf(tokens, count[0]);
                bestCount = count[0];
            }
            int[] litlenFreq = new int[286];
            int[] distFreq = new int[30];
            litlenFreq[256] = 1;
            for (int k = 0; k < count[0]; k++) {
                int token = tokens[k];
                if (token >= 0) {
                    litlenFreq[token]++;
                } else {
                    int len = (-token) >>> 16;
                    int dist = (-token) & 0xffff;
                    litlenFreq[257 + LENGTH_CODE[len]]++;
                    distFreq[distanceCode(dist)]++;
                }
            }
            litlenBits = huffmanCodeLengths(litlenFreq, 285, LENGTH_LIMIT);
            distBits = huffmanCodeLengths(distFreq, 29, LENGTH_LIMIT);
        }
        return bestEncoding(bestTokens, bestCount);
    }

    /** Stored (uncompressed) blocks for inputs too short to match; keeps the encoder total. */
    private byte[] storedBlock() {
        BitWriter writer = new BitWriter();
        writer.write(1, 1);
        writer.write(0, 2);
        while (writer.count != 0) {
            writer.write(0, 1);
        }
        byte[] header = writer.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header);
        out.write(length & 0xff);
        out.write((length >>> 8) & 0xff);
        out.write(~length & 0xff);
        out.write((~length >>> 8) & 0xff);
        out.writeBytes(data);
        return out.toByteArray();
    }
}
