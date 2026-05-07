package com.mdt.jump.storage;

import com.mdt.jump.api.ComIdRecord;
import com.mdt.jump.config.PluginConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class RemoteDatabaseStorage implements ComIdStorage {
    private final PluginConfiguration configuration;
    private final String tableName;

    public RemoteDatabaseStorage(PluginConfiguration configuration) throws Exception {
        this.configuration = configuration;
        this.tableName = configuration.getRemoteTableName();
        initializeDriver();
        ensureTable();
    }

    @Override
    public Optional<String> findComIdByUuid(String uuid) throws SQLException {
        String sql = "SELECT com_id FROM " + tableName + " WHERE uuid = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString(1));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> findUuidByComId(String comId) throws SQLException {
        String sql = "SELECT uuid FROM " + tableName + " WHERE com_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, comId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString(1));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(ComIdRecord record) throws SQLException {
        try (Connection connection = openConnection()) {
            int updated = updateRecord(connection, record);
            if (updated == 0) {
                insertRecord(connection, record);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
            configuration.getRemoteJdbcUrl(),
            configuration.getRemoteUsername(),
            configuration.getRemotePassword()
        );
    }

    private void initializeDriver() throws ClassNotFoundException {
        String driverClassName = configuration.getRemoteDriverClassName();
        if (!driverClassName.isEmpty()) {
            Class.forName(driverClassName);
        }
    }

    private void ensureTable() throws SQLException {
        String sql =
            "CREATE TABLE IF NOT EXISTS " + tableName +
            " (uuid VARCHAR(64) PRIMARY KEY, com_id VARCHAR(16) NOT NULL UNIQUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int updateRecord(Connection connection, ComIdRecord record) throws SQLException {
        String sql = "UPDATE " + tableName + " SET com_id = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getComId());
            statement.setString(2, record.getUuid());
            return statement.executeUpdate();
        }
    }

    private void insertRecord(Connection connection, ComIdRecord record) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (uuid, com_id) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getUuid());
            statement.setString(2, record.getComId());
            statement.executeUpdate();
        }
    }
}
