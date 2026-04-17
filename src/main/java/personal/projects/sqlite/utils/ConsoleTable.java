package personal.projects.sqlite.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A sleek CLI table formatter with ANSI color support and Unicode borders.
 */
public class ConsoleTable {

    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GRAY = "\u001B[90m";

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();

    public ConsoleTable(List<String> headers) {
        this.headers = headers;
    }

    public void addRow(List<Object> columns) {
        rows.add(columns.stream()
                .map(c -> c == null ? "NULL" : c.toString())
                .collect(Collectors.toList()));
    }

    public String format() {
        if (headers.isEmpty()) return "";

        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i < widths.length) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        
        // 1. Top Border
        sb.append(GRAY).append(drawEdge(widths, "┌", "┬", "┐")).append(RESET).append("\n");

        // 2. Headers
        sb.append(GRAY).append("│ ").append(RESET);
        for (int i = 0; i < headers.size(); i++) {
            sb.append(CYAN).append(pad(headers.get(i), widths[i])).append(RESET);
            sb.append(GRAY).append(" │ ").append(RESET);
        }
        sb.append("\n");

        // 3. Header Separator
        sb.append(GRAY).append(drawEdge(widths, "├", "┼", "┤")).append(RESET).append("\n");

        // 4. Data Rows
        for (List<String> row : rows) {
            sb.append(GRAY).append("│ ").append(RESET);
            for (int i = 0; i < row.size(); i++) {
                String val = row.get(i);
                String color = val.equals("NULL") ? YELLOW : RESET;
                sb.append(color).append(pad(val, widths[i])).append(RESET);
                sb.append(GRAY).append(" │ ").append(RESET);
            }
            sb.append("\n");
        }

        // 5. Bottom Border
        sb.append(GRAY).append(drawEdge(widths, "└", "┴", "┘")).append(RESET).append("\n");

        return sb.toString();
    }

    private String drawEdge(int[] widths, String left, String mid, String right) {
        StringBuilder sb = new StringBuilder(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append(mid);
        }
        sb.append(right);
        return sb.toString();
    }

    private String pad(String text, int width) {
        return String.format("%-" + width + "s", text);
    }
}
