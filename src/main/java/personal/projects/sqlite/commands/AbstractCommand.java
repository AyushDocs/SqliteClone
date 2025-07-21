package personal.projects.sqlite.commands;

public abstract class AbstractCommand {
    public abstract String getName();
    public abstract String execute(String[] args);
}
