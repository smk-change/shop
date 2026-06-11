package com.smk.shop.service;

import com.smk.shop.dao.UserDao;
import com.smk.shop.model.User;
import java.math.BigDecimal;

public class UserService {
    private final UserDao userDao = new UserDao();

    public User getUserById(int id) {
        return userDao.findById(id);
    }

    public User getUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        return userDao.findByUsername(username.trim());
    }

    public User login(String username, String password) {
        if (username == null || password == null || username.trim().isEmpty() || password.isEmpty()) {
            return null;
        }
        User user = userDao.findByUsername(username.trim());
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public boolean register(User user) {
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            return false;
        }
        return userDao.create(user);
    }

    public boolean recharge(int userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return userDao.recharge(userId, amount);
    }
}
