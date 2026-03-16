package com.filesorter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Scanner;

public class SortingEngine {
    private final DataRepository repo = new DataRepository();
    private final Scanner scanner = new Scanner(System.in);

    public void sortDirectory(String dirPath) {
        Path startPath = Paths.get(dirPath);
        if (!Files.exists(startPath) || !Files.isDirectory(startPath)) {
            System.out.println("❌ Invalid directory path.");
            return;
        }

        try {
            int batchId = repo.createBatch();
            System.out.println("🚀 Starting sort batch #" + batchId);

            Files.walk(startPath)
                 .filter(Files::isRegularFile)
                 .forEach(file -> processFile(file, startPath, batchId));

            System.out.println("✅ Sorting complete!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processFile(Path file, Path rootDir, int batchId) {
        try {
            String hash = calculateSHA256(file);
            if (handleDuplicate(file, hash)) return; // Skip if user deleted it or chose to skip

            String fileName = file.getFileName().toString();
            String ext = getExtension(fileName);
            
            String ruleDir = repo.getRuleDirectory(ext);
            List<String> tags = repo.getTagsForFile(file.toAbsolutePath().toString());
            String targetDirName = null;

            // Conflict resolution: Rule vs Tag
            if (ruleDir != null && !tags.isEmpty()) {
                System.out.println("⚠️ Conflict for " + fileName + ": Rule (" + ruleDir + ") OR Tag (" + tags.get(0) + ")?");
                System.out.print("Type [r] for rule, [t] for tag: ");
                String choice = scanner.nextLine().trim().toLowerCase();
                targetDirName = choice.equals("t") ? tags.get(0) : ruleDir;
            } else if (!tags.isEmpty()) {
                targetDirName = tags.get(0); // Use first tag as folder name
            } else if (ruleDir != null) {
                targetDirName = ruleDir;
            }

            if (targetDirName != null) {
                Path targetPath = rootDir.resolve(targetDirName).resolve(fileName);
                Files.createDirectories(targetPath.getParent());
                Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                repo.logAction(batchId, file.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString());
                System.out.println("📂 Moved: " + fileName + " -> " + targetDirName + "/");
            }

        } catch (Exception e) {
            System.out.println("❌ Error processing " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private boolean handleDuplicate(Path file, String hash) throws Exception {
        String sql = "SELECT file_path FROM file_metadata WHERE sha256_hash = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String existing = rs.getString("file_path");
                System.out.println("👯 Duplicate detected: " + file.getFileName());
                System.out.println("   Matches existing file: " + existing);
                System.out.print("   [d]elete, [r]ename with _copy1, or [s]kip? ");
                String choice = scanner.nextLine().trim().toLowerCase();
                
                if (choice.equals("d")) {
                    Files.delete(file);
                    System.out.println("🗑️ Deleted duplicate.");
                    return true;
                } else if (choice.equals("r")) {
                    String newName = file.getFileName().toString().replaceFirst("(\\.[^.]*)?$", "_copy1$1");
                    Path newPath = file.resolveSibling(newName);
                    Files.move(file, newPath);
                    System.out.println("📝 Renamed to " + newName);
                    return false; // Continue sorting with new name
                } else {
                    return true; // Skip
                }
            } else {
                // Save new metadata
                String insertSql = "INSERT INTO file_metadata (file_path, file_name, extension, sha256_hash) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, file.toAbsolutePath().toString());
                    insertStmt.setString(2, file.getFileName().toString());
                    insertStmt.setString(3, getExtension(file.getFileName().toString()));
                    insertStmt.setString(4, hash);
                    insertStmt.executeUpdate();
                }
            }
        }
        return false;
    }

    private String calculateSHA256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
}