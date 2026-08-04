package personal.projects.sqlite;

import personal.projects.sqlite.entities.Database;

import java.util.Arrays;
import java.util.Scanner;

public class Main {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java AeroSQL <db_path> [command]");
            System.exit(1);
        }

        String dbPath = args[0];
        AeroSQL app = new AeroSQL();

        final Database database;
        try {
            database = Database.open(dbPath);
        } catch (Exception e) {
            System.err.println("Failed to open database: " + e.getMessage());
            System.exit(1);
            return;
        }

        try (database) {
            if (args.length == 1) {
                runRepl(app, database, dbPath);
            } else {
                String commandName = args[1];
                app.run(database, commandName, Arrays.asList(args).subList(2, args.length));
            }
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
        }
    }

    private static void runRepl(AeroSQL app, Database database, String dbPath) {
        System.out.println(GREEN + "Welcome to AeroSQL Engine (v1.0)" + RESET);
        System.out.println("Connected to: " + BLUE + dbPath + RESET);
        System.out.println("Type SQL or commands. Press " + GREEN + "Ctrl+D" + RESET + " to exit.");
        System.out.println();

        java.io.Console console = System.console();
        if (console == null) {
            // Fallback for non-interactive environments
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("aerosql> ");
                if (!scanner.hasNextLine()) break;
                app.runCommand(database, scanner.nextLine());
            }
            return;
        }

        while (true) {
            String line = console.readLine("aerosql> ");
            if (line == null) {
                System.out.println("\nGoodbye!");
                break;
            }

            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase(".exit") || line.equalsIgnoreCase(".quit")) {
                break;
            }

            try {
                app.runCommand(database, line);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}