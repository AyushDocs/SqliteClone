package personal.projects.sqlite;

import personal.projects.sqlite.commands.Command;
import personal.projects.sqlite.commands.CommandMap;
import personal.projects.sqlite.entities.Database;
import personal.projects.sqlite.exceptions.CommandNotFound;
import personal.projects.sqlite.exceptions.ReducedNumberOfExceptions;

import java.util.Map;

public class Main {
  public static void main(String[] args){
    requireCommandInDbFile(args);
    String command = getCommandName(args);
    Map<String, Command> commandMap = CommandMap.getCommands();
    requireCommandInKnownCommands(commandMap, command);
    String dbfilePath = getDatabaseFileName(args);
    Database database=new Database(dbfilePath);
    String output=commandMap.get(command).execute(args,database);
    System.out.println(output);
  }

  private static String getCommandName(String[] args) {
      return args[1];
  }

  private static String getDatabaseFileName(String[] args) {
      return args[0];
  }

  private static void requireCommandInKnownCommands(Map<String, Command> commandMap, String command) {
    if(!commandMap.containsKey(command)){
        System.out.println("Missing or invalid command passed: " + command);
        throw new CommandNotFound(command);
    }
  }

  private static void requireCommandInDbFile(String[] args){
    if (args.length < 2)
      throw new ReducedNumberOfExceptions("Missing <database path> and <command>");
  }
}
