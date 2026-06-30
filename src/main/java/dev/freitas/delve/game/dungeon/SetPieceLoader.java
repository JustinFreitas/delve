package dev.freitas.delve.game.dungeon;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads authored set-pieces from {@code classpath:content/setpieces.json}. Static and cached so the
 * generator can stamp them without a constructor dependency; a missing/blank file just yields none.
 */
public final class SetPieceLoader {

    private static final Logger log = LoggerFactory.getLogger(SetPieceLoader.class);
    private static List<SetPiece> cache;

    private SetPieceLoader() {}

    public static synchronized List<SetPiece> load() {
        if (cache != null) {
            return cache;
        }
        try (InputStream in = SetPieceLoader.class.getResourceAsStream("/content/setpieces.json")) {
            if (in == null) {
                cache = List.of();
            } else {
                cache = List.of(new ObjectMapper().readValue(in, SetPiece[].class));
            }
        } catch (Exception e) {
            log.warn("Could not load set-pieces; proceeding with none", e);
            cache = List.of();
        }
        return cache;
    }
}
