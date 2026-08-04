package personal.projects.sqlite;

import personal.projects.sqlite.entities.Database;

import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

public class Main {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java AeroSQL <db_path> [command]");
            System.err.println("       java AeroSQL <db_path> \"cmd1; cmd2; ...\"");
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
                // Join remaining args and split on semicolons for multi-command support
                String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                for (String cmd : input.split(";")) {
                    cmd = cmd.trim();
                    if (!cmd.isEmpty()) {
                        app.runCommand(database, cmd);
                    }
                }
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

        Console console = System.console();
        if (console == null) {
            // Fallback for non-interactive environments
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("aerosql> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase(".exit") || line.equalsIgnoreCase(".quit")) break;
                try {
                    app.runCommand(database, line);
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
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