-- Insert New Products for the Shop
USE shop_db;

INSERT INTO products (name, description, price, stock, image_url) VALUES 
('植物精油香薰蜡烛', '天然大豆蜡配以植物精油，琥珀色玻璃罐包装，点亮后散发温润芬芳与宁静氛围。', 39.00, 40, 'images/candle.png'),
('黑胡桃木实木置物架', '采用北美黑胡桃木打造，表面涂有天然木蜡油。极简线条，兼具实用收纳与空间陈列之美。', 159.00, 15, 'images/shelf.png'),
('手工粗陶单花插花瓶', '手作肌理感粗陶花瓶，质朴温润。器形优雅，适合插入一枝枯枝或干花以点缀空间。', 49.00, 20, 'images/vase.png'),
('水洗亚麻抱枕套配枕芯', '选用优质水洗亚麻面料，质地柔软透气，呈现自然的褶皱感。暖米色调，营造温馨慵懒的居家感。', 58.00, 30, 'images/cushion.png');
