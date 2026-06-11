package com.smk.shop.controller;

import com.smk.shop.service.CartService;
import com.smk.shop.model.CartItem;
import com.smk.shop.model.Product;
import com.smk.shop.model.User;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/cart")
public class CartServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final CartService cartService = new CartService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        List<CartItem> cartItems = cartService.getCartItems(user.getId());
        sendJson(resp, cartItems);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = readJson(req, Map.class);
        if (body == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body.");
            return;
        }

        Object prodIdObj = body.get("productId");
        Object qtyObj = body.get("quantity");
        String action = (String) body.get("action"); // "add" or "update"

        if (prodIdObj == null || qtyObj == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "productId and quantity are required.");
            return;
        }

        int productId = Integer.parseInt(prodIdObj.toString());
        int quantity = Integer.parseInt(qtyObj.toString());

        try {
            boolean success;
            if ("update".equalsIgnoreCase(action)) {
                success = cartService.updateQuantity(user.getId(), productId, quantity);
            } else {
                success = cartService.addToCart(user.getId(), productId, quantity);
            }

            if (success) {
                sendJson(resp, cartService.getCartItems(user.getId()));
            } else {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cart operation failed.");
            }
        } catch (IllegalArgumentException e) {
            if ("Product not found.".equals(e.getMessage())) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            } else {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            }
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        String productIdStr = req.getParameter("productId");
        if (productIdStr == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "productId parameter is required.");
            return;
        }

        try {
            int productId = Integer.parseInt(productIdStr);
            if (cartService.removeCartItem(user.getId(), productId)) {
                sendJson(resp, cartService.getCartItems(user.getId()));
            } else {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove item from cart.");
            }
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid productId.");
        }
    }
}
