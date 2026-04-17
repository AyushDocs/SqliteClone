package personal.projects.sqlite.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.*;

/**
 * Dynamically builds a MemoryLayout from an external specification file.
 */
public class DynamicLayoutFactory {

    public static StructLayout build(String resourcePath) {
        List<MemoryLayout> members = new ArrayList<>();

        try (var is = DynamicLayoutFactory.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
            
            var reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                String name = parts[0].trim();
                int size = Integer.parseInt(parts[1].trim());
                String type = parts[2].trim();

                members.add(parseMember(name, size, type));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build dynamic layout from " + resourcePath, e);
        }

        return MemoryLayout.structLayout(members.toArray(new MemoryLayout[0])).withByteAlignment(1);
    }

    private static MemoryLayout parseMember(String name, int size, String type) {
        if (type.startsWith("sequence:")) {
            return MemoryLayout.sequenceLayout(size, JAVA_BYTE).withName(name).withByteAlignment(1);
        }
        
        return (switch (type) {
            case "byte" -> JAVA_BYTE;
            case "short" -> JAVA_SHORT;
            case "int" -> JAVA_INT;
            case "long" -> JAVA_LONG;
            default -> throw new IllegalArgumentException("Unknown type in spec: " + type);
        }).withName(name).withByteAlignment(1);
    }
}
