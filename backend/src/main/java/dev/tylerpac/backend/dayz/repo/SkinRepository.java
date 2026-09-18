package dev.tylerpac.backend.dayz.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;
import dev.tylerpac.backend.dayz.model.Skin;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class SkinRepository {

    private static final String COLUMNS =
        "id, skin_key, weapon_type, display_name, textures, materials, skin_type, variant_class";

    private static final RowMapper<Skin> MAPPER = (rs, rowNum) -> new Skin(
        rs.getLong("id"),
        rs.getString("skin_key"),
        rs.getString("weapon_type"),
        rs.getString("display_name"),
        rs.getString("textures"),
        rs.getString("materials"),
        rs.getString("skin_type"),
        rs.getString("variant_class"));

    private final JdbcClient jdbc;

    public SkinRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    public List<Skin> findAllEnabled() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM skins WHERE enabled = TRUE ORDER BY id").query(MAPPER).list();
    }

    public Optional<Skin> findEnabledById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM skins WHERE id = :id AND enabled = TRUE")
            .param("id", id)
            .query(MAPPER)
            .optional();
    }

    public Optional<Skin> findEnabledByKey(String skinKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM skins WHERE skin_key = :skinKey AND enabled = TRUE")
            .param("skinKey", skinKey)
            .query(MAPPER)
            .optional();
    }
}
