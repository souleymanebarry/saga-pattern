--liquibase formatted sql
--changeset sbarry:001-insert-products

INSERT INTO products (product_id, sku, name, brand, price, description, created_at) VALUES
    ('bd4ecd31-cdd7-4666-a462-1c994e3f02c1', 'SKU-IPH15P', 'iPhone 15 Pro', 'Apple', 1229.00, 'Smartphone haut de gamme avec puce A17 Pro et triple capteur photo.', NOW()),
    ('fc819c95-cede-4edd-826c-e63573bf260b', 'SKU-MBAM3', 'MacBook Air M3', 'Apple', 1499.00, 'Ultrabook puissant et léger équipé de la puce Apple M3.', NOW()),
    ('c263c862-9378-44f0-8875-17a352d90717', 'SKU-PS5SLIM', 'PlayStation 5 Slim', 'Sony', 549.00, 'Console next-gen avec SSD ultra rapide et jeux 4K HDR.', NOW()),
    ('94a2e2ea-39a9-4947-96a3-517a04acc1af', 'SKU-NSWOLED', 'Nintendo Switch OLED', 'Nintendo', 349.00, 'Console hybride avec écran OLED vibrant.', NOW()),
    ('393a5d1b-a41c-4303-af62-08975d1eb87a', 'SKU-SGS24', 'Samsung Galaxy S24', 'Samsung', 999.00, 'Smartphone premium avec écran AMOLED et nouvelles fonctions IA.', NOW()),
    ('c5ecb3aa-f57c-4a6a-af59-ba7fc413d503', 'SKU-XPS15', 'Dell XPS 15', 'Dell', 1899.00, 'Laptop professionnel 15'''' avec écran InfinityEdge.', NOW()),
    ('406fa8b4-f8ba-497f-b196-bccbfb90dab0', 'SKU-APP2', 'AirPods Pro 2', 'Apple', 299.00, 'Écouteurs premium avec réduction de bruit active améliorée.', NOW()),
    ('fe3095f7-0361-441c-86d3-205e9d620229', 'SKU-WH1000XM5', 'Sony WH-1000XM5', 'Sony', 399.00, 'Casque à réduction de bruit parmi les meilleurs du marché.', NOW()),
    ('b648ac0a-3bbb-4629-bb54-6ccfabf95efe', 'SKU-PIXEL9', 'Google Pixel 9', 'Google', 899.00, 'Smartphone Pixel nouvelle génération avec caméra IA avancée.', NOW()),
    ('9396b9bb-007b-4419-b9eb-36dd71a4e305', 'SKU-MXM3S', 'Logitech MX Master 3S', 'Logitech', 119.00, 'Souris ergonomique haut de gamme pour productivité.', NOW()),
    ('ceb06a7c-466a-4fb0-a653-5d2751b0720f', 'SKU-LG27-4K', 'LG 27'''' 4K Monitor', 'LG', 379.00, 'Écran 27 pouces UHD idéal pour travail et création.', NOW()),
    ('ca081535-3f9d-4678-bdf7-cf7148340b0e', 'SKU-HLL2350DW', 'Brother HL-L2350DW Laser Printer', 'Brother', 129.00, 'Imprimante laser compacte et économique.', NOW());
--rollback DELETE FROM products WHERE sku IN ('SKU-IPH15P','SKU-MBAM3','SKU-PS5SLIM','SKU-NSWOLED','SKU-SGS24','SKU-XPS15','SKU-APP2','SKU-WH1000XM5','SKU-PIXEL9','SKU-MXM3S','SKU-LG27-4K','SKU-HLL2350DW');
