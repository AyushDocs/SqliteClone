package personal.projects.sqlite.utils;

import personal.projects.sqlite.entities.Database;
import personal.projects.sqlite.entities.HeaderField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DatabaseHeaderParser {

    public static void parseHeader(byte[] bytes, Database db) throws Exception {
        CustomReader reader=new CustomReader(bytes);
        reader.goTo(0);
        Map<String,Integer> headerFields = new HashMap<>();
        Map<String,HeaderField<?>> xmlReadHeaders=XmlHeaderFieldParser.parseHeaderFields("header_fields.xml");
        for (Entry<String,HeaderField<?>> entry : xmlReadHeaders.entrySet() ) {
            int offset = entry.getValue().getOffset();
            int size = entry.getValue().getSize();
            int value;
            if(size==1)
                value=reader.readByte();
            else if(size==2)
                value=reader.readShort();
            else if(size==4)
                value=reader.readInt();
            else
                throw new IllegalArgumentException("illegal argument passed");

            headerFields.put(entry.getKey(),value);
        }

        // Also handle text encoding
        int encodingId = headerFields.get("textEncodingId");
        db.textEncoding=getEncoding(encodingId);

        reader.goTo(72);
        byte[] reserved = reader.readNBytes(20);
        db.reserved = bytesToHex(reserved);
        db.headerFields=headerFields;
    }


    private static Charset getEncoding(int encoding) {
        return switch (encoding) {
            case 2 -> StandardCharsets.UTF_16LE;
            case 3 -> StandardCharsets.UTF_16BE;
            default -> StandardCharsets.UTF_8;
        };
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x ", b));
        return sb.toString().trim();
    }
}
