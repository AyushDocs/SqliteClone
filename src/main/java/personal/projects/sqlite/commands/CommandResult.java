package personal.projects.sqlite.commands;

/**
 * Represents the outcome of a command execution.
 */
public record CommandResult(String output, boolean success) {

    public static CommandResult success(String output) {
        return new CommandResult(output, true);
    }

    public static CommandResult error(String message) {
        return new CommandResult(message, false);
    }

    @Override
    public String toString() {
        return this.output;
    }
}
