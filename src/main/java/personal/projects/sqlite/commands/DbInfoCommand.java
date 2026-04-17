package personal.projects.sqlite.commands;


public class DbInfoCommand implements Command {
    @Override
    public String getName() {
        return ".dbinfo";
    }

    @Override
    public CommandResult execute(CommandContext context) {
        StringBuilder response = new StringBuilder();
        context.database().header.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue() + "\n")
                .forEach(response::append);
        return CommandResult.success(response.toString().trim());
    }
}

