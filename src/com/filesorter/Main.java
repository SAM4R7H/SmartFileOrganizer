package com.filesorter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        DatabaseManager.initializeDatabase();
        DataRepository repo = new DataRepository();
        SortingEngine engine = new SortingEngine();

        String command = args[0].toLowerCase();

        switch (command) {
            case "rule":
                if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                    repo.listRules();
                } else if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                    repo.addRule(args[2], args[3]);
                } else {
                    System.out.println("Usage:");
                    System.out.println("  rule add <.ext> <dir>");
                    System.out.println("  rule list");
                }
                break;
            case "tag":
                if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                    repo.addTag(Paths.get(args[2]).toAbsolutePath().toString(), args[3]);
                } else {
                    System.out.println("Usage: tag add <FilePath> <TagName>");
                }
                break;
            case "sort":
                if (args.length == 2) {
                    engine.sortDirectory(args[1]);
                } else {
                    System.out.println("Usage: sort <DirectoryPath>");
                }
                break;
            case "undo":
                if (args.length == 2) {
                    undoBatch(Integer.parseInt(args[1]));
                } else {
                    System.out.println("Usage: undo <BatchID>");
                }
                break;
            default:
                printHelp();
        }
    }

    private static void undoBatch(int batchId) {
        System.out.println("⏪ Reverting batch #" + batchId);
        String sql = "SELECT id, original_path, new_path FROM action_log WHERE batch_id = ? AND status = 'MOVED'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, batchId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Path original = Paths.get(rs.getString("original_path"));
                Path current = Paths.get(rs.getString("new_path"));
                
                if (Files.exists(current)) {
                    Files.createDirectories(original.getParent());
                    Files.move(current, original);
                    
                    // Mark reverted
                    try (PreparedStatement update = conn.prepareStatement("UPDATE action_log SET status = 'REVERTED' WHERE id = ?")) {
                        update.setInt(1, rs.getInt("id"));
                        update.executeUpdate();
                    }
                    System.out.println("↩️ Reverted: " + current.getFileName());
                }
            }
            System.out.println("✅ Undo complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("🛠️  Smart File Organizer CLI");
        System.out.println("Commands:");
        System.out.println("  rule add <.ext> <dir>   - Add sorting rule (e.g., rule add .pdf Docs)");
        System.out.println("  tag add <file> <tag>    - Tag a file (e.g., tag add my.pdf finance)");
        System.out.println("  sort <dir_path>         - Sort a directory recursively");
        System.out.println("  undo <batch_id>         - Revert a sorting batch");
    }
}