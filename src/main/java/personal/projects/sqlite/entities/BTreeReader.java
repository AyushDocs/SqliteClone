package personal.projects.sqlite.entities;

import personal.projects.sqlite.utils.SegmentReader;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BTreeReader {

    private final MemorySegment dbFile;
    private final int pageSize;

    public BTreeReader(MemorySegment dbFile, int pageSize) {
        this.dbFile = dbFile;
        this.pageSize = pageSize;
    }

    public List<Row> readTable(int rootPageNumber) {
        List<Row> rows = new ArrayList<>();
        parsePage(rootPageNumber, rows);
        return rows;
    }

    private void parsePage(int pageNumber, List<Row> rows) {
        // Page 1 is special: the B-Tree header starts at offset 100
        int btreeHeaderOffset = (pageNumber == 1) ? 100 : 0;
        long pageStart = (long) (pageNumber - 1) * pageSize;
        
        // Wrap the segment slice in our high-performance reader
        SegmentReader reader = new SegmentReader(dbFile.asSlice(pageStart, pageSize));
        reader.goTo(btreeHeaderOffset);

        int pageType = reader.readByte();
        reader.goTo(btreeHeaderOffset + 3); // Skip freeblock to reach numCells
        int numCells = reader.readShort();

        if (pageType == 0x05 || pageType == 0x02) {
            // Interior page
            for (int i = 0; i < numCells; i++) {
                reader.goTo(btreeHeaderOffset + 12 + i * 2);
                int cellOffset = reader.readShort();
                reader.goTo(cellOffset);
                int childPage = reader.readInt();
                parsePage(childPage, rows);
            }
            // TODO: parse the right-most child pointer at offset + 8
        } else if (pageType == 0x0D) {
            // Table leaf page
            for (int i = 0; i < numCells; i++) {
                reader.goTo(btreeHeaderOffset + 8 + i * 2);
                int cellOffset = reader.readShort();
                reader.goTo(cellOffset);

                long payloadSize = reader.readVarInt();
                long rowId = reader.readVarInt();

                int headerOffsetBeforeSize = (int) reader.getOffset();
                int headerSize = (int) reader.readVarInt();
                int headerEnd = headerOffsetBeforeSize + headerSize;

                List<Integer> serialTypes = new ArrayList<>();
                while (reader.getOffset() < headerEnd) {
                    serialTypes.add((int) reader.readVarInt());
                }

                List<Object> values = new ArrayList<>();
                for (int serialType : serialTypes) {
                    int size = getSerialTypeSize(serialType);
                    if (serialType == 0) {
                        values.add(null);
                    } else if (serialType >= 13 && serialType % 2 != 0) {
                        values.add(new String(reader.readNBytes(size), StandardCharsets.UTF_8));
                    } else if (serialType >= 12 && serialType % 2 == 0) {
                        values.add(reader.readNBytes(size));
                    } else {
                        // Numeric types
                        values.add(readNumeric(reader, serialType));
                    }
                }
                rows.add(new Row(rowId, values));
            }
        }
    }

    private static int getSerialTypeSize(int type) {
        return switch (type) {
            case 0, 8, 9 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 6;
            case 6, 7 -> 8;
            default -> {
                if (type >= 12 && type % 2 == 0) yield (type - 12) / 2;
                if (type >= 13) yield (type - 13) / 2;
                throw new IllegalArgumentException("Unknown serial type: " + type);
            }
        };
    }

    private static Object readNumeric(SegmentReader reader, int type) {
        return switch (type) {
            case 1 -> (long) reader.readByte();
            case 2 -> (long) reader.readShort();
            case 4 -> (long) reader.readInt();
            case 6 -> reader.readLong();
            case 8 -> 0L;
            case 9 -> 1L;
            default -> 0L; // Simplified
        };
    }

    public record Row(long rowId, List<Object> columns) {}
}
