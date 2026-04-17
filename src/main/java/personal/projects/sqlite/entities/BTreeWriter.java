package personal.projects.sqlite.entities;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles writing rows to B-Tree pages.
 */
public class BTreeWriter {

    private final MemorySegment dbFile;
    private final int pageSize;

    public BTreeWriter(MemorySegment dbFile, int pageSize) {
        this.dbFile = dbFile;
        this.pageSize = pageSize;
    }

    public synchronized void insertRow(int rootPage, List<Object> values) {
        // For simplicity in this initial version, we only insert into the root page if it's a leaf.
        // Deep B-tree splits will be added in the next iteration.
        
        long pageStart = (long) (rootPage - 1) * pageSize;
        int btreeHeaderOffset = (rootPage == 1) ? 100 : 0;
        MemorySegment page = dbFile.asSlice(pageStart, pageSize);

        int pageType = page.get(ValueLayout.JAVA_BYTE, btreeHeaderOffset) & 0xFF;
        if (pageType != 0x0D) {
            throw new RuntimeException("Insert only supported on leaf pages currently. Page " + rootPage + " is type " + pageType);
        }

        // 1. Calculate rowId (scan for max)
        BTreeReader reader = new BTreeReader(dbFile, pageSize);
        List<BTreeReader.Row> rows = reader.readTable(rootPage);
        long nextRowId = rows.stream().mapToLong(BTreeReader.Row::rowId).max().orElse(0) + 1;

        // 2. Encode Payload
        byte[] payload = encodeRecord(values);
        
        // 3. Find space on page
        int numCells = page.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), btreeHeaderOffset + 3) & 0xFFFF;
        int cellContentStart = page.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), btreeHeaderOffset + 5) & 0xFFFF;
        if (cellContentStart == 0) cellContentStart = pageSize; // 0 means 65536

        // Size of new cell: varint payloadSize + varint rowid + payload
        byte[] payloadSizeVar = encodeVarInt(payload.length);
        byte[] rowIdVar = encodeVarInt(nextRowId);
        int cellSize = payloadSizeVar.length + rowIdVar.length + payload.length;

        // Check if there is space between cell pointers and content area
        int cellPointerEnd = btreeHeaderOffset + 8 + (numCells * 2);
        if (cellPointerEnd + 2 + cellSize > cellContentStart) {
            throw new RuntimeException("Page full! B-Tree splitting not yet implemented.");
        }

        // 4. Write Cell to the end of content area
        int newCellOffset = cellContentStart - cellSize;
        int writePtr = newCellOffset;
        
        for (byte b : payloadSizeVar) page.set(ValueLayout.JAVA_BYTE, writePtr++, b);
        for (byte b : rowIdVar) page.set(ValueLayout.JAVA_BYTE, writePtr++, b);
        for (byte b : payload) page.set(ValueLayout.JAVA_BYTE, writePtr++, b);

        // 5. Update Cell Pointer array
        page.set(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), btreeHeaderOffset + 8 + (numCells * 2), (short) newCellOffset);

        // 6. Update Header
        page.set(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), btreeHeaderOffset + 3, (short) (numCells + 1));
        page.set(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), btreeHeaderOffset + 5, (short) newCellOffset);

        // 7. Update File Change Counter (Offset 24 of Page 1)
        MemorySegment page1 = dbFile.asSlice(0, 100);
        int currentCounter = page1.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), 24);
        page1.set(ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), 24, currentCounter + 1);
    }

    private byte[] encodeRecord(List<Object> values) {
        List<Byte> header = new ArrayList<>();
        List<Byte> body = new ArrayList<>();

        for (Object val : values) {
            if (val == null) {
                header.addAll(toList(encodeVarInt(0)));
            } else if (val instanceof String s) {
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                header.addAll(toList(encodeVarInt(bytes.length * 2 + 13)));
                body.addAll(toList(bytes));
            } else if (val instanceof Long l) {
                header.addAll(toList(encodeVarInt(6))); // 8-byte int
                byte[] bytes = new byte[8];
                java.nio.ByteBuffer.wrap(bytes).putLong(l);
                body.addAll(toList(bytes));
            } else if (val instanceof Integer i) {
                header.addAll(toList(encodeVarInt(4))); // 4-byte int
                byte[] bytes = new byte[4];
                java.nio.ByteBuffer.wrap(bytes).putInt(i);
                body.addAll(toList(bytes));
            }
        }

        byte[] headerSizeVar = encodeVarInt(header.size() + 1); // +1 for the header size varint itself? 
        // Note: Simple encoding for now, real SQLite is more complex about header size.
        
        byte[] result = new byte[headerSizeVar.length + header.size() + body.size()];
        int p = 0;
        for (byte b : headerSizeVar) result[p++] = b;
        for (byte b : header) result[p++] = b;
        for (byte b : body) result[p++] = b;
        return result;
    }

    private List<Byte> toList(byte[] arr) {
        List<Byte> list = new ArrayList<>();
        for (byte b : arr) list.add(b);
        return list;
    }

    private byte[] encodeVarInt(long value) {
        // SQLite varint encoding logic
        if (value < 0) throw new IllegalArgumentException();
        List<Byte> bytes = new ArrayList<>();
        if (value == 0) return new byte[]{0};
        
        while (value > 0) {
            bytes.add(0, (byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        bytes.set(bytes.size() - 1, (byte) (bytes.get(bytes.size() - 1) & 0x7F));
        
        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) res[i] = bytes.get(i);
        return res;
    }
}
