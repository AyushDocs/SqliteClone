package personal.projects.sqlite.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import personal.projects.sqlite.entities.BTreeReader;
import personal.projects.sqlite.entities.BTreeWriter;
import personal.projects.sqlite.entities.Database;

/**
 * Handles INSERT INTO statements.
 */
public class InsertCommand implements Command {

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+(\\w+)\\s+VALUES\\s*\\((.*)\\)", 
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "INSERT";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String fullCommand = getName() + " " + String.join(" ", context.parameters());
        Matcher matcher = INSERT_PATTERN.matcher(fullCommand);

        if (!matcher.find()) {
            return CommandResult.error("Invalid INSERT syntax. Use: INSERT INTO <table> VALUES (val1, val2, ...)");
        }

        String tableName = matcher.group(1);
        String valuesStr = matcher.group(2);
        List<Object> values = parseValues(valuesStr);

        Database db = context.database();
        BTreeReader bTreeReader = new BTreeReader(db.memoryMap, db.pageSize);

        // 1. Find root page of the table
        List<BTreeReader.Row> schemaRows = bTreeReader.readTable(1);
        Optional<BTreeReader.Row> tableEntry = schemaRows.stream()
                .filter(row -> "table".equals(row.columns().get(0)) && tableName.equalsIgnoreCase((String) row.columns().get(1)))
                .findFirst();

        if (tableEntry.isEmpty()) {
            return CommandResult.error("Table not found: " + tableName);
        }

        int rootPage = (int) (long) tableEntry.get().columns().get(3);

        // 2. Perform insertion using BTreeWriter
        try {
            BTreeWriter writer = new BTreeWriter(db.memoryMap, db.pageSize);
            writer.insertRow(rootPage, values);
            return CommandResult.success("1 row inserted into " + tableName);
        } catch (Exception e) {
            return CommandResult.error("Insertion failed: " + e.getMessage());
        }
    }

    private List<Object> parseValues(String valuesStr) {
        List<Object> values = new ArrayList<>();
        // Simple comma split, stripping quotes
        String[] parts = valuesStr.split(",");
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                values.add(trimmed.substring(1, trimmed.length() - 1));
            } else if (trimmed.equalsIgnoreCase("NULL")) {
                values.add(null);
            } else {
                try {
                    values.add(Long.valueOf(trimmed));
                } catch (NumberFormatException e) {
                    values.add(trimmed); // Treat as raw string if not null or long
                }
            }
        }
        return values;
    }
}
