package dev.freitas.delve.game.model;

import java.util.ArrayList;
import java.util.List;

/** A multi-level dungeon, levels ordered from shallowest (index 0, depth 1) to deepest. */
public class Dungeon {

    private List<DungeonLevel> levels = new ArrayList<>();

    public Dungeon() {}

    public DungeonLevel level(int index) {
        return levels.get(index);
    }

    public int levelCount() {
        return levels.size();
    }

    public void addLevel(DungeonLevel level) {
        levels.add(level);
    }

    public List<DungeonLevel> getLevels() {
        return levels;
    }

    public void setLevels(List<DungeonLevel> levels) {
        this.levels = levels;
    }
}
