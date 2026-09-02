INSERT INTO users (username, password, role)
VALUES (
           'admin',
           '$2a$10$ilgAN5HSUdjanxvtej2eHOp6EbmWW/vZdU0PuWyCQfasSBkDJYrk6',
           'ADMIN'
       )
    ON CONFLICT DO NOTHING;

INSERT INTO users (username, password, role)
VALUES (
           'alice',
           '$2a$10$TmOlyy/8mp.cCe30AhbYgO1UY97Gx3.Q4THUnGipmv779pVM0ROBi',
           'USER'
       )
    ON CONFLICT DO NOTHING;
INSERT INTO products (name, price, stock) VALUES ('機械鍵盤', 2999.99, 10);
INSERT INTO products (name, price, stock) VALUES ('人體工學椅', 8800.00, 5);
INSERT INTO products (name, price, stock) VALUES ('4K 螢幕', 12000.00, 3);
