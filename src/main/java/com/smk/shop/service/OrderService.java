package com.smk.shop.service;

import com.smk.shop.dao.CartDao;
import com.smk.shop.dao.OrderDao;
import com.smk.shop.dao.ProductDao;
import com.smk.shop.dao.UserDao;
import com.smk.shop.model.CartItem;
import com.smk.shop.model.Order;
import com.smk.shop.util.DbUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderService {
    private final OrderDao orderDao = new OrderDao();
    private final UserDao userDao = new UserDao();
    private final ProductDao productDao = new ProductDao();
    private final CartDao cartDao = new CartDao();

    public List<Order> getOrdersByUserId(int userId) {
        return orderDao.getOrdersByUserId(userId);
    }

    public Order checkout(int userId) throws Exception {
        Connection conn = null;
        try {
            conn = DbUtil.getConnection();
            conn.setAutoCommit(false); // Start Transaction

            // 1. Fetch user to verify balance (Locking user row for safety)
            BigDecimal userBalance = userDao.getBalanceForUpdate(conn, userId);

            // 2. Fetch cart items
            List<CartItem> cartItems = cartDao.getCartItems(conn, userId);
            if (cartItems.isEmpty()) {
                throw new Exception("Shopping cart is empty.");
            }

            BigDecimal orderTotal = BigDecimal.ZERO;
            for (CartItem item : cartItems) {
                BigDecimal itemSubtotal = item.getProduct().getPrice().multiply(new BigDecimal(item.getQuantity()));
                orderTotal = orderTotal.add(itemSubtotal);
            }

            // 3. Check user balance
            if (userBalance.compareTo(orderTotal) < 0) {
                throw new Exception("Insufficient balance. Order total: $" + orderTotal + ", Your balance: $" + userBalance);
            }

            // Sort cart items by productId to avoid deadlock during concurrent SELECT FOR UPDATE
            List<CartItem> sortedCartItems = new ArrayList<>(cartItems);
            sortedCartItems.sort((a, b) -> Integer.compare(a.getProduct().getId(), b.getProduct().getId()));

            // 4. Lock products and verify stock, then deduct stock
            for (CartItem item : sortedCartItems) {
                int productId = item.getProduct().getId();
                int quantity = item.getQuantity();
                int databaseStock = productDao.getStockForUpdate(conn, productId);
                if (databaseStock < quantity) {
                    throw new Exception("Product '" + item.getProduct().getName() + "' is out of stock. Available: " + databaseStock + ", Requested: " + quantity);
                }
                productDao.deductStock(conn, productId, quantity);
            }

            // 5. Deduct user balance
            userDao.deductBalance(conn, userId, orderTotal);

            // 6. Create Order record
            int orderId = orderDao.createOrder(conn, userId, orderTotal);

            // 7. Create OrderItems records
            for (CartItem item : cartItems) {
                orderDao.createOrderItem(
                    conn,
                    orderId,
                    item.getProduct().getId(),
                    item.getQuantity(),
                    item.getProduct().getPrice()
                );
            }

            // 8. Clear user cart
            cartDao.clearCart(conn, userId);

            conn.commit(); // Commit Transaction

            // Fetch final order object to return
            return orderDao.getOrderById(conn, orderId);

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
}
