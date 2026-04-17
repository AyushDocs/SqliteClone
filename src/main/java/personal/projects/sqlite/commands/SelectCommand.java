package personal.projects.sqlite.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import personal.projects.sqlite.entities.BTreeReader;
import personal.projects.sqlite.entities.Database;
import personal.projects.sqlite.utils.ConsoleTable;

/**
 * A sophisticated SELECT command with pretty-printed output.
 */
public class SelectCommand implements Command {

    @Override
    public String getName() {
        return "SELECT";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        List<String> params = context.parameters();
        String tableName = null;
        for (int i = 0; i < params.size(); i++) {
            if ("FROM".equalsIgnoreCase(params.get(i)) && i + 1 < params.size()) {
                tableName = params.get(i + 1);
                break;
            }
        }

        if (tableName == null) {
            return CommandResult.error("Could not find table name. Syntax: SELECT * FROM <tableName>");
        }

        Database db = context.database();
        BTreeReader bTreeReader = new BTreeReader(db.memoryMap, db.pageSize);

        // 1. Discover the table in the schema
        final String finalTableName = tableName;
        List<BTreeReader.Row> schemaRows = bTreeReader.readTable(1);
        Optional<BTreeReader.Row> tableEntry = schemaRows.stream()
                .filter(row -> "table".equals(row.columns().get(0)) && finalTableName.equalsIgnoreCase((String) row.columns().get(1)))
                .findFirst();

        if (tableEntry.isEmpty()) {
            return CommandResult.error("Table not found: " + tableName);
        }

        // 2. Extract rootpage and SQL definition
        int rootPage = (int) (long) tableEntry.get().columns().get(3);
        String createSql = (String) tableEntry.get().columns().get(4);

        // 3. Extract Column Names (Best effort)
        List<String> headers = extractHeaders(createSql);

        // 4. Read data rows
        List<BTreeReader.Row> dataRows = bTreeReader.readTable(rootPage);

        if (dataRows.isEmpty()) {
            return CommandResult.success("Empty set.");
        }

        // 5. Build and format table
        ConsoleTable table = new ConsoleTable(headers);
        for (BTreeReader.Row row : dataRows) {
            List<Object> displayCols = new ArrayList<>();
            displayCols.add(row.rowId());
            displayCols.addAll(row.columns());
            table.addRow(displayCols);
        }

        return CommandResult.success(table.format());
    }

    private List<String> extractHeaders(String sql) {
        List<String> headers = new ArrayList<>();
        headers.add("rowid"); // SQLite always has a rowid hidden or explicit
        
        try {
            Pattern p = Pattern.compile("\\((.*)\\)", Pattern.DOTALL);
            Matcher m = p.matcher(sql);
            if (m.find()) {
                String columnDefs = m.group(1);
                String[] parts = columnDefs.split(",");
                for (String part : parts) {
                    headers.add(part.trim().split("\\s+")[0]);
                }
            }
        } catch (Exception e) {
            // Fallback to generic headers if SQL is too complex
            headers.add("data...");
        }
        return headers;
    }
}
