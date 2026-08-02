CREATE TABLE IF NOT EXISTS inspection_route (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_code VARCHAR(64) NOT NULL UNIQUE,
    route_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    waypoints_json TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO inspection_route (route_code, route_name, description, waypoints_json)
SELECT 'ROUTE-001', '东区示例航线', '默认演示航线',
       '[{"lat":31.2304,"lng":121.4737,"alt":80},{"lat":31.2330,"lng":121.4800,"alt":90}]'
WHERE NOT EXISTS (
    SELECT 1 FROM inspection_route WHERE route_code = 'ROUTE-001'
);
