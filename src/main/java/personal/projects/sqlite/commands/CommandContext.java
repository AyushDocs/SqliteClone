package personal.projects.sqlite.commands;

import personal.projects.sqlite.entities.Database;
import java.util.Map;
import java.util.List;

/**
 * Encapsulates the environment in which a command runs.
 */
public record CommandContext(
    Database database,
    List<String> parameters,
    Map<String, String> flags
) {}
