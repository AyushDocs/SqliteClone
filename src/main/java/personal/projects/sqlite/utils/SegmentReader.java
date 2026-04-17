package personal.projects.sqlite.utils;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * A high-performance reader powered by Project Panama's MemorySegment.
 * Handles big-endian reads and SQLite varints type-safely.
 */
public class SegmentReader {
    private long offset;
    private final MemorySegment segment;

    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfLong LONG_BE = ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);

    public SegmentReader(byte[] buffer) {
        this.segment = MemorySegment.ofArray(buffer);
        this.offset = 0;
    }

    public SegmentReader(MemorySegment segment) {
        this.segment = segment;
        this.offset = 0;
    }

    public void goTo(long newOffset) {
        this.offset = newOffset;
    }

    public int readByte() {
        int val = segment.get(ValueLayout.JAVA_BYTE, offset) & 0xFF;
        offset += 1;
        return val;
    }

    public int readShort() {
        int val = segment.get(SHORT_BE, offset) & 0xFFFF;
        offset += 2;
        return val;
    }

    public int readInt() {
        int val = segment.get(INT_BE, offset);
        offset += 4;
        return val;
    }

    public long readLong() {
        long val = segment.get(LONG_BE, offset);
        offset += 8;
        return val;
    }

    public byte[] readNBytes(int n) {
        byte[] result = segment.asSlice(offset, n).toArray(ValueLayout.JAVA_BYTE);
        offset += n;
        return result;
    }

    /**
     * Reads a SQLite varint (1-9 bytes).
     * Returns a long as varints can represent 64-bit values.
     */
    public long readVarInt() {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            int b = readByte();
            result = (result << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) return result;
        }
        // 9th byte is special: all 8 bits are data
        return (result << 8) | readByte();
    }

    public long getOffset() {
        return this.offset;
    }

    public boolean hasRemaining() {
        return offset < segment.byteSize();
    }

    public void skip(long n) {
        offset += n;
    }
}
