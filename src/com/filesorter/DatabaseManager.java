package com.filesorter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:filesorter.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "file_extension TEXT NOT NULL UNIQUE, " +
                    "target_directory TEXT NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS file_metadata (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "file_path TEXT NOT NULL UNIQUE, " +
                    "file_name TEXT NOT NULL, " +
                    "extension TEXT, size_bytes INTEGER, " +
                    "sha256_hash TEXT, indexed_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS sort_batches (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "started_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "status TEXT DEFAULT 'COMPLETED')");

            stmt.execute("CREATE TABLE IF NOT EXISTS action_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "batch_id INTEGER NOT NULL, " +
                    "original_path TEXT NOT NULL, " +
                    "new_path TEXT NOT NULL, " +
                    "status TEXT DEFAULT 'MOVED', " +
                    "FOREIGN KEY(batch_id) REFERENCES sort_batches(id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS tags (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS file_tags (" +
                    "file_path TEXT NOT NULL, " +
                    "tag_name TEXT NOT NULL, " +
                    "PRIMARY KEY (file_path, tag_name))");

            System.out.println("✅ Database schema initialized successfully.");
        } catch (SQLException e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
        }
    }
}