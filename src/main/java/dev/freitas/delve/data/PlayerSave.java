package dev.freitas.delve.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One persisted save game, keyed by the owning Discord user id. The full B/X game state (character,
 * party of retainers, in-progress dungeon run, session state) is stored as a Jackson JSON blob in
 * {@code stateJson}; normalizing a single-player save relationally would be overkill.
 *
 * <p>Implements {@link Persistable} with a transient {@code isNew} flag so Spring Data JDBC issues an
 * INSERT for first-time saves even though the id is caller-assigned (cf. ukulele's GuildProperties).
 */
@Table("player_save")
public class PlayerSave implements Persistable<Long> {

    @Id
    @Column("discord_user_id")
    private final Long discordUserId;

    @Column("state_json")
    private String stateJson;

    @Transient
    private boolean isNew;

    public PlayerSave(Long discordUserId, String stateJson) {
        this.discordUserId = discordUserId;
        this.stateJson = stateJson;
    }

    public Long getDiscordUserId() {
        return discordUserId;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public PlayerSave markNew() {
        this.isNew = true;
        return this;
    }

    public PlayerSave markExisting() {
        this.isNew = false;
        return this;
    }

    @Override
    public Long getId() {
        return discordUserId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
