package com.filesorter;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataRepository {

    public void addRule(String extension, String targetDir) {
        String sql = "INSERT OR REPLACE INTO rules (file_extension, target_directory) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, extension.toLowerCase());
            pstmt.setString(2, targetDir);
            pstmt.executeUpdate();
            System.out.println("✅ Rule added: " + extension + " -> " + targetDir);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getRuleDirectory(String extension) {
        String sql = "SELECT target_directory FROM rules WHERE file_extension = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, extension.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("target_directory");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addTag(String filePath, String tagName) {
        String tagSql = "INSERT OR IGNORE INTO tags (name) VALUES (?)";
        String linkSql = "INSERT OR IGNORE INTO file_tags (file_path, tag_name) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement tagStmt = conn.prepareStatement(tagSql)) {
                tagStmt.setString(1, tagName.toLowerCase());
                tagStmt.executeUpdate();
            }
            try (PreparedStatement linkStmt = conn.prepareStatement(linkSql)) {
                linkStmt.setString(1, filePath);
                linkStmt.setString(2, tagName.toLowerCase());
                linkStmt.executeUpdate();
            }
            System.out.println("✅ Tag '" + tagName + "' added to " + filePath);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getTagsForFile(String filePath) {
        List<String> tags = new ArrayList<>();
        String sql = "SELECT tag_name FROM file_tags WHERE file_path = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) tags.add(rs.getString("tag_name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tags;
    }

    public int createBatch() throws SQLException {
        String sql = "INSERT INTO sort_batches (status) VALUES ('COMPLETED')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    public void logAction(int batchId, String originalPath, String newPath) {
        String sql = "INSERT INTO action_log (batch_id, original_path, new_path) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, batchId);
            pstmt.setString(2, originalPath);
            pstmt.setString(3, newPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void listRules() {
        String sql = "SELECT file_extension, target_directory FROM rules ORDER BY file_extension";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            System.out.println("\n📋 Active Sorting Rules:");
            System.out.println("-----------------------------------");
            System.out.printf("%-15s | %-15s%n", "Extension", "Target Directory");
            System.out.println("-----------------------------------");

            boolean hasRules = false;
            while (rs.next()) {
                hasRules = true;
                System.out.printf("%-15s | %-15s%n",
                    rs.getString("file_extension"),
                    rs.getString("target_directory"));
            }

            if (!hasRules) System.out.println("  No rules found. Add one first!");
            System.out.println("-----------------------------------\n");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}  // ← single closing brace for the entire class