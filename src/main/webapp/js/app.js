// 全局应用状态
let currentUser = window.initialUser || null;
let cartItems = [];
let products = [];

// DOM 节点
const authSection = document.getElementById('auth-section');
const userSection = document.getElementById('user-section');
const headerUsername = document.getElementById('header-username');
const headerBalance = document.getElementById('header-balance');
const cartBadge = document.getElementById('cart-badge');
const catalogGrid = document.getElementById('catalog-grid');
const cartDrawer = document.getElementById('cart-drawer');
const cartItemsList = document.getElementById('cart-items-list');
const cartTotalPrice = document.getElementById('cart-total-price');
const backdrop = document.getElementById('backdrop');

// 弹窗组件
const loginModal = document.getElementById('login-modal');
const registerModal = document.getElementById('register-modal');
const rechargeModal = document.getElementById('recharge-modal');
const ordersModal = document.getElementById('orders-modal');
const ordersList = document.getElementById('orders-list');

// 提示框通知辅助函数
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  const icon = type === 'success' ? '✓' : '✗';
  toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
  
  container.appendChild(toast);
  
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

// 格式化货币
function formatPrice(amount) {
  return '$' + parseFloat(amount).toFixed(2);
}

// API 请求封装
async function apiRequest(url, method = 'GET', body = null) {
  const options = {
    method,
    headers: {
      'Content-Type': 'application/json'
    }
  };
  
  if (body) {
    options.body = JSON.stringify(body);
  }
  
  const response = await fetch(window.contextPath + url, options);
  
  if (!response.ok) {
    const errData = await response.json().catch(() => ({}));
    throw new Error(errData.error || `请求失败，状态码: ${response.status}`);
  }
  
  return response.json();
}

// 更新导航栏/用户区 UI
function updateUserUI() {
  if (currentUser) {
    authSection.style.display = 'none';
    userSection.style.display = 'flex';
    headerUsername.textContent = currentUser.username;
    headerBalance.textContent = formatPrice(currentUser.balance);
  } else {
    authSection.style.display = 'flex';
    userSection.style.display = 'none';
    cartBadge.textContent = '0';
    cartItems = [];
  }
}

// 购物车侧边栏开关
function openCart() {
  if (!currentUser) {
    openModal('login');
    showToast('请先登录以查看您的购物车。', 'error');
    return;
  }
  cartDrawer.classList.add('open');
  backdrop.classList.add('active');
  renderCart();
}

function closeCart() {
  cartDrawer.classList.remove('open');
  if (!document.querySelector('.modal.active')) {
    backdrop.classList.remove('active');
  }
}

// 弹窗管理
function openModal(modalId) {
  closeCart();
  // 先关闭所有处于 active 状态的弹窗
  document.querySelectorAll('.modal').forEach(m => m.classList.remove('active'));
  
  let modal;
  if (modalId === 'login') modal = loginModal;
  else if (modalId === 'register') modal = registerModal;
  else if (modalId === 'recharge') modal = rechargeModal;
  else if (modalId === 'orders') {
    modal = ordersModal;
    loadOrders();
  }
  
  if (modal) {
    modal.classList.add('active');
    backdrop.classList.add('active');
  }
}

function closeModal() {
  document.querySelectorAll('.modal').forEach(m => m.classList.remove('active'));
  if (!cartDrawer.classList.contains('open')) {
    backdrop.classList.remove('active');
  }
}

// 加载并渲染商品目录
async function loadProducts(search = '') {
  try {
    const url = `/api/products` + (search ? `?search=${encodeURIComponent(search)}` : '');
    products = await apiRequest(url);
    renderProducts();
  } catch (error) {
    showToast('加载商品目录失败: ' + error.message, 'error');
  }
}

function renderProducts() {
  catalogGrid.innerHTML = '';
  if (products.length === 0) {
    catalogGrid.innerHTML = '<div class="cart-empty"><p>未找到符合搜索条件的商品。</p></div>';
    return;
  }
  
  products.forEach(p => {
    const card = document.createElement('div');
    card.className = 'product-card';
    card.innerHTML = `
      <div class="product-image-container">
        <img class="product-image" src="${p.imageUrl}" alt="${p.name}" onerror="this.src='https://placehold.co/400?text=${encodeURIComponent(p.name)}'">
      </div>
      <div class="product-info">
        <h3 class="product-name">${p.name}</h3>
        <p class="product-desc">${p.description}</p>
        <div class="product-footer">
          <div>
            <div class="product-price">${formatPrice(p.price)}</div>
            <div class="product-stock">库存: ${p.stock}</div>
          </div>
          <button class="btn btn-primary btn-add-cart" onclick="handleAddToCart(${p.id}, ${p.stock})" ${p.stock <= 0 ? 'disabled' : ''}>
            ${p.stock <= 0 ? '暂无现货' : '加入购物车'}
          </button>
        </div>
      </div>
    `;
    catalogGrid.appendChild(card);
  });
}

// 加载并渲染购物车
async function loadCart() {
  if (!currentUser) return;
  try {
    cartItems = await apiRequest('/api/cart');
    updateCartBadge();
  } catch (error) {
    console.error('加载购物车失败', error);
  }
}

function updateCartBadge() {
  const totalQty = cartItems.reduce((sum, item) => sum + item.quantity, 0);
  cartBadge.textContent = totalQty;
}

function renderCart() {
  cartItemsList.innerHTML = '';
  let total = 0;
  
  if (cartItems.length === 0) {
    cartItemsList.innerHTML = `
      <div class="cart-empty">
        <div class="cart-empty-icon">🛒</div>
        <p>您的购物车空空如也。</p>
      </div>
    `;
    cartTotalPrice.textContent = formatPrice(0);
    return;
  }
  
  cartItems.forEach(item => {
    const subtotal = item.product.price * item.quantity;
    total += subtotal;
    
    const row = document.createElement('div');
    row.className = 'cart-item';
    row.innerHTML = `
      <img class="cart-item-image" src="${item.product.imageUrl}" alt="${item.product.name}" onerror="this.src='https://placehold.co/100?text=Product'">
      <div class="cart-item-details">
        <div class="cart-item-name">${item.product.name}</div>
        <div class="cart-item-price">${formatPrice(item.product.price)}</div>
        <div class="cart-item-actions">
          <div class="quantity-controls">
            <button class="qty-btn" onclick="handleUpdateQty(${item.product.id}, ${item.quantity - 1})">-</button>
            <div class="qty-val">${item.quantity}</div>
            <button class="qty-btn" onclick="handleUpdateQty(${item.product.id}, ${item.quantity + 1}, ${item.product.stock})">+</button>
          </div>
          <button class="btn-remove-item" onclick="handleRemoveFromCart(${item.product.id})">移除</button>
        </div>
      </div>
    `;
    cartItemsList.appendChild(row);
  });
  
  cartTotalPrice.textContent = formatPrice(total);
}

// 购物车操作动作处理器
async function handleAddToCart(productId, stock) {
  if (!currentUser) {
    openModal('login');
    showToast('请先登录再将商品加入购物车。', 'error');
    return;
  }
  
  try {
    cartItems = await apiRequest('/api/cart', 'POST', { productId, quantity: 1, action: 'add' });
    updateCartBadge();
    showToast('已成功加入购物车。');
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function handleUpdateQty(productId, quantity, stock = 9999) {
  if (quantity > stock) {
    showToast(`抱歉，购买数量已超过库存最大上限：${stock} 件。`, 'error');
    return;
  }
  try {
    cartItems = await apiRequest('/api/cart', 'POST', { productId, quantity, action: 'update' });
    updateCartBadge();
    renderCart();
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function handleRemoveFromCart(productId) {
  try {
    cartItems = await apiRequest(`/api/cart?productId=${productId}`, 'DELETE');
    updateCartBadge();
    renderCart();
    showToast('已从购物车中移出该商品。');
  } catch (error) {
    showToast(error.message, 'error');
  }
}

// 用户动作处理器：登录、注册、退出登录、充值
async function submitLogin(e) {
  e.preventDefault();
  const form = e.target;
  const username = form.username.value;
  const password = form.password.value;
  
  try {
    currentUser = await apiRequest('/api/auth/login', 'POST', { username, password });
    updateUserUI();
    await loadCart();
    closeModal();
    showToast(`欢迎回来，${currentUser.username}！`);
    form.reset();
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function submitRegister(e) {
  e.preventDefault();
  const form = e.target;
  const username = form.username.value;
  const password = form.password.value;
  const email = form.email.value;
  
  try {
    currentUser = await apiRequest('/api/auth/register', 'POST', { username, password, email });
    updateUserUI();
    await loadCart();
    closeModal();
    showToast('账号注册成功！$1,000.00 模拟启动资金已充入您的账户钱包。');
    form.reset();
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function handleLogout() {
  try {
    await apiRequest('/api/auth/logout', 'POST');
    currentUser = null;
    updateUserUI();
    showToast('已安全退出登录。');
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function submitRecharge(e) {
  e.preventDefault();
  const form = e.target;
  const amount = parseFloat(form.amount.value);
  
  try {
    currentUser = await apiRequest('/api/auth/recharge', 'POST', { amount });
    updateUserUI();
    closeModal();
    showToast(`成功充值 ${formatPrice(amount)}！`);
    form.reset();
  } catch (error) {
    showToast(error.message, 'error');
  }
}

// 结账生成订单动作处理器
async function handleCheckout() {
  if (cartItems.length === 0) {
    showToast('您的购物车内目前没有任何商品。', 'error');
    return;
  }
  
  try {
    const order = await apiRequest('/api/orders', 'POST');
    showToast('订单提交并支付成功！本次消费：' + formatPrice(order.totalPrice));
    
    // 清空本地购物车状态
    cartItems = [];
    updateCartBadge();
    closeCart();
    
    // 重新拉取最新的余额与商品库存
    await refreshUserState();
    await loadProducts();
  } catch (error) {
    showToast(error.message, 'error');
  }
}

async function refreshUserState() {
  try {
    currentUser = await apiRequest('/api/auth/me');
    updateUserUI();
  } catch (error) {
    currentUser = null;
    updateUserUI();
  }
}

// 加载并渲染历史订单
async function loadOrders() {
  ordersList.innerHTML = '<div class="cart-empty"><p>正在加载历史订单，请稍候...</p></div>';
  try {
    const orders = await apiRequest('/api/orders');
    renderOrders(orders);
  } catch (error) {
    ordersList.innerHTML = `<div class="cart-empty"><p style="color: var(--error)">加载订单失败: ${error.message}</p></div>`;
  }
}

function renderOrders(orders) {
  ordersList.innerHTML = '';
  if (orders.length === 0) {
    ordersList.innerHTML = `
      <div class="cart-empty">
        <p>您目前还没有在平台中创建过任何订单。</p>
      </div>
    `;
    return;
  }
  
  orders.forEach(order => {
    const card = document.createElement('div');
    card.className = 'order-card';
    
    const formattedDate = new Date(order.createdAt).toLocaleString();
    let itemsHtml = '';
    
    order.items.forEach(item => {
      itemsHtml += `
        <div class="order-item-row">
          <span class="order-item-name">${item.product.name}</span>
          <span class="order-item-qty-price">${item.quantity} x ${formatPrice(item.price)}</span>
        </div>
      `;
    });
    
    const statusText = order.status === 'PAID' ? '已支付' : order.status;
    
    card.innerHTML = `
      <div class="order-meta">
        <div>订单编号 <span class="order-id">#${order.id}</span></div>
        <div>成交时间：${formattedDate}</div>
      </div>
      <div class="order-items-list">
        ${itemsHtml}
      </div>
      <div class="order-meta" style="margin-top: 12px; margin-bottom: 0; border: none; padding: 0;">
        <span style="font-weight: 600;">交易状态：<span style="color: var(--success);">${statusText}</span></span>
        <span class="order-total">实付款：${formatPrice(order.totalPrice)}</span>
      </div>
    `;
    
    ordersList.appendChild(card);
  });
}

// 检索关键词输入框防抖监听
let searchTimeout;
document.getElementById('search-input').addEventListener('input', (e) => {
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    loadProducts(e.target.value);
  }, 300);
});

// 应用初始化
function init() {
  updateUserUI();
  loadCart();
  loadProducts();
}

init();
