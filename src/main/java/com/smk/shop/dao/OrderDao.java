package com.smk.shop.dao;

import com.smk.shop.model.Order;
import com.smk.shop.model.OrderItem;
import com.smk.shop.model.Product;
import com.smk.shop.util.DbUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderDao {

    public int createOrder(Connection conn, int userId, BigDecimal totalPrice) throws SQLException {
        int orderId = 0;
        String insertOrderSql = "INSERT INTO orders (user_id, total_price, status) VALUES (?, ?, 'PAID')";
        try (PreparedStatement psOrder = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
            psOrder.setInt(1, userId);
            psOrder.setBigDecimal(2, totalPrice);
            psOrder.executeUpdate();
            try (ResultSet rsKeys = psOrder.getGeneratedKeys()) {
                if (rsKeys.next()) {
                    orderId = rsKeys.getInt(1);
                }
            }
        }
        if (orderId == 0) {
            throw new SQLException("Creating order failed, no ID obtained.");
        }
        return orderId;
    }

    public void createOrderItem(Connection conn, int orderId, int productId, int quantity, BigDecimal price) throws SQLException {
        String insertOrderItemSql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement psItem = conn.prepareStatement(insertOrderItemSql)) {
            psItem.setInt(1, orderId);
            psItem.setInt(2, productId);
            psItem.setInt(3, quantity);
            psItem.setBigDecimal(4, price);
            int updated = psItem.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Failed to create order item for product: " + productId);
            }
        }
    }


    public List<Order> getOrdersByUserId(int userId) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("id");
                    Order order = new Order(
                        orderId,
                        rs.getInt("user_id"),
                        rs.getBigDecimal("total_price"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        getOrderItems(conn, orderId)
                    );
                    orders.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    public Order getOrderById(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Order(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getBigDecimal("total_price"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        getOrderItems(conn, orderId)
                    );
                }
            }
        }
        return null;
    }

    private List<OrderItem> getOrderItems(Connection conn, int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        String sql = "SELECT o.id AS item_id, o.quantity, o.price, " +
                     "p.id AS product_id, p.name, p.description, p.price AS current_price, p.stock, p.image_url " +
                     "FROM order_items o JOIN products p ON o.product_id = p.id " +
                     "WHERE o.order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product product = new Product(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("current_price"),
                        rs.getInt("stock"),
                        rs.getString("image_url")
                    );
                    OrderItem item = new OrderItem(
                        rs.getInt("item_id"),
                        orderId,
                        product,
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price")
                    );
                    items.add(item);
                }
            }
        }
        return items;
    }

    // Temporary helper class for checkout info
    private static class TempCartItem {
        int productId;
        String name;
        int quantity;
        BigDecimal price;
        int stock;

        TempCartItem(int productId, String name, int quantity, BigDecimal price, int stock) {
            this.productId = productId;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.stock = stock;
        }
    }
}
