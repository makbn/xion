package io.xion.infrastructure.store;

import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Typed({ContainerStore.class, SqliteContainerStore.class})
public class SqliteContainerStore implements ContainerStore {

    private final DataSource dataSource;

    @Inject
    public SqliteContainerStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void initSchema() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS containers (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL UNIQUE,
                      binary_path TEXT NOT NULL,
                      status TEXT NOT NULL,
                      runtime_dir TEXT NOT NULL,
                      pid INTEGER,
                      network TEXT,
                      created_at TEXT NOT NULL,
                      started_at TEXT,
                      stopped_at TEXT,
                      profile_json TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to init SQLite schema", e);
        }
    }

    @Override
    public void save(ContainerRecord record) {
        String sql = """
                INSERT INTO containers
                (id, name, binary_path, status, runtime_dir, pid, network, created_at, started_at, stopped_at, profile_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, record);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save container " + record.id(), e);
        }
    }

    @Override
    public void update(ContainerRecord record) {
        String sql = """
                UPDATE containers SET name=?, binary_path=?, status=?, runtime_dir=?, pid=?, network=?,
                created_at=?, started_at=?, stopped_at=?, profile_json=? WHERE id=?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, record.name());
            ps.setString(2, record.binary());
            ps.setString(3, record.status().name());
            ps.setString(4, record.runtimeDir());
            if (record.pid().isPresent()) {
                ps.setLong(5, record.pid().get());
            } else {
                ps.setObject(5, null);
            }
            ps.setString(6, record.network().orElse(null));
            ps.setString(7, record.createdAt().toString());
            ps.setString(8, record.startedAt().map(Instant::toString).orElse(null));
            ps.setString(9, record.stoppedAt().map(Instant::toString).orElse(null));
            ps.setString(10, record.profileJson());
            ps.setString(11, record.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update container " + record.id(), e);
        }
    }

    @Override
    public Optional<ContainerRecord> findById(String id) {
        return queryOne("SELECT * FROM containers WHERE id = ?", id);
    }

    @Override
    public Optional<ContainerRecord> findByName(String name) {
        return queryOne("SELECT * FROM containers WHERE name = ?", name);
    }

    @Override
    public List<ContainerRecord> listAll() {
        return queryMany("SELECT * FROM containers ORDER BY created_at DESC");
    }

    @Override
    public List<ContainerRecord> listByStatus(ContainerStatus status) {
        return queryMany("SELECT * FROM containers WHERE status = ? ORDER BY created_at DESC", status.name());
    }

    @Override
    public void delete(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM containers WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete container " + id, e);
        }
    }

    private void bind(PreparedStatement ps, ContainerRecord record) throws SQLException {
        ps.setString(1, record.id());
        ps.setString(2, record.name());
        ps.setString(3, record.binary());
        ps.setString(4, record.status().name());
        ps.setString(5, record.runtimeDir());
        if (record.pid().isPresent()) {
            ps.setLong(6, record.pid().get());
        } else {
            ps.setObject(6, null);
        }
        ps.setString(7, record.network().orElse(null));
        ps.setString(8, record.createdAt().toString());
        ps.setString(9, record.startedAt().map(Instant::toString).orElse(null));
        ps.setString(10, record.stoppedAt().map(Instant::toString).orElse(null));
        ps.setString(11, record.profileJson());
    }

    private Optional<ContainerRecord> queryOne(String sql, String arg) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private List<ContainerRecord> queryMany(String sql, String... args) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ContainerRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private static ContainerRecord map(ResultSet rs) throws SQLException {
        Long pid = rs.getObject("pid") == null ? null : rs.getLong("pid");
        String network = rs.getString("network");
        String started = rs.getString("started_at");
        String stopped = rs.getString("stopped_at");
        return new ContainerRecord(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("binary_path"),
                ContainerStatus.valueOf(rs.getString("status")),
                rs.getString("runtime_dir"),
                Optional.ofNullable(pid),
                Optional.ofNullable(network),
                Instant.parse(rs.getString("created_at")),
                started == null ? Optional.empty() : Optional.of(Instant.parse(started)),
                stopped == null ? Optional.empty() : Optional.of(Instant.parse(stopped)),
                rs.getString("profile_json"));
    }
}
