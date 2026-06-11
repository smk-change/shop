package com.smk.shop.servlet;

import com.smk.shop.dao.CartDao;
import com.smk.shop.dao.ProductDao;
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
    private final CartDao cartDao = new CartDao();
    private final ProductDao productDao = new ProductDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        List<CartItem> cartItems = cartDao.getCartItems(user.getId());
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

        Product product = productDao.getProductById(productId);
        if (product == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Product not found.");
            return;
        }

        // Verify stock before adding to cart (Optional, but good UX)
        if (quantity > product.getStock()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Insufficient stock. Only " + product.getStock() + " units available.");
            return;
        }

        boolean success;
        if ("update".equalsIgnoreCase(action)) {
            success = cartDao.updateQuantity(user.getId(), productId, quantity);
        } else {
            // Default is "add"
            success = cartDao.addToCart(user.getId(), productId, quantity);
        }

        if (success) {
            sendJson(resp, cartDao.getCartItems(user.getId()));
        } else {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cart operation failed.");
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
            if (cartDao.removeCartItem(user.getId(), productId)) {
                sendJson(resp, cartDao.getCartItems(user.getId()));
            } else {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to remove item from cart.");
            }
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid productId.");
        }
    }
}
