package com.smk.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smk.shop.dao.CartDao;
import com.smk.shop.dao.OrderDao;
import com.smk.shop.dao.ProductDao;
import com.smk.shop.dao.UserDao;
import com.smk.shop.model.CartItem;
import com.smk.shop.model.Order;
import com.smk.shop.model.Product;
import com.smk.shop.model.User;
import com.smk.shop.service.OrderService;
import com.smk.shop.controller.CartServlet;
import com.smk.shop.util.DbTestUtil;
import com.smk.shop.util.DbUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ShopIntegrationTest {

    private final UserDao userDao = new UserDao();
    private final ProductDao productDao = new ProductDao();
    private final CartDao cartDao = new CartDao();
    private final OrderDao orderDao = new OrderDao();
    private final OrderService orderService = new OrderService();
    private final CartServlet cartServlet = new CartServlet();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        // Reset database before each test
        DbTestUtil.resetDatabase();
    }

    @Test
    public void testUserRegistrationLoginAndRecharge() {
        // 1. Create a user
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password123");
        user.setEmail("test@example.com");
        user.setBalance(new BigDecimal("500.00"));

        boolean created = userDao.create(user);
        assertTrue(created);
        assertTrue(user.getId() > 0);

        // 2. Find by username
        User fetched = userDao.findByUsername("testuser");
        assertNotNull(fetched);
        assertEquals(user.getId(), fetched.getId());
        assertEquals("password123", fetched.getPassword());
        assertEquals("test@example.com", fetched.getEmail());
        assertEquals(0, new BigDecimal("500.00").compareTo(fetched.getBalance()));

        // 3. Recharge
        boolean recharged = userDao.recharge(user.getId(), new BigDecimal("150.50"));
        assertTrue(recharged);

        User updatedUser = userDao.findById(user.getId());
        assertEquals(0, new BigDecimal("650.50").compareTo(updatedUser.getBalance()));
    }

    private void invokePost(CartServlet servlet, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        java.lang.reflect.Method method = servlet.getClass().getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);
        try {
            method.invoke(servlet, req, resp);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testCartServletValidationAndStockEnforcement() throws Exception {
        // Create test user
        User user = new User(0, "cartuser", "pass", "cart@example.com", new BigDecimal("1000.00"));
        userDao.create(user);

        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("user", user);

        // 1. Add valid item (Product 1: stock 10)
        // Body: { "productId": 1, "quantity": 3, "action": "add" }
        String addJson = "{\"productId\":1,\"quantity\":3,\"action\":\"add\"}";
        HttpServletRequest req = mockRequest(sessionAttrs, addJson);
        MockResponse resp = new MockResponse();

        invokePost(cartServlet, req, resp.getProxy());
        assertEquals(200, resp.status);

        List<CartItem> items = cartDao.getCartItems(user.getId());
        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getProduct().getId());
        assertEquals(3, items.get(0).getQuantity());

        // 2. Add with negative quantity -> should be rejected with 400 Bad Request
        String negativeJson = "{\"productId\":1,\"quantity\":-2,\"action\":\"add\"}";
        req = mockRequest(sessionAttrs, negativeJson);
        resp = new MockResponse();
        invokePost(cartServlet, req, resp.getProxy());
        assertEquals(400, resp.status);
        assertTrue(resp.writer.toString().contains("positive"));

        // 3. Add quantity exceeding stock (Product 1 stock is 10, current in cart is 3, trying to add 8) -> total 11 > 10
        String exceedJson = "{\"productId\":1,\"quantity\":8,\"action\":\"add\"}";
        req = mockRequest(sessionAttrs, exceedJson);
        resp = new MockResponse();
        invokePost(cartServlet, req, resp.getProxy());
        assertEquals(400, resp.status);
        assertTrue(resp.writer.toString().contains("Insufficient stock"));

        // 4. Update quantity to negative -> should be rejected with 400 Bad Request
        String updateNegativeJson = "{\"productId\":1,\"quantity\":-1,\"action\":\"update\"}";
        req = mockRequest(sessionAttrs, updateNegativeJson);
        resp = new MockResponse();
        invokePost(cartServlet, req, resp.getProxy());
        assertEquals(400, resp.status);
        assertTrue(resp.writer.toString().contains("negative"));

        // 5. Update quantity to 0 -> should remove it from the cart
        String updateZeroJson = "{\"productId\":1,\"quantity\":0,\"action\":\"update\"}";
        req = mockRequest(sessionAttrs, updateZeroJson);
        resp = new MockResponse();
        invokePost(cartServlet, req, resp.getProxy());
        assertEquals(200, resp.status);

        items = cartDao.getCartItems(user.getId());
        assertTrue(items.isEmpty());
    }

    @Test
    public void testCheckoutFlowAndStockDeduction() throws Exception {
        // Create user
        User user = new User(0, "checkoutuser", "pass", "check@example.com", new BigDecimal("1000.00"));
        userDao.create(user);

        // Add Product 1 (price 389.00, stock 10) x 2 = 778.00
        // Add Product 2 (price 89.00, stock 25) x 1 = 89.00
        // Total = 867.00
        cartDao.addToCart(user.getId(), 1, 2);
        cartDao.addToCart(user.getId(), 2, 1);

        // Checkout
        Order order = orderService.checkout(user.getId());
        assertNotNull(order);
        assertEquals(0, new BigDecimal("867.00").compareTo(order.getTotalPrice()));
        assertEquals("PAID", order.getStatus());
        assertEquals(2, order.getItems().size());

        // Verify stock deducted
        Product p1 = productDao.getProductById(1);
        assertEquals(8, p1.getStock()); // 10 - 2

        Product p2 = productDao.getProductById(2);
        assertEquals(24, p2.getStock()); // 25 - 1

        // Verify user balance deducted
        User updatedUser = userDao.findById(user.getId());
        assertEquals(0, new BigDecimal("133.00").compareTo(updatedUser.getBalance())); // 1000 - 867

        // Verify cart is cleared
        List<CartItem> cartItems = cartDao.getCartItems(user.getId());
        assertTrue(cartItems.isEmpty());
    }

    @Test
    public void testCheckoutInsufficientBalance() throws Exception {
        // Create user with low balance
        User user = new User(0, "pooruser", "pass", "poor@example.com", new BigDecimal("10.00"));
        userDao.create(user);

        // Product 1 price 389.00
        cartDao.addToCart(user.getId(), 1, 1);

        // Checkout should fail
        assertThrows(Exception.class, () -> {
            orderService.checkout(user.getId());
        });

        // Verify stock is NOT deducted
        Product p1 = productDao.getProductById(1);
        assertEquals(10, p1.getStock());

        // Verify user balance is NOT deducted
        User updatedUser = userDao.findById(user.getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(updatedUser.getBalance()));

        // Verify cart is NOT cleared
        List<CartItem> cartItems = cartDao.getCartItems(user.getId());
        assertEquals(1, cartItems.size());
    }

    @Test
    public void testCheckoutOutOfStock() throws Exception {
        User user = new User(0, "richuser", "pass", "rich@example.com", new BigDecimal("10000.00"));
        userDao.create(user);

        // Product 1 stock is 10. Try to add 11 (bypassing servlet check via DAO)
        cartDao.addToCart(user.getId(), 1, 11);

        // Checkout should fail
        assertThrows(Exception.class, () -> {
            orderService.checkout(user.getId());
        });

        // Verify stock is NOT deducted
        Product p1 = productDao.getProductById(1);
        assertEquals(10, p1.getStock());

        // Verify user balance is NOT deducted
        User updatedUser = userDao.findById(user.getId());
        assertEquals(0, new BigDecimal("10000.00").compareTo(updatedUser.getBalance()));
    }

    @Test
    public void testConcurrentCheckoutOversellingAndDeadlock() throws Exception {
        // Product 1 has stock 10.
        // We will create 15 concurrent users, each with $1000 balance, and 1 unit of Product 1 in their cart.
        // Exactly 10 checkouts should succeed. 5 should fail due to "out of stock".
        // There must be no deadlocks.
        int threadCount = 15;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User u = new User(0, "concur_user_" + i, "pass", "concur@example.com", new BigDecimal("1000.00"));
            userDao.create(u);
            cartDao.addToCart(u.getId(), 1, 1); // 1 unit of Product 1
            users.add(u);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureMessages = Collections.synchronizedList(new ArrayList<>());

        for (User u : users) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    orderService.checkout(u.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    failureMessages.add(e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire!
        boolean finishedInTime = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finishedInTime, "Test timed out - potential deadlock occurred!");

        assertEquals(10, successCount.get());
        assertEquals(5, failureCount.get());

        // Verify final stock is exactly 0
        Product p1 = productDao.getProductById(1);
        assertEquals(0, p1.getStock());

        // Verify all 5 failures were indeed out-of-stock messages
        for (String msg : failureMessages) {
            assertTrue(msg.contains("out of stock") || msg.contains("no longer exists"));
        }
    }

    @Test
    public void testConcurrentDeadlockWithOverlappingProducts() throws Exception {
        // We will test locking order.
        // User A wants to buy Product 1 and Product 2.
        // User B wants to buy Product 2 and Product 1.
        // If they checkout concurrently, they shouldn't deadlock because we sort the products before locking.
        User userA = new User(0, "usera", "pass", "a@example.com", new BigDecimal("1000.00"));
        userDao.create(userA);
        // Product 1 and Product 2 in cart
        cartDao.addToCart(userA.getId(), 1, 1);
        cartDao.addToCart(userA.getId(), 2, 1);

        User userB = new User(0, "userb", "pass", "b@example.com", new BigDecimal("1000.00"));
        userDao.create(userB);
        // Product 2 and Product 1 in cart
        cartDao.addToCart(userB.getId(), 2, 1);
        cartDao.addToCart(userB.getId(), 1, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.checkout(userA.getId());
                successCount.incrementAndGet();
            } catch (Throwable e) {
                exceptions.add(e);
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.checkout(userB.getId());
                successCount.incrementAndGet();
            } catch (Throwable e) {
                exceptions.add(e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Deadlock detected during concurrent checkout of overlapping products!");
        assertEquals(2, successCount.get(), "Both checkouts should have succeeded, but errors occurred: " + exceptions);
    }

    // --- Helpers for Mocking Servlet Requests and Responses ---

    private static HttpServletRequest mockRequest(Map<String, Object> sessionAttributes, String jsonBody) throws IOException {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSession")) {
                        return mockSession(sessionAttributes);
                    } else if (method.getName().equals("getReader")) {
                        return new BufferedReader(new StringReader(jsonBody));
                    }
                    return null;
                }
        );
    }

    private static HttpSession mockSession(Map<String, Object> attributes) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getAttribute")) {
                        return attributes.get(args[0]);
                    } else if (method.getName().equals("setAttribute")) {
                        attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    return null;
                }
        );
    }

    private static class MockResponse {
        int status = 200;
        String contentType;
        StringWriter writer = new StringWriter();

        HttpServletResponse getProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setStatus")) {
                            status = (int) args[0];
                        } else if (method.getName().equals("setContentType")) {
                            contentType = (String) args[0];
                        } else if (method.getName().equals("getWriter")) {
                            return new PrintWriter(writer);
                        }
                        return null;
                    }
            );
        }
    }
}
