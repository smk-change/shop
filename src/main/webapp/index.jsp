<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.smk.shop.model.User" %>
<%
    User currentUser = session != null ? (User) session.getAttribute("user") : null;
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Shop. — 极简美学编辑商店</title>
  <link rel="stylesheet" href="<%= contextPath %>/css/style.css">
  <script>
    // Inject server-side variables directly into the client context to prevent flickering
    window.contextPath = '<%= contextPath %>';
    window.initialUser = <%= currentUser != null ? "{" +
        "\"id\":" + currentUser.getId() + "," +
        "\"username\":\"" + currentUser.getUsername() + "\"," +
        "\"email\":" + (currentUser.getEmail() != null ? "\"" + currentUser.getEmail() + "\"" : "null") + "," +
        "\"balance\":" + currentUser.getBalance() +
        "}" : "null" %>;
  </script>
</head>
<body>

  <!-- 头部导航栏 -->
  <header class="header">
    <div class="container header-container">
      <a href="#" class="logo">
        Shop<span class="logo-dot"></span>
      </a>
      
      <div class="nav-actions">
        <!-- 访客区域 -->
        <div id="auth-section" class="nav-actions" style="display: none;">
          <button class="btn btn-secondary" onclick="openModal('login')">登录</button>
          <button class="btn btn-dark" onclick="openModal('register')">注册</button>
        </div>

        <!-- 登录用户区域 -->
        <div id="user-section" class="nav-actions" style="display: none;">
          <button class="btn btn-text" onclick="openModal('orders')">订单历史</button>
          <div class="user-badge">
            <span class="username" id="header-username">访客</span>
            <span class="balance" id="header-balance">$0.00</span>
            <button class="btn-recharge" onclick="openModal('recharge')" title="账户充值">+</button>
          </div>
          <button class="btn btn-secondary" onclick="handleLogout()">退出登录</button>
        </div>

        <!-- 购物车触发按钮 -->
        <button class="cart-trigger" onclick="openCart()" title="查看购物车">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="21" r="1"></circle><circle cx="20" cy="21" r="1"></circle><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path></svg>
          <span class="cart-badge" id="cart-badge">0</span>
        </button>
      </div>
    </div>
  </header>

  <!-- Hero 欢迎模块 -->
  <section class="hero">
    <div class="container">
      <div class="hero-subtitle">编辑推荐系列</div>
      <h1>真诚设计 至臻舒适 </h1>
      <p class="hero-desc">为现代工作空间和居家环境精选的物件。采用有机材料打造，散发温润的人文主义美学。</p>
      
      <!-- 搜索栏 -->
      <div class="search-container">
        <input type="text" class="search-input" id="search-input" placeholder="搜索商品目录（如：椅子、水杯、台灯）...">
        <span class="search-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        </span>
      </div>
    </div>
  </section>

  <!-- 商品目录区域 -->
  <main class="catalog-section">
    <div class="container">
      <div class="catalog-header">
        <h2>商品目录</h2>
      </div>
      
      <!-- 商品网格 -->
      <div class="catalog-grid" id="catalog-grid">
        <div class="cart-empty"><p>正在加载商品...</p></div>
      </div>
    </div>
  </main>

  <!-- 背景遮罩层 -->
  <div class="backdrop" id="backdrop" onclick="closeCart(); closeModal();"></div>

  <!-- 滑出式购物车抽屉 -->
  <aside class="cart-drawer" id="cart-drawer">
    <div class="cart-header">
      <h2>购物车</h2>
      <button class="cart-close" onclick="closeCart()">&times;</button>
    </div>
    <div class="cart-items-list" id="cart-items-list">
      <!-- 动态加载购物车内容 -->
    </div>
    <div class="cart-footer">
      <div class="cart-summary-row">
        <span>订单总额:</span>
        <span class="cart-total-price" id="cart-total-price">$0.00</span>
      </div>
      <button class="btn btn-primary" style="width: 100%; height: 48px;" onclick="handleCheckout()">
        确认结账
      </button>
    </div>
  </aside>

  <!-- 登录弹窗 -->
  <div class="modal" id="login-modal">
    <div class="modal-header">
      <h2>用户登录</h2>
      <button class="modal-close" onclick="closeModal()">&times;</button>
    </div>
    <div class="modal-body">
      <form id="login-form" onsubmit="submitLogin(event)">
        <div class="form-group">
          <label class="form-label" for="login-username">用户名</label>
          <input class="form-input" type="text" id="login-username" name="username" placeholder="例如：smk" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="login-password">密码</label>
          <input class="form-input" type="password" id="login-password" name="password" placeholder="请输入密码" required>
        </div>
        <button class="btn btn-primary" type="submit" style="width: 100%; margin-top: 10px;">登录</button>
      </form>
      <div class="form-footer">
        还没有账号？ <a href="#" onclick="openModal('register')">点击注册</a>
      </div>
    </div>
  </div>

  <!-- 注册弹窗 -->
  <div class="modal" id="register-modal">
    <div class="modal-header">
      <h2>创建账号</h2>
      <button class="modal-close" onclick="closeModal()">&times;</button>
    </div>
    <div class="modal-body">
      <form id="register-form" onsubmit="submitRegister(event)">
        <div class="form-group">
          <label class="form-label" for="register-username">用户名</label>
          <input class="form-input" type="text" id="register-username" name="username" placeholder="请输入用户名" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="register-email">电子邮箱 (选填)</label>
          <input class="form-input" type="email" id="register-email" name="email" placeholder="例如：user@example.com">
        </div>
        <div class="form-group">
          <label class="form-label" for="register-password">密码</label>
          <input class="form-input" type="password" id="register-password" name="password" placeholder="请输入密码" required>
        </div>
        <button class="btn btn-primary" type="submit" style="width: 100%; margin-top: 10px;">注册账号</button>
      </form>
      <div class="form-footer">
        已有账号？ <a href="#" onclick="openModal('login')">点击登录</a>
      </div>
    </div>
  </div>

  <!-- 充值弹窗 -->
  <div class="modal" id="recharge-modal">
    <div class="modal-header">
      <h2>账户充值</h2>
      <button class="modal-close" onclick="closeModal()">&times;</button>
    </div>
    <div class="modal-body">
      <form id="recharge-form" onsubmit="submitRecharge(event)">
        <div class="form-group">
          <label class="form-label" for="recharge-amount">充值金额 ($)</label>
          <input class="form-input" type="number" id="recharge-amount" name="amount" min="10" max="10000" step="10" value="100" required>
        </div>
        <button class="btn btn-primary" type="submit" style="width: 100%; margin-top: 10px;">立即充值</button>
      </form>
    </div>
  </div>

  <!-- 订单历史弹窗 -->
  <div class="modal modal-orders" id="orders-modal">
    <div class="modal-header">
      <h2>历史订单</h2>
      <button class="modal-close" onclick="closeModal()">&times;</button>
    </div>
    <div class="modal-body" id="orders-list">
      <!-- 动态加载历史订单 -->
    </div>
  </div>

  <!-- 弹窗消息通知容器 -->
  <div class="toast-container" id="toast-container"></div>

  <!-- 引入 JavaScript 控制脚本 -->
  <script src="<%= contextPath %>/js/app.js"></script>
</body>
</html>
