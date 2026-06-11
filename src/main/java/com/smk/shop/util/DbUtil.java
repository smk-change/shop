package com.smk.shop.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DbUtil {
    private static HikariDataSource dataSource;
    private static final Properties prop = new Properties();

    static {
        try (InputStream input = DbUtil.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input == null) {
                throw new RuntimeException("Sorry, unable to find db.properties");
            }
            prop.load(input);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Database configuration load failed: " + e.getMessage());
        }
    }

    public static synchronized void initializeDataSource() {
        if (dataSource != null) return;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(prop.getProperty("db.url"));
        config.setUsername(prop.getProperty("db.username"));
        config.setPassword(prop.getProperty("db.password"));
        config.setDriverClassName(prop.getProperty("db.driver"));
        
        // Connection pool optimization
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializeDataSource();
        }
        return dataSource.getConnection();
    }

    public static Properties getProperties() {
        return prop;
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
