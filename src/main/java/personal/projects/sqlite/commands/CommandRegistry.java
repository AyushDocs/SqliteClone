package personal.projects.sqlite.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class CommandRegistry {
    private static final Map<String, Command> COMMANDS;

    static {
        Map<String, Command> map = new HashMap<>();
        
        ServiceLoader<Command> loader = ServiceLoader.load(Command.class);
        for (Command cmd : loader)
            map.put(cmd.getName().toLowerCase(), cmd);
        
        COMMANDS = Collections.unmodifiableMap(map);
    }

    public static Optional<Command> get(String name) {
        return Optional.ofNullable(COMMANDS.get(name.toLowerCase()));
    }

    public static Map<String, Command> getCommands() {
        return COMMANDS;
    }
    
    private CommandRegistry() {}
}
