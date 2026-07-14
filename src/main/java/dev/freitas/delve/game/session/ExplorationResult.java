package dev.freitas.delve.game.session;

import java.util.ArrayList;
import java.util.List;

/** The narrated outcome of an exploration action — a list of message lines for the command to render. */
public class ExplorationResult {

    private final List<String> lines = new ArrayList<>();
    private boolean success = true;

    public static ExplorationResult of(String line) {
        return new ExplorationResult().add(line);
    }

    public static ExplorationResult failure(String line) {
        ExplorationResult r = new ExplorationResult();
        r.success = false;
        return r.add(line);
    }

    /** Appends a line; an empty string is a deliberate blank separator line, only null is skipped. */
    public ExplorationResult add(String line) {
        if (line != null) {
            lines.add(line);
        }
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getLines() {
        return lines;
    }

    public String text() {
        return String.join("\n", lines);
    }
}
