package personal.projects.sqlite;

import personal.projects.sqlite.commands.*;
import personal.projects.sqlite.entities.Database;
import personal.projects.sqlite.exceptions.CommandNotFound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The core AeroSQL Engine application.
 */
public class AeroSQL {

    /**
     * Entry point for a single command line (used by REPL).
     */
    public void runCommand(String dbPath, String inputLine) {
        if (inputLine == null || inputLine.trim().isEmpty()) return;

        String[] tokens = inputLine.trim().split("\\s+");
        String firstToken = tokens[0];
        List<String> remainingTokens = new ArrayList<>(Arrays.asList(tokens).subList(1, tokens.length));

        run(dbPath, firstToken, remainingTokens);
    }

    public void run(String dbPath, String commandName, List<String> parameters) {
        String baseCommandName = commandName;
        List<String> actualParameters = new ArrayList<>(parameters);

        if (commandName.contains(" ")) {
            String[] parts = commandName.split("\\s+", 2);
            baseCommandName = parts[0];
            actualParameters.addAll(0, Arrays.asList(parts[1].split("\\s+")));
        }

        final String finalCommandName = baseCommandName;
        Command command = CommandRegistry.get(finalCommandName)
                .orElseThrow(() -> new CommandNotFound(finalCommandName));

        try (Database database = Database.open(dbPath)) {
            CommandResult result = database.execute(command, actualParameters);
            
            if (result.success()) {
                System.out.println(result.output());
            } else {
                System.err.println("Error: " + result.output());
            }
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
        }
    }
}
