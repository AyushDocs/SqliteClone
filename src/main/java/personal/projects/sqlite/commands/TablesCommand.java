package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.BTreeReader;
import personal.projects.sqlite.entities.Database;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to list all tables in the database.
 * Uses BTreeReader to traverse the master schema table correctly.
 */
public class TablesCommand implements Command {

    @Override
    public String getName() {
        return ".tables";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        Database db = context.database();
        BTreeReader reader = new BTreeReader(db.memoryMap, db.pageSize);
        
        // Root page 1 is always the sqlite_schema table
        List<BTreeReader.Row> schemaRows = reader.readTable(1);
        
        // Filter for type == 'table' and extract the table name (column index 1)
        String tableList = schemaRows.stream()
            .filter(row -> "table".equals(row.columns().get(0)))
            .map(row -> (String) row.columns().get(1))
            .filter(name -> !name.startsWith("sqlite_")) // Hide internal tables
            .collect(Collectors.joining("  "));

        if (tableList.isEmpty()) {
            return CommandResult.success("No tables found.");
        }

        return CommandResult.success(tableList);
    }
}
