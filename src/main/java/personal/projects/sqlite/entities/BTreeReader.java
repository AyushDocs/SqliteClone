package personal.projects.sqlite.entities;

import personal.projects.sqlite.utils.CustomReader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BTreeReader {

    private final byte[] dbFile;
    private final int pageSize;

    public BTreeReader(byte[] dbFile, int pageSize) {
        this.dbFile = dbFile;
        this.pageSize = pageSize;
    }

    /**
     * Reads all rows from a table starting at a given root page number.
     */
    public List<Row> readTable(int rootPageNumber) {
        List<Row> rows = new ArrayList<>();
        parsePage(rootPageNumber, rows);
        return rows;
    }

    private void parsePage(int pageNumber, List<Row> rows) {
        int offset = (pageNumber - 1) * pageSize;
        CustomReader reader = new CustomReader(dbFile);
        reader.goTo(offset);

        int pageType = reader.readByte();
        int numCells = reader.readShort();

        if (pageType == 0x05 || pageType == 0x02) {
            // Interior page (table or index)
            reader.skip(2); // right-most child pointer (not yet used)
            for (int i = 0; i < numCells; i++) {
                reader.goTo(offset + 12 + i * 2); // cell pointer array
                int cellOffset = reader.readShort();
                reader.goTo(offset + cellOffset);
                int childPage = reader.readInt();
                parsePage(childPage, rows);
            }
            // TODO: parse right-most child pointer
        }
        else if (pageType == 0x0D) {
            // Table leaf page
            for (int i = 0; i < numCells; i++) {
                reader.goTo(offset + 8 + i * 2);
                int cellOffset = reader.readShort();
                reader.goTo(offset + cellOffset);

                int payloadSize = reader.readVarInt();
                int rowId = reader.readVarInt();

                int headerSize = (int) reader.readVarInt();
                int headerStart = reader.getOffset();

                List<Integer> serialTypes = new ArrayList<>();
                while (reader.getOffset() < headerStart + headerSize) {
                    serialTypes.add((int) reader.readVarInt());
                }

                List<Object> values = new ArrayList<>();
                for (int serialType : serialTypes) {
                    int size = getSerialTypeSize(serialType);
                    byte[] val = reader.readNBytes(size);
                    if (serialType >= 13) {
                        values.add(new String(val, StandardCharsets.UTF_8)); // TEXT
                    } else if (serialType == 12) {
                        values.add(val); // BLOB
                    } else {
                        values.add(parseInteger(val)); // Integer / Float
                    }
                }

                rows.add(new Row(rowId, values));
            }
        }
        else {
            throw new IllegalStateException("Unknown page type: " + pageType);
        }
    }

    private static int getSerialTypeSize(int type) {
        if (type == 0) return 0;  // NULL
        if (type == 1) return 1;
        if (type == 2) return 2;
        if (type == 3) return 3;
        if (type == 4) return 4;
        if (type == 5) return 6;
        if (type == 6) return 8;
        if (type == 7) return 8;
        if (type == 8) return 0;
        if (type == 9) return 0;
        if (type >= 12 && type % 2 == 0) return (type - 12) / 2;
        if (type >= 13) return (type - 13) / 2;
        throw new IllegalArgumentException("Unknown serial type: " + type);
    }

    private static long parseInteger(byte[] bytes) {
        long val = 0;
        for (byte b : bytes) {
            val = (val << 8) | (b & 0xFF);
        }
        return val;
    }

    public static class Row {
        public final long rowId;
        public final List<Object> columns;
        public Row(long rowId, List<Object> columns) {
            this.rowId = rowId;
            this.columns = columns;
        }
    }
}
