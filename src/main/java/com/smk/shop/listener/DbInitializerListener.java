package com.smk.shop.listener;

import com.smk.shop.util.DbUtil;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class DbInitializerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[DbInitializerListener] Initializing database...");
        try {
            Properties prop = DbUtil.getProperties();
            String dbUrl = prop.getProperty("db.url");
            String username = prop.getProperty("db.username");
            String password = prop.getProperty("db.password");
            String driver = prop.getProperty("db.driver");

            // 1. Resolve host and port to connect to MySQL without specifying a database first
            // dbUrl looks like: jdbc:mysql://192.168.124.1:3306/shop_db?useSSL=false...
            String baseJdbcUrl = dbUrl.substring(0, dbUrl.indexOf("/shop_db"));
            String queryParams = dbUrl.substring(dbUrl.indexOf("?") != -1 ? dbUrl.indexOf("?") : dbUrl.length());
            String masterUrl = baseJdbcUrl + "/" + queryParams;

            // Load driver class
            Class.forName(driver);

            // Connect to MySQL server root (without database) to ensure shop_db exists
            System.out.println("[DbInitializerListener] Connecting to MySQL master to verify database existence: " + baseJdbcUrl);
            try (Connection conn = DriverManager.getConnection(masterUrl, username, password);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS shop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
                System.out.println("[DbInitializerListener] Database 'shop_db' verified/created.");
            }

            // 2. Trigger HikariCP DataSource initialization
            System.out.println("[DbInitializerListener] Initializing HikariCP data source...");
            DbUtil.initializeDataSource();

            // Check if the database has already been initialized (i.e. 'users' table exists)
            boolean alreadyInitialized = false;
            try (Connection conn = DbUtil.getConnection()) {
                alreadyInitialized = checkIfTableExists(conn, "users");
            }

            if (alreadyInitialized) {
                System.out.println("[DbInitializerListener] Database tables already exist. Skipping execution of schema.sql to preserve existing data.");
            } else {
                // 3. Execute schema.sql to create tables and insert mock products
                System.out.println("[DbInitializerListener] Executing schema.sql to initialize tables...");
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
                    if (is == null) {
                        throw new RuntimeException("Cannot find schema.sql in classpath!");
                    }
                    
                    String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));

                    // Strip single-line and multi-line comments
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
                        System.out.println("[DbInitializerListener] schema.sql executed successfully.");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[DbInitializerListener] Database initialization failed!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean checkIfTableExists(Connection conn, String tableName) {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[DbInitializerListener] Closing database connections...");
        DbUtil.close();
    }
}
