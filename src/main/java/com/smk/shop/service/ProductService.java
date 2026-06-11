package com.smk.shop.service;

import com.smk.shop.dao.ProductDao;
import com.smk.shop.model.Product;
import java.util.List;

public class ProductService {
    private final ProductDao productDao = new ProductDao();

    public List<Product> getAllProducts(String search) {
        return productDao.getAllProducts(search);
    }

    public Product getProductById(int id) {
        return productDao.getProductById(id);
    }
}
