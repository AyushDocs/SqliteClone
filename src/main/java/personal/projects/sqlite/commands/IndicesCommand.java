package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.BTreeReader;
import personal.projects.sqlite.entities.Database;
import java.util.List;
import java.util.stream.Collectors;

/**
 * List all indices in the database or for a specific table.
 * Usage: .indices [table_name]
 */
public class IndicesCommand implements Command {

    @Override
    public String getName() {
        return ".indices";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        Database db = context.database();
        BTreeReader bTreeReader = new BTreeReader(db.memoryMap, db.pageSize);

        // Read the master schema
        List<BTreeReader.Row> schemaRows = bTreeReader.readTable(1);
        
        String filterTable = context.parameters().isEmpty() ? null : context.parameters().get(0);

        List<String> indices = schemaRows.stream()
                .filter(row -> "index".equals(row.columns().get(0)))
                .filter(row -> filterTable == null || filterTable.equalsIgnoreCase((String) row.columns().get(2)))
                .map(row -> (String) row.columns().get(1))
                .collect(Collectors.toList());

        if (indices.isEmpty()) {
            return CommandResult.success("No indices found" + (filterTable != null ? " for table: " + filterTable : ""));
        }

        return CommandResult.success(String.join("\n", indices));
    }
}
