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

    /**
     * Executes shopping cart checkout in a single database transaction.
     * Ensures stock safety (FOR UPDATE locking) and balance sufficiency.
     * @return Created Order object if successful, null otherwise.
     * @throws Exception describing validation failures (out of stock, insufficient balance, etc.)
     */
    public Order checkout(int userId) throws Exception {
        Connection conn = null;
        try {
            conn = DbUtil.getConnection();
            conn.setAutoCommit(false); // Start Transaction

            // 1. Fetch user to verify balance (Locking user row for safety)
            BigDecimal userBalance = BigDecimal.ZERO;
            String selectUserSql = "SELECT balance FROM users WHERE id = ? FOR UPDATE";
            try (PreparedStatement psUser = conn.prepareStatement(selectUserSql)) {
                psUser.setInt(1, userId);
                try (ResultSet rsUser = psUser.executeQuery()) {
                    if (rsUser.next()) {
                        userBalance = rsUser.getBigDecimal("balance");
                    } else {
                        throw new Exception("User not found.");
                    }
                }
            }

            // 2. Fetch cart items
            String selectCartSql = "SELECT c.product_id, c.quantity, p.name, p.price, p.stock " +
                                  "FROM cart_items c JOIN products p ON c.product_id = p.id " +
                                  "WHERE c.user_id = ?";
            List<TempCartItem> cartItems = new ArrayList<>();
            BigDecimal orderTotal = BigDecimal.ZERO;

            try (PreparedStatement psCart = conn.prepareStatement(selectCartSql)) {
                psCart.setInt(1, userId);
                try (ResultSet rsCart = psCart.executeQuery()) {
                    while (rsCart.next()) {
                        int productId = rsCart.getInt("product_id");
                        int quantity = rsCart.getInt("quantity");
                        String name = rsCart.getString("name");
                        BigDecimal price = rsCart.getBigDecimal("price");
                        int currentStock = rsCart.getInt("stock");

                        BigDecimal itemSubtotal = price.multiply(new BigDecimal(quantity));
                        orderTotal = orderTotal.add(itemSubtotal);

                        cartItems.add(new TempCartItem(productId, name, quantity, price, currentStock));
                    }
                }
            }

            if (cartItems.isEmpty()) {
                throw new Exception("Shopping cart is empty.");
            }

            // 3. Check user balance
            if (userBalance.compareTo(orderTotal) < 0) {
                throw new Exception("Insufficient balance. Order total: $" + orderTotal + ", Your balance: $" + userBalance);
            }

            // 4. Lock products and verify stock, then deduct stock
            String lockProductSql = "SELECT stock FROM products WHERE id = ? FOR UPDATE";
            String updateStockSql = "UPDATE products SET stock = stock - ? WHERE id = ?";
            
            try (PreparedStatement psLock = conn.prepareStatement(lockProductSql);
                 PreparedStatement psUpdateStock = conn.prepareStatement(updateStockSql)) {
                
                for (TempCartItem item : cartItems) {
                    // Lock product row
                    psLock.setInt(1, item.productId);
                    try (ResultSet rsProd = psLock.executeQuery()) {
                        if (rsProd.next()) {
                            int databaseStock = rsProd.getInt("stock");
                            if (databaseStock < item.quantity) {
                                throw new Exception("Product '" + item.name + "' is out of stock. Available: " + databaseStock + ", Requested: " + item.quantity);
                            }
                        } else {
                            throw new Exception("Product '" + item.name + "' no longer exists.");
                        }
                    }
                    
                    // Deduct stock
                    psUpdateStock.setInt(1, item.quantity);
                    psUpdateStock.setInt(2, item.productId);
                    psUpdateStock.executeUpdate();
                }
            }

            // 5. Deduct user balance
            String updateBalanceSql = "UPDATE users SET balance = balance - ? WHERE id = ?";
            try (PreparedStatement psUpdateBal = conn.prepareStatement(updateBalanceSql)) {
                psUpdateBal.setBigDecimal(1, orderTotal);
                psUpdateBal.setInt(2, userId);
                psUpdateBal.executeUpdate();
            }

            // 6. Create Order record
            int orderId = 0;
            String insertOrderSql = "INSERT INTO orders (user_id, total_price, status) VALUES (?, ?, 'PAID')";
            try (PreparedStatement psOrder = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                psOrder.setInt(1, userId);
                psOrder.setBigDecimal(2, orderTotal);
                psOrder.executeUpdate();
                try (ResultSet rsKeys = psOrder.getGeneratedKeys()) {
                    if (rsKeys.next()) {
                        orderId = rsKeys.getInt(1);
                    }
                }
            }

            // 7. Create OrderItems records
            String insertOrderItemSql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
            try (PreparedStatement psItem = conn.prepareStatement(insertOrderItemSql)) {
                for (TempCartItem item : cartItems) {
                    psItem.setInt(1, orderId);
                    psItem.setInt(2, item.productId);
                    psItem.setInt(3, item.quantity);
                    psItem.setBigDecimal(4, item.price);
                    psItem.executeUpdate();
                }
            }

            // 8. Clear user cart
            String clearCartSql = "DELETE FROM cart_items WHERE user_id = ?";
            try (PreparedStatement psClear = conn.prepareStatement(clearCartSql)) {
                psClear.setInt(1, userId);
                psClear.executeUpdate();
            }

            conn.commit(); // Commit Transaction

            // Fetch final order object to return
            return getOrderById(conn, orderId);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback transaction on error
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
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

    private Order getOrderById(Connection conn, int orderId) throws SQLException {
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
