package personal.projects.sqlite;

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

        if (args.length == 1) {
            runRepl(app, dbPath);
        } else {
            String commandName = args[1];
            app.run(dbPath, commandName, Arrays.asList(args).subList(2, args.length));
        }
    }

    private static void runRepl(AeroSQL app, String dbPath) {
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
                app.runCommand(dbPath, scanner.nextLine());
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
                app.runCommand(dbPath, line);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}
