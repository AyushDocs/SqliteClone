package personal.projects.sqlite.commands;

public interface Command {
    String getName();
    CommandResult execute(CommandContext context);
}