package dev.freitas.delve.api.dto;

import java.util.List;

/**
 * The JSON shape returned by every play/DM action endpoint: the narrated lines to render plus a
 * snapshot of state so the UI can update its panels without a second round-trip.
 */
public record ActionResult(boolean ok, List<String> lines, StateSnapshot state) {

    public static ActionResult of(boolean ok, List<String> lines, StateSnapshot state) {
        return new ActionResult(ok, lines, state);
    }
}
