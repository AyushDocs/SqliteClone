package personal.projects.sqlite.exceptions;

public class CommandNotFound extends RuntimeException {
    public CommandNotFound(String message) {
        super(message);
    }
}
