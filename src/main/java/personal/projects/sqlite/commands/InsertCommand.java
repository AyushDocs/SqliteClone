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
            "INSERT\\s+INTO\\s+(\\w+)(?:\\s*\\(([^)]*)\\))?\\s+VALUES\\s*\\((.*)\\)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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
        String columnListStr = matcher.group(2);
        String valuesStr = matcher.group(3);
        List<Object> values = parseValues(valuesStr);

        Database db = context.database();
        BTreeReader bTreeReader = new BTreeReader(db.memoryMap, db.pageSize);

        // 1. Find root page of the table and its schema row
        List<BTreeReader.Row> schemaRows = bTreeReader.readTable(1);
        Optional<BTreeReader.Row> tableEntry = schemaRows.stream()
                .filter(row -> "table".equals(row.columns().get(0)) && tableName.equalsIgnoreCase((String) row.columns().get(1)))
                .findFirst();

        if (tableEntry.isEmpty()) {
            return CommandResult.error("Table not found: " + tableName);
        }

        int rootPage = (int) (long) tableEntry.get().columns().get(3);
        List<String> tableColumns = extractTableColumns((String) tableEntry.get().columns().get(4));

        try {
            // Align values with the table's declared column order.
            if (columnListStr != null && !columnListStr.isBlank()) {
                values = reorderByColumnList(columnListStr, values, tableColumns);
            } else if (values.size() < tableColumns.size()) {
                List<Object> padded = new ArrayList<>(values);
                while (padded.size() < tableColumns.size()) padded.add(null);
                values = padded;
            } else if (values.size() > tableColumns.size()) {
                return CommandResult.error("Table " + tableName + " has " + tableColumns.size()
                        + " columns but " + values.size() + " values were supplied");
            }

            BTreeWriter writer = new BTreeWriter(db);

            // 2. Collect the indexes that must be kept in sync, resolved before the row is written.
            List<IndexInfo> indexes = resolveIndexes(schemaRows, tableName, tableColumns);

            // 3. Assign the rowid up front so index entries can be written first.
            long rowId = writer.nextRowId(rootPage);
            for (IndexInfo index : indexes) {
                List<Object> keyValues = new ArrayList<>();
                for (String col : index.columns()) {
                    Integer idx = columnIndex(tableColumns, col);
                    if (idx == null || idx >= values.size()) {
                        return CommandResult.error("Cannot maintain index " + index.name()
                                + ": column '" + col + "' missing from INSERT.");
                    }
                    keyValues.add(values.get(idx));
                }
                if (index.unique() && writer.indexHasKeyPrefix(index.rootPage(), keyValues)) {
                    return CommandResult.error("UNIQUE constraint failed: " + index.name());
                }
                writer.insertIndexKey(index.rootPage(), keyValues, rowId);
            }

            // 4. Perform the table insertion
            writer.insertRowWithId(rootPage, rowId, values);
            return CommandResult.success("1 row inserted into " + tableName);
        } catch (Exception e) {
            return CommandResult.error("Insertion failed: " + e.getMessage());
        }
    }

    private record IndexInfo(String name, int rootPage, boolean unique, List<String> columns) {}

    /** Reorders VALUES to match the table's column order using an explicit INSERT column list. */
    private List<Object> reorderByColumnList(String columnListStr, List<Object> values, List<String> tableColumns) {
        List<String> named = new ArrayList<>();
        for (String part : columnListStr.split(",")) {
            named.add(part.trim());
        }
        if (named.size() != values.size()) {
            throw new IllegalArgumentException("Column list has " + named.size()
                    + " names but " + values.size() + " values were supplied");
        }
        List<Object> ordered = new ArrayList<>();
        for (String col : tableColumns) {
            Integer idx = columnIndex(named, col);
            ordered.add(idx == null ? null : values.get(idx));
        }
        return ordered;
    }

    /** Finds every index on the table and parses its column list from the CREATE INDEX SQL. */
    private List<IndexInfo> resolveIndexes(List<BTreeReader.Row> schemaRows, String tableName, List<String> tableColumns) {
        List<IndexInfo> indexes = new ArrayList<>();
        for (BTreeReader.Row row : schemaRows) {
            if (!"index".equals(row.columns().get(0))) continue;
            String tblName = (String) row.columns().get(2);
            if (!tableName.equalsIgnoreCase(tblName)) continue;
            String sql = (String) row.columns().get(4);
            Matcher m = Pattern.compile(
                    "CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\\w+\\s+ON\\s+\\w+\\s*\\((.+)\\)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
            if (!m.find()) {
                throw new IllegalStateException("Cannot parse CREATE INDEX statement: " + sql);
            }
            boolean unique = m.group(1) != null;
            List<String> columns = new ArrayList<>();
            for (String part : m.group(2).split(",")) {
                String col = part.trim();
                if (!col.matches("\\w+")) {
                    throw new IllegalStateException(
                            "Index " + row.columns().get(1) + " uses an expression; unsupported for INSERT.");
                }
                columns.add(col);
            }
            indexes.add(new IndexInfo((String) row.columns().get(1), (int) (long) row.columns().get(3), unique, columns));
        }
        return indexes;
    }

    private Integer columnIndex(List<String> tableColumns, String col) {
        for (int i = 0; i < tableColumns.size(); i++) {
            if (tableColumns.get(i).equalsIgnoreCase(col)) return i;
        }
        return null;
    }

    private List<String> extractTableColumns(String createSql) {
        List<String> columns = new ArrayList<>();
        Matcher m = Pattern.compile("\\((.*)\\)", Pattern.DOTALL).matcher(createSql);
        if (m.find()) {
            for (String part : m.group(1).split(",")) {
                String[] tokens = part.trim().split("\\s+");
                if (tokens.length > 0) columns.add(tokens[0]);
            }
        }
        return columns;
    }

    /**
     * Splits a VALUES clause into typed values, honoring single-quoted strings,
     * doubled-quote escapes ({@code ''}), and commas inside strings.
     */
    private List<Object> parseValues(String valuesStr) {
        List<Object> values = new ArrayList<>();
        int i = 0;
        int n = valuesStr.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(valuesStr.charAt(i))) i++;
            if (i >= n) break;

            if (valuesStr.charAt(i) == '\'') {
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    if (valuesStr.charAt(i) == '\'') {
                        if (i + 1 < n && valuesStr.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                        } else {
                            i++;
                            closed = true;
                            break;
                        }
                    } else {
                        sb.append(valuesStr.charAt(i));
                        i++;
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException("Unterminated string literal");
                }
                values.add(sb.toString());
            } else {
                int start = i;
                while (i < n && valuesStr.charAt(i) != ',') i++;
                String token = valuesStr.substring(start, i).trim();
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("Empty value in VALUES clause");
                }
                values.add(parseToken(token));
            }

            if (i < n && valuesStr.charAt(i) == ',') i++;
        }
        return values;
    }

    private Object parseToken(String token) {
        if (token.equalsIgnoreCase("NULL")) return null;
        if (token.matches("[-+]?\\d+")) return Long.valueOf(token);
        if (token.matches("[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+[eE][-+]?\\d+|\\d+\\.\\d*[eE][-+]?\\d+)")) {
            return Double.valueOf(token);
        }
        return token;
    }
}
