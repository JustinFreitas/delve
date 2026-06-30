package dev.freitas.delve.game.model;

import dev.freitas.delve.game.engine.DamageRoll;
import java.util.EnumMap;
import java.util.Map;

/**
 * A single dungeon room: its description, cardinal exits, stocked contents (monster/trap/special),
 * any treasure, and the per-play flags that track what the delver has done here.
 */
public class Room {

    private int id;
    private String description;
    // Optional area label and boxed read-aloud text, populated when running an authored module.
    private String name;
    private String readAloud;
    private Map<Direction, Exit> exits = new EnumMap<>(Direction.class);

    private ContentType content = ContentType.EMPTY;

    // Monster occupant (looked up in the Bestiary by name when an encounter starts).
    private String monsterName;
    private int monsterCount;

    // Trap (hidden until detected; sprung once triggered).
    private boolean trapped;
    private boolean trapDetected;
    private boolean trapSprung;
    private String trapDescription;
    private DamageRoll trapDamage;

    // Treasure.
    private boolean hasTreasure;
    private int treasureGold;

    private String specialText;

    // Inter-level stairs.
    private boolean stairsUp;
    private boolean stairsDown;
    private int stairsDestinationLevel = -1;
    private int stairsDestinationRoomId = -1;

    // Per-play state.
    private boolean visited;
    private boolean searched;
    private boolean cleared;
    private boolean looted;

    public Room() {}

    public Room(int id) {
        this.id = id;
    }

    /** Whether an undefeated monster currently occupies this room. */
    public boolean hasLiveMonster() {
        return content == ContentType.MONSTER && monsterName != null && !cleared;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReadAloud() {
        return readAloud;
    }

    public void setReadAloud(String readAloud) {
        this.readAloud = readAloud;
    }

    public Map<Direction, Exit> getExits() {
        return exits;
    }

    public void setExits(Map<Direction, Exit> exits) {
        this.exits = exits;
    }

    public ContentType getContent() {
        return content;
    }

    public void setContent(ContentType content) {
        this.content = content;
    }

    public String getMonsterName() {
        return monsterName;
    }

    public void setMonsterName(String monsterName) {
        this.monsterName = monsterName;
    }

    public int getMonsterCount() {
        return monsterCount;
    }

    public void setMonsterCount(int monsterCount) {
        this.monsterCount = monsterCount;
    }

    public boolean isTrapped() {
        return trapped;
    }

    public void setTrapped(boolean trapped) {
        this.trapped = trapped;
    }

    public boolean isTrapDetected() {
        return trapDetected;
    }

    public void setTrapDetected(boolean trapDetected) {
        this.trapDetected = trapDetected;
    }

    public boolean isTrapSprung() {
        return trapSprung;
    }

    public void setTrapSprung(boolean trapSprung) {
        this.trapSprung = trapSprung;
    }

    public String getTrapDescription() {
        return trapDescription;
    }

    public void setTrapDescription(String trapDescription) {
        this.trapDescription = trapDescription;
    }

    public DamageRoll getTrapDamage() {
        return trapDamage;
    }

    public void setTrapDamage(DamageRoll trapDamage) {
        this.trapDamage = trapDamage;
    }

    public boolean isHasTreasure() {
        return hasTreasure;
    }

    public void setHasTreasure(boolean hasTreasure) {
        this.hasTreasure = hasTreasure;
    }

    public int getTreasureGold() {
        return treasureGold;
    }

    public void setTreasureGold(int treasureGold) {
        this.treasureGold = treasureGold;
    }

    public String getSpecialText() {
        return specialText;
    }

    public void setSpecialText(String specialText) {
        this.specialText = specialText;
    }

    public boolean isStairsUp() {
        return stairsUp;
    }

    public void setStairsUp(boolean stairsUp) {
        this.stairsUp = stairsUp;
    }

    public boolean isStairsDown() {
        return stairsDown;
    }

    public void setStairsDown(boolean stairsDown) {
        this.stairsDown = stairsDown;
    }

    public int getStairsDestinationLevel() {
        return stairsDestinationLevel;
    }

    public void setStairsDestinationLevel(int stairsDestinationLevel) {
        this.stairsDestinationLevel = stairsDestinationLevel;
    }

    public int getStairsDestinationRoomId() {
        return stairsDestinationRoomId;
    }

    public void setStairsDestinationRoomId(int stairsDestinationRoomId) {
        this.stairsDestinationRoomId = stairsDestinationRoomId;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public boolean isSearched() {
        return searched;
    }

    public void setSearched(boolean searched) {
        this.searched = searched;
    }

    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public boolean isLooted() {
        return looted;
    }

    public void setLooted(boolean looted) {
        this.looted = looted;
    }
}
