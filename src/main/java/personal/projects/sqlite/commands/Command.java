package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.Database;

public abstract class Command {
    public abstract String getName();
    public abstract String execute(String[] args, Database database);
}