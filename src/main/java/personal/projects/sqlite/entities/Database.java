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

    public final MemorySegment memoryMap;
    public final int pageSize;
    public final Map<String, HeaderValue> header;
    private final Arena arena;
    private final FileChannel channel;

    private Database(MemorySegment memoryMap, int pageSize, Map<String, HeaderValue> header, Arena arena, FileChannel channel) {
        this.memoryMap = memoryMap;
        this.pageSize = pageSize;
        this.header = header;
        this.arena = arena;
        this.channel = channel;
    }

    /**
     * Factory method to open a database. Uses READ_WRITE mode to support insertions.
     */
    public static Database open(String path) throws IOException {
        Arena arena = Arena.ofShared();
        FileChannel channel = FileChannel.open(Path.of(path), 
                StandardOpenOption.READ, 
                StandardOpenOption.WRITE);
        
        // Map the entire file for direct hardware access
        MemorySegment memoryMap = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size(), arena);
        
        // Parse the header (first 100 bytes)
        byte[] headerBytes = memoryMap.asSlice(0, 100).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        Map<String, HeaderValue> header = DatabaseHeaderParser.parse(headerBytes);
        
        int pageSize = (int) ((HeaderValue.NumericValue) header.get("pageSize")).value();

        return new Database(memoryMap, pageSize, header, arena, channel);
    }

    public CommandResult execute(Command command, List<String> parameters) {
        return command.execute(new CommandContext(this, parameters, new java.util.HashMap<>()));
    }

    @Override
    public void close() throws IOException {
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
