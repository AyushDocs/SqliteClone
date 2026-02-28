//package personal.projects.sqlite.entities;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.ArrayList;
//import java.util.List;
//
//public record Record(int rowId, long payloadLength, long headerSize) {
//
//    public static Record parse(byte[] pageData, int offset) {
//        ByteBuffer buffer = ByteBuffer.wrap(pageData);
//        buffer.order(ByteOrder.BIG_ENDIAN);
//        buffer.position(offset);
//
//        long payloadLength = VariableLengthInt.read(buffer);
//        long rowId = VariableLengthInt.read(buffer);
//
//        int headerStart = buffer.position();
//        long headerLength = VariableLengthInt.read(buffer);
//
//        List<Long> serialTypes = new ArrayList<>();
//        int headerEnd = headerStart + (int) headerLength;
//        while (buffer.position() < headerEnd) {
//            serialTypes.add(VariableLengthInt.read(buffer));
//        }
//
//        List<Object> values = new ArrayList<>();
//        for (Long serialType : serialTypes) {
//            values.add(decodeValue(serialType, buffer));
//        }
//
//        return new Record((int) rowId, values);
//    }
//
//    private static Object decodeValue(long serialType, ByteBuffer buffer) {
//        return switch ((int) serialType) {
//            case 0 -> null; // NULL
//            case 1 -> buffer.get(); // int8
//            case 2 -> buffer.getShort(); // int16
//            case 3 -> (buffer.get() << 16) | (buffer.getShort() & 0xFFFF); // 24-bit
//            case 4 -> buffer.getInt(); // int32
//            case 5 -> buffer.getLong() >>> 16; // top 48 bits
//            case 6 -> buffer.getLong(); // int64
//            case 8 -> 0L;
//            case 9 -> 1L;
//            default -> {
//                if (serialType >= 13 && (serialType % 2 == 1)) {
//                    int len = (int) ((serialType - 13) / 2);
//                    byte[] strBytes = new byte[len];
//                    buffer.get(strBytes);
//                    yield new String(strBytes);
//                } else {
//                    yield "UNSUPPORTED";
//                }
//            }
//        };
//    }
//}
