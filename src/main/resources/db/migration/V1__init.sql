-- One row per player save, keyed by Discord user id. The full B/X game state is held as a JSON blob.
CREATE TABLE IF NOT EXISTS player_save (
    discord_user_id BIGINT      NOT NULL PRIMARY KEY,
    state_json      CLOB
);
