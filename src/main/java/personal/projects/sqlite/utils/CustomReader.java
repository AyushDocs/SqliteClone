package personal.projects.sqlite.utils;

import java.nio.ByteBuffer;

public class CustomReader {
    private int offset;
    private final byte[] buffer;

    public CustomReader(byte[] buffer) {
        this.buffer = buffer;
        offset = 0;
    }
    public void goTo(int newOffset){
        offset = newOffset;
    }
    public int readByte(){
        return buffer[offset++];
    }
    public int readShort(){
        return buffer[offset++] << 8 | (buffer[offset++] & 0xFF);
    }
    public byte[] readNBytes(int n){
        if(offset+n > buffer.length){
            throw new IllegalStateException();
        }
        byte[] result = new byte[n];
        for(int i=0;i<n;i++){
            result[i] = buffer[offset++];
        }
        return result;
    }
    public int readInt(){
        return (buffer[offset++] & 0xFF) << 24 |
                (buffer[offset++] & 0xFF) << 16 |
                (buffer[offset++] & 0xFF) << 8  |
                (buffer[offset++] & 0xFF);
    }
    public int readVarInt(){
        int result = 0;
        int bytesRead = 0;
        while (bytesRead < 9) {
            byte b = buffer[offset++];
            result = (result << 7) | (b & 0x7F);
            bytesRead++;
            if ((b & 0x80) == 0) break;
        }
        return result;
    }

    public int getOffset() {
        return this.offset;
    }

    public boolean hasRemaining() {
        return offset<buffer.length;
    }

    public void skip(int i) {
        offset += i;
    }
}
