package personal.projects.sqlite.entities;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import personal.projects.sqlite.commands.Command;
import personal.projects.sqlite.commands.CommandContext;
import personal.projects.sqlite.commands.CommandResult;
import personal.projects.sqlite.utils.DatabaseHeaderParser;

/**
 * Represents the SQLite database session.
 */
public class Database implements AutoCloseable {

    private static final long FILE_CHANGE_COUNTER_OFFSET = 24;
    private static final long PAGE_COUNT_OFFSET = 28;
    private static final int HEADROOM_PAGES = 256;

    public final MemorySegment memoryMap;
    public final int pageSize;
    public int pageCount;
    public final Map<String, HeaderValue> header;
    private final Arena arena;
    private final FileChannel channel;

    private Database(MemorySegment memoryMap, int pageSize, int pageCount, Map<String, HeaderValue> header, Arena arena, FileChannel channel) {
        this.memoryMap = memoryMap;
        this.pageSize = pageSize;
        this.pageCount = pageCount;
        this.header = header;
        this.arena = arena;
        this.channel = channel;
    }

    /**
     * Factory method to open a database. Uses READ_WRITE mode to support insertions.
     * Maps extra headroom pages so the file can grow without re-mapping on every insert.
     */
    public static Database open(String path) throws IOException {
        Arena arena = Arena.ofShared();
        FileChannel channel = FileChannel.open(Path.of(path), 
                StandardOpenOption.READ, 
                StandardOpenOption.WRITE);
        
        long size = channel.size();
        int pageSize = 4096;
        if (size >= 100) {
            byte[] head = new byte[100];
            var headBuf = java.nio.ByteBuffer.wrap(head);
            channel.read(headBuf, 0);
            pageSize = ((head[16] & 0xFF) << 8 | (head[17] & 0xFF)) & 0xFFFF;
            if (pageSize == 1) pageSize = 65536;
        }
        long mappedSize = size + (long) HEADROOM_PAGES * pageSize;
        MemorySegment memoryMap = channel.map(FileChannel.MapMode.READ_WRITE, 0, mappedSize, arena);
        
        // Parse the header (first 100 bytes)
        byte[] headerBytes = memoryMap.asSlice(0, 100).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        Map<String, HeaderValue> header = DatabaseHeaderParser.parse(headerBytes);
        
        pageSize = (int) ((HeaderValue.NumericValue) header.get("pageSize")).value();
        int pageCount = (int) ((HeaderValue.NumericValue) header.get("pageCount")).value();

        return new Database(memoryMap, pageSize, pageCount, header, arena, channel);
    }

    public CommandResult execute(Command command, List<String> parameters) {
        return command.execute(new CommandContext(this, parameters, new java.util.HashMap<>()));
    }

    /**
     * Allocates a new page number beyond the current page count, growing the
     * file to cover it, and returns the page number.
     */
    public synchronized int allocatePage() {
        pageCount += 1;
        long lastByte = (long) pageCount * pageSize - 1;
        memoryMap.set(java.lang.foreign.ValueLayout.JAVA_BYTE, lastByte, (byte) 0);
        memoryMap.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), PAGE_COUNT_OFFSET, pageCount);
        return pageCount;
    }

    /** Increments the file change counter, as SQLite does after a committed change. */
    public void bumpChangeCounter() {
        int c = memoryMap.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), FILE_CHANGE_COUNTER_OFFSET);
        memoryMap.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN), FILE_CHANGE_COUNTER_OFFSET, c + 1);
    }

    @Override
    public void close() throws IOException {
        // Mapping with headroom extends the file to the mapped size on close;
        // trim it back to the actual number of used pages.
        channel.truncate((long) pageCount * pageSize);
        // Arena.close() unmaps the file automatically
        arena.close();
        channel.close();
    }

    /**
     * Sealed interface for database header values (metadata).
     */
    public sealed interface HeaderValue permits HeaderValue.NumericValue, HeaderValue.StringValue {
        record NumericValue(long value) implements HeaderValue {}
        record StringValue(String value) implements HeaderValue {}
    }
}
