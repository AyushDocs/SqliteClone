package personal.projects.sqlite.commands;

import java.util.HashMap;
import java.util.Map;

public class CommandMap {
    public static Map<String, AbstractCommand> getCommands() {
        Map<String,AbstractCommand> commands = new HashMap<String,AbstractCommand>();
        AbstractCommand dbinfo=new DbInfoCommand();
        commands.put(dbinfo.getName(),dbinfo);
        return commands;
    }
}
