package com.smk.shop.service;

import com.smk.shop.dao.CartDao;
import com.smk.shop.dao.ProductDao;
import com.smk.shop.model.CartItem;
import com.smk.shop.model.Product;
import java.util.List;

public class CartService {
    private final CartDao cartDao = new CartDao();
    private final ProductDao productDao = new ProductDao();

    public List<CartItem> getCartItems(int userId) {
        return cartDao.getCartItems(userId);
    }

    public boolean addToCart(int userId, int productId, int quantity) throws Exception {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive.");
        }
        Product product = productDao.getProductById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product not found.");
        }

        int existingQuantity = 0;
        List<CartItem> cartItems = cartDao.getCartItems(userId);
        for (CartItem item : cartItems) {
            if (item.getProduct().getId() == productId) {
                existingQuantity = item.getQuantity();
                break;
            }
        }
        int targetQuantity = existingQuantity + quantity;

        if (targetQuantity > product.getStock()) {
            throw new IllegalArgumentException("Insufficient stock. Only " + product.getStock() + " units available.");
        }

        return cartDao.addToCart(userId, productId, quantity);
    }

    public boolean updateQuantity(int userId, int productId, int quantity) throws Exception {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        Product product = productDao.getProductById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product not found.");
        }

        if (quantity > product.getStock()) {
            throw new IllegalArgumentException("Insufficient stock. Only " + product.getStock() + " units available.");
        }

        return cartDao.updateQuantity(userId, productId, quantity);
    }

    public boolean removeCartItem(int userId, int productId) {
        return cartDao.removeCartItem(userId, productId);
    }

    public boolean clearCart(int userId) {
        return cartDao.clearCart(userId);
    }
}
