package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.Database;

public class TablesCommand extends Command {

    @Override
    public String getName() {
        return ".tables";
    }

    @Override
    public String execute(String[] args, Database database) {
        return String.join(" ", database.tableNames);
    }

}
