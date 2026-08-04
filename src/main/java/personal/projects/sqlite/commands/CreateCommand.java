package personal.projects.sqlite.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import personal.projects.sqlite.entities.BTreeReader;
import personal.projects.sqlite.entities.BTreeWriter;
import personal.projects.sqlite.entities.Database;

/**
 * Handles CREATE TABLE and CREATE INDEX statements.
 */
public class CreateCommand implements Command {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s*\\((.+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern INDEX_PATTERN = Pattern.compile(
            "CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s+ON\\s+(\\w+)\\s*\\((.+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String getName() {
        return "CREATE";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String fullCommand = "CREATE " + String.join(" ", context.parameters());
        if (fullCommand.matches("(?is)CREATE\\s+TABLE.*")) {
            return createTable(context, fullCommand);
        }
        if (fullCommand.matches("(?is)CREATE\\s+(UNIQUE\\s+)?INDEX.*")) {
            return createIndex(context, fullCommand);
        }
        return CommandResult.error("Unsupported CREATE statement. Use CREATE TABLE or CREATE INDEX.");
    }

    private CommandResult createTable(CommandContext context, String fullCommand) {
        Matcher m = TABLE_PATTERN.matcher(fullCommand);
        if (!m.find()) {
            return CommandResult.error("Invalid CREATE TABLE syntax. Use: CREATE TABLE <name> (col type, ...)");
        }

        boolean ifNotExists = m.group(1) != null;
        String tableName = m.group(2);
        List<String> columnNames = parseColumnNames(m.group(3));
        if (columnNames.isEmpty()) {
            return CommandResult.error("CREATE TABLE requires at least one column.");
        }

        Database db = context.database();
        BTreeReader reader = new BTreeReader(db.memoryMap, db.pageSize);

        if (tableExists(reader, tableName)) {
            if (ifNotExists) return CommandResult.success("Table " + tableName + " already exists (no-op).");
            return CommandResult.error("Table already exists: " + tableName);
        }

        try {
            BTreeWriter writer = new BTreeWriter(db);
            int rootPage = writer.createTableRoot();
            long rowId = writer.nextRowId(1);
            List<Object> schemaRecord = List.of("table", tableName, tableName, (long) rootPage, fullCommand);
            writer.insertRowWithId(1, rowId, schemaRecord);
            return CommandResult.success("Table " + tableName + " created.");
        } catch (Exception e) {
            return CommandResult.error("CREATE TABLE failed: " + e.getMessage());
        }
    }

    private CommandResult createIndex(CommandContext context, String fullCommand) {
        Matcher m = INDEX_PATTERN.matcher(fullCommand);
        if (!m.find()) {
            return CommandResult.error("Invalid CREATE INDEX syntax. Use: CREATE INDEX <name> ON <table> (col, ...)");
        }

        boolean unique = m.group(1) != null;
        boolean ifNotExists = m.group(2) != null;
        String indexName = m.group(3);
        String tableName = m.group(4);
        List<String> columns = parseColumnNames(m.group(5));
        if (columns.isEmpty()) {
            return CommandResult.error("CREATE INDEX requires at least one column.");
        }

        Database db = context.database();
        BTreeReader reader = new BTreeReader(db.memoryMap, db.pageSize);

        if (!tableExists(reader, tableName)) {
            return CommandResult.error("Table not found: " + tableName);
        }
        if (indexExists(reader, indexName)) {
            if (ifNotExists) return CommandResult.success("Index " + indexName + " already exists (no-op).");
            return CommandResult.error("Index already exists: " + indexName);
        }

        List<String> tableColumns = extractTableColumns(findTableSql(reader, tableName));
        for (String col : columns) {
            boolean found = tableColumns.stream().anyMatch(c -> c.equalsIgnoreCase(col));
            if (!found) {
                return CommandResult.error("Column not found: " + col + " (table " + tableName + ")");
            }
        }

        try {
            BTreeWriter writer = new BTreeWriter(db);
            int rootPage = writer.createIndexRoot();
            long rowId = writer.nextRowId(1);
            List<Object> schemaRecord = List.of("index", indexName, tableName, (long) rootPage, fullCommand);
            writer.insertRowWithId(1, rowId, schemaRecord);
            return CommandResult.success((unique ? "UNIQUE index " : "Index ") + indexName + " created on " + tableName + ".");
        } catch (Exception e) {
            return CommandResult.error("CREATE INDEX failed: " + e.getMessage());
        }
    }

    private boolean tableExists(BTreeReader reader, String tableName) {
        return findTableSql(reader, tableName) != null;
    }

    private boolean indexExists(BTreeReader reader, String indexName) {
        return reader.readTable(1).stream()
                .filter(row -> "index".equals(row.columns().get(0)))
                .anyMatch(row -> indexName.equalsIgnoreCase((String) row.columns().get(1)));
    }

    private String findTableSql(BTreeReader reader, String tableName) {
        return reader.readTable(1).stream()
                .filter(row -> "table".equals(row.columns().get(0)) && tableName.equalsIgnoreCase((String) row.columns().get(1)))
                .map(row -> (String) row.columns().get(4))
                .findFirst()
                .orElse(null);
    }

    private List<String> parseColumnNames(String columnDefs) {
        List<String> names = new ArrayList<>();
        for (String part : columnDefs.split(",")) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length > 0 && tokens[0].matches("\\w+")) {
                names.add(tokens[0]);
            }
        }
        return names;
    }

    private List<String> extractTableColumns(String createSql) {
        List<String> columns = new ArrayList<>();
        if (createSql == null) return columns;
        Matcher m = Pattern.compile("\\((.*)\\)", Pattern.DOTALL).matcher(createSql);
        if (m.find()) {
            for (String part : m.group(1).split(",")) {
                String[] tokens = part.trim().split("\\s+");
                if (tokens.length > 0) columns.add(tokens[0]);
            }
        }
        return columns;
    }
}
