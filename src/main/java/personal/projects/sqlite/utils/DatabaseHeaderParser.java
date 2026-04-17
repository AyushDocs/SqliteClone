package personal.projects.sqlite.utils;

import static personal.projects.sqlite.utils.SQLiteHeaderLayout.LAYOUT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import personal.projects.sqlite.entities.Database.HeaderValue;
import personal.projects.sqlite.entities.Database.HeaderValue.NumericValue;
import personal.projects.sqlite.entities.Database.HeaderValue.StringValue;

public class DatabaseHeaderParser {

    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfLong LONG_BE = ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);

    public static Map<String, HeaderValue> parse(byte[] headerBytes) {
        Map<String, HeaderValue> headerMap = new HashMap<>();
        MemorySegment segment = MemorySegment.ofArray(headerBytes);

        for (MemoryLayout member : LAYOUT.memberLayouts()) {
            member.name().ifPresent(name -> {
                if (name.equals("magic") || name.equals("reserved")) return;

                HeaderValue value = getValue(segment, member);
                headerMap.put(name, value);
            });
        }

        // Special handling for the 64k pageSize logic
        long pageSize = ((NumericValue) headerMap.getOrDefault("pageSize", new NumericValue(0))).value() & 0xFFFF;
        if (pageSize == 1) pageSize = 65536;
        headerMap.put("pageSize", new NumericValue(pageSize));

        return headerMap;
    }

    private static HeaderValue getValue(MemorySegment segment, MemoryLayout member) {
        long offset = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement(member.name().get()));
        
        return switch (member) {
            case ValueLayout.OfByte b -> new NumericValue((long) segment.get(b, offset));
            case ValueLayout.OfShort s -> new NumericValue((long) segment.get(SHORT_BE, offset));
            case ValueLayout.OfInt i -> new NumericValue((long) segment.get(INT_BE, offset));
            case ValueLayout.OfLong l -> new NumericValue(segment.get(LONG_BE, offset));
            default -> null; // Sequences like 'magic' are handled elsewhere
        };
    }
}
