package personal.projects.sqlite.commands;

import java.util.HashMap;
import java.util.Map;

public class CommandMap {

    private CommandMap(){}

    public static Map<String, Command> getCommands() {
        Map<String, Command> commands = new HashMap<>();
        Command dbinfo=new DbInfoCommand();
        Command tables=new TablesCommand();
        commands.put(dbinfo.getName(),dbinfo);
        commands.put(tables.getName(),tables);
        return commands;
    }
}
