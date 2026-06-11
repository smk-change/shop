package com.smk.shop.servlet;

import com.smk.shop.dao.OrderDao;
import com.smk.shop.model.Order;
import com.smk.shop.model.User;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/orders")
public class OrderServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final OrderDao orderDao = new OrderDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        List<Order> orders = orderDao.getOrdersByUserId(user.getId());
        sendJson(resp, orders);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please login first.");
            return;
        }

        User user = (User) session.getAttribute("user");
        try {
            Order order = orderDao.checkout(user.getId());
            sendJson(resp, order);
        } catch (Exception e) {
            // Send business logic validation errors as 400 Bad Request
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
}
