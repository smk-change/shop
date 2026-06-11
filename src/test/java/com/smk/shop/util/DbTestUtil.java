package com.smk.shop.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DbTestUtil {

    public static void resetDatabase() {
        System.out.println("[DbTestUtil] Resetting database for tests...");
        try {
            // Ensure data source is initialized
            DbUtil.initializeDataSource();

            try (InputStream is = DbTestUtil.class.getClassLoader().getResourceAsStream("schema.sql")) {
                if (is == null) {
                    throw new RuntimeException("Cannot find schema.sql in classpath!");
                }

                String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                // Strip comments
                String cleanSql = sql.replaceAll("(?m)^\\s*--.*$", "")
                                     .replaceAll("(?s)/\\*.*?\\*/", "");

                String[] statements = cleanSql.split(";");
                try (Connection conn = DbUtil.getConnection();
                     Statement stmt = conn.createStatement()) {

                    for (String sqlStmt : statements) {
                        String trimmed = sqlStmt.trim();
                        if (!trimmed.isEmpty()) {
                            stmt.execute(trimmed);
                        }
                    }
                    System.out.println("[DbTestUtil] Database reset complete.");
                }
            }
        } catch (Exception e) {
            System.err.println("[DbTestUtil] Database reset failed!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
