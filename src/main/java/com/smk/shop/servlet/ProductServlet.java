package com.smk.shop.servlet;

import com.smk.shop.dao.ProductDao;
import com.smk.shop.model.Product;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/products")
public class ProductServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final ProductDao productDao = new ProductDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String search = req.getParameter("search");
        List<Product> list = productDao.getAllProducts(search);
        sendJson(resp, list);
    }
}
