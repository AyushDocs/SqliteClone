package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.Database;

public class DbInfoCommand extends Command {
    @Override
    public String getName() {
        return  ".dbinfo";
    }

    @Override
    public String execute(String[] args, Database database) {
        StringBuilder response=new StringBuilder();
        database.headerFields.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue()+"\n")
                .forEach(response::append);
        return response.toString();
    }
}

