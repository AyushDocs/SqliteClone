//package personal.projects.sqlite.entities;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.ArrayList;
//import java.util.List;
//
//public class LeafTablePage extends Page {
//    private final int cellCount;
//    private final int[] cellPointers;
//    private final List<Record> records = new ArrayList<>();
//
//    public LeafTablePage(int pageNumber, byte[] data) {
//        super(pageNumber, PageType.LEAF_TABLE, data);
//
//        ByteBuffer buffer = ByteBuffer.wrap(data);
//        buffer.order(ByteOrder.BIG_ENDIAN);
//
//        // Header starts at byte 0
//        int headerOffset = (pageNumber == 1) ? 100 : 0;
//
//        byte pageType = buffer.get(headerOffset); // should be 0x0D
//        assert PageType.fromByte(pageType) == PageType.LEAF_TABLE;
//
//        cellCount = Short.toUnsignedInt(buffer.getShort(headerOffset + 3));
//
//        // Cell pointer array starts after header (8+ bytes)
//        int[] pointers = new int[cellCount];
//        for (int i = 0; i < cellCount; i++) {
//            pointers[i] = Short.toUnsignedInt(buffer.getShort(headerOffset + 8 + i * 2));
//        }
//        this.cellPointers = pointers;
//
//        // Parse each cell
//        for (int offset : pointers) {
//            Record record = Record.parse(data, offset);
//            records.add(record);
//        }
//    }
//
//    public List<Record> getRecords() {
//        return records;
//    }
//
//    public int getCellCount() {
//        return cellCount;
//    }
//}
//
