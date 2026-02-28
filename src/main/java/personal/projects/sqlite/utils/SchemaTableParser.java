package personal.projects.sqlite.utils;

import personal.projects.sqlite.entities.Database;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SchemaTableParser {

    public static void parseSchema(byte[] bytes, Database db) {
        CustomReader reader = new CustomReader(bytes);

        // 1. Page type
        int pageType = reader.readByte();
        if (pageType != 0x0D) {
            throw new IllegalStateException("Not a table b-tree leaf page (expected 0x0D)");
        }

        // 2. Number of cells
        int numCells = reader.readShort();

        // 3. Cell pointers start at byte 8
        List<String> tableNames = new ArrayList<>();
        for (int i = 0; i < numCells; i++) {
            // Cell pointer
            reader.goTo(8 + (i * 2));
            int cellOffset = reader.readShort();

            // Jump to cell
            reader.goTo(cellOffset);

            // [payload size varint]
            long payloadSize = reader.readVarInt();
            // [rowid varint]
            long rowId = reader.readVarInt();

            // Header
            int headerSize = (int) reader.readVarInt();
            int headerStart = reader.getOffset();

            // Serial types
            List<Integer> serialTypes = new ArrayList<>();
            while (reader.getOffset() < headerStart + headerSize) {
                serialTypes.add((int) reader.readVarInt());
            }

            // Column values
            String typeName = null;
            String tableName = null;
            for (int j = 0; j < serialTypes.size(); j++) {
                int type = serialTypes.get(j);
                int size = getSerialTypeSize(type);
                byte[] valueBytes = reader.readNBytes(size);

                if (j == 0) {
                    typeName = new String(valueBytes, StandardCharsets.UTF_8);
                } else if (j == 1) {
                    tableName = new String(valueBytes, StandardCharsets.UTF_8);
                    tableNames.add(tableName);
                }

            }

        }

        db.tableNames = tableNames;
    }

    private static int getSerialTypeSize(int type) {
        if (type == 0) return 0;  // NULL
        if (type == 1) return 1;
        if (type == 2) return 2;
        if (type == 3) return 3;
        if (type == 4) return 4;
        if (type == 5) return 6;
        if (type == 6) return 8;  // int64
        if (type == 7) return 8;  // float64
        if (type == 8) return 0;  // integer 0
        if (type == 9) return 0;  // integer 1
        if (type >= 12 && type % 2 == 0) return (type - 12) / 2; // blob
        if (type >= 13) return (type - 13) / 2;                  // text
        throw new IllegalArgumentException("Unknown serial type: " + type);
    }
}
