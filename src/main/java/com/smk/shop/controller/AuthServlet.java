package com.smk.shop.servlet;

import com.smk.shop.service.UserService;
import com.smk.shop.model.User;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if ("/me".equals(pathInfo)) {
            handleMe(req, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "API endpoint not found.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if ("/login".equals(pathInfo)) {
            handleLogin(req, resp);
        } else if ("/register".equals(pathInfo)) {
            handleRegister(req, resp);
        } else if ("/logout".equals(pathInfo)) {
            handleLogout(req, resp);
        } else if ("/recharge".equals(pathInfo)) {
            handleRecharge(req, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "API endpoint not found.");
        }
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in.");
            return;
        }

        User sessionUser = (User) session.getAttribute("user");
        // Reload from database to ensure fresh balance
        User freshUser = userService.getUserById(sessionUser.getId());
        if (freshUser == null) {
            session.invalidate();
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "User not found.");
            return;
        }

        // Keep session updated
        session.setAttribute("user", freshUser);
        freshUser.setPassword(null); // Hide password in JSON output
        sendJson(resp, freshUser);
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, String> credentials = readJson(req, Map.class);
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null || username.trim().isEmpty() || password.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Username and password are required.");
            return;
        }

        User user = userService.login(username, password);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password.");
            return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute("user", user);
        
        user.setPassword(null); // Hide password in JSON output
        sendJson(resp, user);
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, String> data = readJson(req, Map.class);
        String username = data.get("username");
        String password = data.get("password");
        String email = data.get("email");

        if (username == null || password == null || username.trim().isEmpty() || password.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Username and password are required.");
            return;
        }

        User existing = userService.getUserByUsername(username.trim());
        if (existing != null) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, "Username is already taken.");
            return;
        }

        User newUser = new User();
        newUser.setUsername(username.trim());
        newUser.setPassword(password); // Plaintext as requested
        newUser.setEmail(email != null ? email.trim() : null);
        newUser.setBalance(new BigDecimal("1000.00")); // Initial balance

        if (userService.register(newUser)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("user", newUser);
            newUser.setPassword(null);
            sendJson(resp, newUser);
        } else {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Registration failed.");
        }
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sendSuccess(resp, "Logged out successfully.");
    }

    private void handleRecharge(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in.");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = readJson(req, Map.class);
        Object amountObj = data.get("amount");
        if (amountObj == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Recharge amount is required.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid recharge amount. Must be positive.");
            return;
        }

        User sessionUser = (User) session.getAttribute("user");
        if (userService.recharge(sessionUser.getId(), amount)) {
            User freshUser = userService.getUserById(sessionUser.getId());
            session.setAttribute("user", freshUser);
            freshUser.setPassword(null);
            sendJson(resp, freshUser);
        } else {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Recharge failed.");
        }
    }
}
