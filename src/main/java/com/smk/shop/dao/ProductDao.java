package com.smk.shop.dao;

import com.smk.shop.model.Product;
import com.smk.shop.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    public List<Product> getAllProducts(String search) {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products";
        if (search != null && !search.trim().isEmpty()) {
            sql += " WHERE name LIKE ? OR description LIKE ?";
        }
        
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            if (search != null && !search.trim().isEmpty()) {
                String likePattern = "%" + search.trim() + "%";
                ps.setString(1, likePattern);
                ps.setString(2, likePattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapProduct(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    public Product getProductById(int id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapProduct(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateStock(int id, int newStock) {
        String sql = "UPDATE products SET stock = ? WHERE id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getStockForUpdate(Connection conn, int id) throws SQLException {
        String sql = "SELECT stock FROM products WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock");
                } else {
                    throw new SQLException("Product not found: " + id);
                }
            }
        }
    }

    public void deductStock(Connection conn, int id, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock = stock - ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, id);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Failed to deduct stock for product: " + id);
            }
        }
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getInt("stock"),
            rs.getString("image_url")
        );
    }
}
