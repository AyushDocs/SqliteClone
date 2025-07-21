package personal.projects.sqlite;

import personal.projects.sqlite.commands.AbstractCommand;
import personal.projects.sqlite.commands.CommandMap;
import java.util.Map;

public class Main {
  public static void main(String[] args){
    if (args.length < 2) {
      System.out.println("Missing <database path> and <command>");
      return;
    }

    String command = args[1];
    Map<String, AbstractCommand> commandMap = CommandMap.getCommands();
    if(!commandMap.containsKey(command)){
        System.out.println("Missing or invalid command passed: " + command);
        return;
    }
    String output=commandMap.get(command).execute(args);
    System.out.println(output);
  }
}
