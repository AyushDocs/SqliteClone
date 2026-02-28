package personal.projects.sqlite.entities;

import java.nio.ByteBuffer;

public class VariableLengthInt {
    public static long read(ByteBuffer buffer) {
        long result = 0;
        int bytesRead = 0;

        while (bytesRead < 9) {
            byte b = buffer.get();
            result = (result << 7) | (b & 0x7F);
            bytesRead++;
            if ((b & 0x80) == 0) break;
        }

        return result;
    }
}
