# Code Review v2 草稿

## Review 狀態

- 專案類型：Spring Boot Backend REST API
- 目前主要技術：Java 21、Spring Boot、Spring Security、JWT、Spring Data JPA、Swagger / OpenAPI、Docker、PostgreSQL
- Review 版本：Draft v0.2

本報告為目前開發過程的初步 Code Review。

內容包含：

- 開發過程中實際遇到並修正的問題
- 已實際測試確認的安全性問題
- 從目前 API 設計觀察到、後續需要進一步確認的項目
- 開發環境與 Runtime 問題
- 目前決定暫時不修改的合理設計取捨

完整版本將在後續通讀整個專案原始碼後，再補上更精確的程式碼位置、實際證據與完整修正結果。

---

# 一、🔴 嚴重問題

## CR-001 — API 回傳使用者密碼

### 1. 位置

- `src/main/java/com/trading/platform/entity/User.java`
- Order API Response
- `GET /api/orders`

### 2. 問題描述

原本呼叫：

```http
GET /api/orders
```

Response 會出現：

```json
"user": {
  "id": 1,
  "username": "admin",
  "password": "admin123",
  "role": "ADMIN"
}
```

原因是 `Order` 關聯 `User Entity`，而 Jackson 在序列化時直接把 `password` 一起輸出。

### 3. 為什麼是問題

API 不應回傳使用者密碼。

即使密碼未來改成 BCrypt hash，也不應將 password hash 暴露給 Client。

可能造成：

- 使用者敏感資訊洩漏
- Password hash 洩漏
- 增加帳號遭受攻擊的風險
- Entity 新增敏感欄位時可能再次意外暴露

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

在 `User.java`：

```java
@JsonIgnore
private String password;
```

重新測試 `/api/orders`：

```json
"user": {
  "id": 1,
  "role": "ADMIN",
  "username": "admin"
}
```

`password` 已經消失。

### 5. 已提交

```text
fix: hide user password from API response
```

---

## CR-002 — Spring Data Repository 與 Entity 欄位不一致

### 1. 位置

- `ProductRepository.java`
- `Product.java`

### 2. 問題描述

Repository 曾存在依照 Entity 不存在欄位建立的查詢方法，例如：

```java
findByCategory(...)
```

但 `Product Entity` 沒有對應的 `category` property。

### 3. 為什麼是問題

Spring Data JPA 會根據 Repository method name 自動解析 Entity property。

如果 property 不存在，Spring ApplicationContext 初始化 Repository 時可能直接失敗。

可能流程：

```text
程式通過編譯
↓
Spring Boot 初始化 Repository
↓
Spring Data 解析 method name
↓
找不到 Product.category
↓
Repository 建立失敗
↓
ApplicationContext 啟動失敗
```

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

無實際使用需求的 `findByCategory(...)` 已移除，使 Repository 與目前 `Product Entity` 定義一致。

### 5. 已提交

已包含於前期 Startup Blocking Issues 修正。

---

## CR-003 — JwtAuthenticationFilter Bean 設定問題造成啟動失敗

### 1. 位置

- `security/JwtAuthenticationFilter.java`
- `config/SecurityConfig.java`

### 2. 問題描述

`SecurityConfig` 需要：

```java
JwtAuthenticationFilter
```

但 Filter 曾沒有正確註冊成 Spring Bean。

因此出現類似：

```text
required a bean of type
JwtAuthenticationFilter
that could not be found
```

### 3. 為什麼是問題

Spring 無法完成 Dependency Injection，因此無法建立 Security Configuration。

可能流程：

```text
JwtAuthenticationFilter Bean 不存在
↓
SecurityConfig 無法注入
↓
SecurityFilterChain 無法建立
↓
ApplicationContext 啟動失敗
↓
Spring Boot 無法啟動
```

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

`JwtAuthenticationFilter` 已正確納入 Spring Bean 管理。

目前 Security Filter 可以正常載入。

### 5. 已提交

已包含於前期 Security / Startup 修正。

---

## CR-004 — JWT 方法呼叫與定義不一致造成編譯失敗

### 1. 位置

- `service/AuthService.java`
- `security/JwtUtil.java`

### 2. 問題描述

JWT 功能開發期間，`generateToken()` 的 method signature 與呼叫端曾不同步。

例如呼叫：

```java
generateToken(username, role)
```

但實際定義可能只有：

```java
generateToken(username)
```

### 3. 為什麼是問題

Java 在 Compile Time 找不到符合參數的方法。

可能造成：

```text
Compilation Error
↓
Maven Build Failure
↓
專案無法執行
```

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

`AuthService` 與 `JwtUtil` 已統一。

目前 JWT 可以同時包含：

- username
- role
- issuedAt
- expiration

登入後可以正常產生 JWT。

### 5. 已提交

已包含於 JWT Authentication 功能修正。

---

## CR-005 — DTO / Service Getter 使用不一致造成編譯失敗

### 1. 位置

- `ProductService.java`
- `ProductRequest.java`

### 2. 問題描述

`ProductRequest` 實際為 Java Record。

因此 Record 的 accessor 應使用：

```java
request.name();
request.price();
request.stock();
```

而不是一般 Java Bean 的：

```java
request.getName();
request.getPrice();
request.getStock();
```

原本 Service 與 DTO 的 accessor 使用方式不一致。

### 3. 為什麼是問題

如果呼叫 DTO 中不存在的方法，Java 會直接產生 Compilation Error。

可能造成：

```text
Compilation Error
↓
Maven Build Failure
↓
Spring Boot 無法執行
```

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

`ProductService` 已依照 `ProductRequest record` 的實際定義使用正確 accessor。

目前專案可以正常編譯與執行。

### 5. 已提交

已包含於 Startup Blocking Issues 修正。

---

## CR-006 — 使用者密碼以明文方式儲存與比對

### 1. 位置

- `User.java`
- `AuthService.java`
- `data.sql`
- `SecurityConfig.java`

### 2. 問題描述

原本測試帳號使用：

```text
admin / admin123
alice / alice123
```

資料庫曾直接儲存明文密碼。

登入邏輯也曾直接進行字串比較，例如：

```java
user.getPassword().equals(request.getPassword())
```

### 3. 為什麼是問題

使用者密碼不應以明文方式儲存在 Database。

如果 Database 發生資料洩漏，攻擊者可以直接取得使用者真正的密碼。

即使是測試系統，也應展示基本的 Password Hashing 安全設計。

建議使用：

```java
PasswordEncoder
BCryptPasswordEncoder
```

登入驗證則使用：

```java
passwordEncoder.matches(...)
```

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

目前：

- Database 中使用者密碼已改成 BCrypt hash
- `data.sql` 已改為 BCrypt hash
- `SecurityConfig` 已提供 `PasswordEncoder`
- `AuthService` 已改用 `PasswordEncoder.matches()`
- Swagger 已實際測試登入
- 正確帳號密碼可成功取得 JWT

### 5. 已提交

```text
fix: secure passwords with BCrypt
```

---

## CR-007 — JWT Secret 存在公開 Fallback

### 1. 位置

- `security/JwtUtil.java`
- `src/main/resources/application.yml`
- IntelliJ Run Configuration

### 2. 問題描述

JWT Secret 雖然曾改成透過環境變數讀取，但原本仍存在公開 fallback，例如：

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-in-prod-please-rotate-this-secret}
```

這代表如果沒有設定 `JWT_SECRET`，系統仍會使用 Repository 中公開可見的 Secret。

### 3. 為什麼是問題

JWT Secret 是 JWT Signature 的核心敏感資訊。

不應直接存在：

- Java Source Code
- `application.yml` 明文
- Git Repository
- 公開 fallback value

如果 JWT Secret 洩漏，可能破壞 JWT Token 的可信任基礎。

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

目前改為：

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration-ms: ${JWT_EXPIRATION_MS:3600000}
```

真正的 JWT Secret 改由 IntelliJ Run Configuration 的 Environment Variables 提供。

已重新啟動 Spring Boot 並測試 Swagger Login，JWT 可正常產生。

### 5. 已提交

```text
fix: externalize JWT and database secrets
```

---

## CR-008 — Database Password 寫死在 application.yml

### 1. 位置

- `src/main/resources/application.yml`
- IntelliJ Run Configuration

### 2. 問題描述

原本 `application.yml` 中直接寫入 PostgreSQL 連線密碼，例如：

```yaml
spring:
  datasource:
    username: trading
    password: trading123
```

由於 `application.yml` 會跟著原始碼進入 Git Repository，真實 Database Credential 可能因此被提交到版本控制。

### 3. 為什麼是問題

Database Password 屬於敏感憑證。

不應直接存在 Git 可追蹤的設定檔中。

可能後果：

- Repository 外洩時 Database Credential 一起外洩
- 其他取得 Repository 的人員可以看到密碼
- Production 若沿用相同方式，安全風險更高

**嚴重程度：🔴 嚴重**

### 4. 狀態

✅ **已修正**

原本：

```yaml
password: trading123
```

改為：

```yaml
password: ${DB_PASSWORD}
```

實際 Database Password 改由 Environment Variable 提供。

IntelliJ Run Configuration 已設定 `DB_PASSWORD`。

完成後重新驗證：

```text
Spring Boot
↓
PostgreSQL Connection
↓
ApplicationContext
↓
Tomcat 8080
↓
Swagger
↓
Login
↓
JWT
```

均可正常運作。

### 5. 已提交

```text
fix: externalize JWT and database secrets
```

---

# 二、🟡 中等問題

## CR-009 — API 直接回傳 JPA Entity

### 1. 位置

- `Order.java`
- `OrderController.java`
- `OrderService.java`
- `OrderResponse.java`

### 2. 問題描述

原本 `OrderController` 的訂單 API 直接將 JPA Entity 當作 API Response 回傳。

例如：

```java
@PostMapping
public Order placeOrder(...)
```

以及：

```java
@GetMapping
public List<Order> myOrders(...)
```

因此原本 `/api/orders` Response 結構可能直接包含：

```text
Order
├── User
└── Product
```

Spring / Jackson 會根據 `Order Entity` 以及關聯的 `User`、`Product` 直接產生 JSON Response。

這也是 CR-001 中 `password` 曾被意外輸出的主要設計原因之一。

Entity 未來如果新增欄位，API Response 也可能跟著改變，甚至暴露原本不應提供給 Client 的資料。

### 3. 為什麼是問題

JPA Entity 的主要用途是描述 Persistence Model。

API Response 則屬於對外提供的 API Contract。

兩者直接綁定可能造成：

- Entity 欄位意外暴露
- API Response 與 Database Model 高度耦合
- Entity 修改時 API Contract 被動改變
- 關聯 Entity 可能被一起序列化
- 後續 API 版本維護較困難

因此建立 `OrderResponse DTO`，只選擇允許 Client 取得的欄位。

修改後形成：

```text
Database
↓
Entity
↓
Service
↓
OrderResponse DTO
↓
Controller
↓
JSON
```

目前 `OrderResponse` 對外提供：

```text
OrderResponse
├── id
├── productId
├── productName
├── quantity
└── totalPrice
```

**嚴重程度：🟡 中等**

### 4. 狀態

✅ **已修正**

已建立：

```text
dto/OrderResponse.java
```

並將 `GET /api/orders`：

```java
List<Order>
```

修改為：

```java
List<OrderResponse>
```

同時將 `POST /api/orders`：

```java
Order
```

修改為：

```java
OrderResponse
```

`OrderService` 會將內部的 `Order Entity` 轉換成 `OrderResponse DTO` 後，再交由 Controller 回傳。

Swagger 已實際測試：

```text
GET /api/orders
→ HTTP 200
→ 成功回傳 OrderResponse
```

以及：

```text
POST /api/orders
→ HTTP 200
→ 成功建立訂單並回傳 OrderResponse
```

實際 Response：

```json
{
  "id": 2,
  "productId": 1,
  "productName": "機械鍵盤",
  "quantity": 1,
  "totalPrice": 2999
}
```

Response 已不再直接包含：

```text
User Entity
Product Entity
password
role
stock
```

因此 API 已不再直接將完整 `Order Entity` 作為 Response 回傳。



### 5. 已提交



```text
refactor: use OrderResponse DTO for order APIs
```

---
## CR-010 — Order Quantity 輸入驗證不足

### 1. 位置

- `OrderRequest.java`
- `OrderController.java`
- `SecurityConfig.java`

### 2. 問題描述

原本 `POST /api/orders` 沒有對訂單數量 `quantity` 做有效輸入驗證。

實際透過 Swagger 測試：

```json
{
  "productId": 1,
  "quantity": -100
}
```

系統原本仍回傳 HTTP 200，並成功建立訂單，例如：

```json
{
  "id": 3,
  "productId": 1,
  "productName": "機械鍵盤",
  "quantity": -100,
  "totalPrice": -299900
}
```

代表 API 可以接受負數數量。

另外，原本庫存扣減邏輯為：

```java
product.setStock(product.getStock() - quantity);
```

當：

```text
quantity = -100
```

實際運算會變成：

```text
stock - (-100)
= stock + 100
```

因此不只會產生負數訂單金額，甚至可能讓商品庫存反而增加。

### 3. 為什麼是問題

Order Quantity 應只能接受大於 0 的整數。

如果沒有輸入驗證，可能造成：

- `quantity = 0`
- `quantity < 0`
- `productId = null`
- 建立非法訂單
- 產生負數訂單金額
- 庫存數量異常增加
- Business Data 不一致

因此在 `OrderRequest` 加入 Bean Validation：

```java
@NotNull(message = "商品 ID 不可為空")
@Positive(message = "商品 ID 必須大於 0")
private Long productId;

@NotNull(message = "數量不可為空")
@Positive(message = "數量必須大於 0")
private Integer quantity;
```

並在 `OrderController` 加入：

```java
@Valid
```

例如：

```java
@PostMapping
public OrderResponse placeOrder(
        @Valid @RequestBody OrderRequest request,
        Authentication authentication) {
```

讓 Spring 在 Request 進入 Service 之前先執行輸入驗證。

**嚴重程度：🟡 中等**

### 4. 狀態

✅ **已修正**

修改後重新透過 Swagger 測試：

```json
{
  "productId": 1,
  "quantity": -98
}
```

目前系統已回傳：

```text
HTTP 400 Bad Request
```

表示負數 `quantity` 已在 Controller Validation 階段被攔截，不會再進入 `OrderService` 建立錯誤訂單。

另外，Validation Error 原本在 Error Dispatch 階段會再次被 Spring Security 攔截，造成：

```text
403 Forbidden
```

已在 `SecurityConfig` 加入：

```java
.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
```

並使用：

```java
import jakarta.servlet.DispatcherType;
```

讓 Spring Validation Error 可以正確回傳：

```text
400 Bad Request
```

Console 也已確認出現：

```text
MethodArgumentNotValidException
rejected value [-98]
數量必須大於 0
```

因此負數 quantity 已無法建立訂單。

### 5. 已提交



```text
fix: validate order request quantity
```

---

## ## CR-011 — 訂單建立與庫存扣減 Transaction 需要確認

### 1. 位置

已完成 Review：

* `OrderService.java`
* `OrderRepository.java`
* `ProductRepository.java`
* `Product.java`
* `schema.sql`

### 2. 原始問題

原本庫存扣減位於獨立方法：

```java
@Transactional
public synchronized void deductStock(Product product, int quantity)
```

但該方法由同一個 `OrderService` 內的 `placeOrder()` 呼叫，可能因為 Self-invocation 繞過 Spring Proxy，導致方法本身的 `@Transactional` 不生效。

此外，庫存扣減與訂單建立不在同一個 Transaction Boundary，可能發生：

```text
庫存扣減成功
↓
訂單建立失敗
↓
資料不一致
```

原本的 `synchronized` 也只能保護同一個 JVM 內的 `OrderService` 實例，無法防止多台應用程式同時更新相同商品。

### 3. 修正內容

#### 3.1 完整下單流程使用同一個 Transaction

已將 `@Transactional` 加在完整業務流程入口：

```java
@Transactional
public OrderResponse placeOrder(String username, OrderRequest request)
```

下列操作現在位於同一個 Transaction 中：

```text
驗證訂單資料
↓
查詢使用者與商品
↓
檢查庫存
↓
扣減商品庫存
↓
建立訂單
↓
Transaction Commit
```

若其中任何步驟拋出 Runtime Exception，訂單建立與庫存修改會一起 Rollback。

#### 3.2 移除獨立庫存 Transaction 與 synchronized

已移除：

```java
@Transactional
public synchronized void deductStock(...)
```

庫存改為直接在 `placeOrder()` 中扣減：

```java
product.setStock(
        product.getStock() - request.getQuantity()
);
```

`Product` 是目前 Transaction 管理中的 Entity，因此 Hibernate 會透過 Dirty Checking，在提交 Transaction 時更新庫存。

#### 3.3 加入樂觀鎖

`Product.java` 已加入：

```java
@Version
private Long version;
```

Hibernate 更新商品時會檢查版本，例如：

```sql
UPDATE products
SET stock = ?, version = ?
WHERE id = ? AND version = ?;
```

若兩個 Request 同時讀取並修改相同商品，先完成的 Transaction 會更新版本；後完成的 Transaction 因版本不一致而失敗並回滾，避免 Lost Update 與超賣。

#### 3.4 更新資料庫欄位

`schema.sql` 已加入：

```sql
version BIGINT NOT NULL DEFAULT 0
```

既有 PostgreSQL 資料也已完成：

```sql
UPDATE products
SET version = 0
WHERE version IS NULL;

ALTER TABLE products
ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE products
ALTER COLUMN version SET NOT NULL;
```

#### 3.5 改善資料查詢與驗證

使用者與商品查詢已由：

```java
.orElse(null)
```

改為：

```java
.orElseThrow(...)
```

避免查不到資料後繼續執行，造成 `NullPointerException`。

訂單資料也已加入實際檢查：

```java
private boolean validateOrder(OrderRequest request) {
    return request != null
            && request.getProductId() != null
            && request.getQuantity() != null
            && request.getQuantity() > 0;
}
```

#### 3.6 修正總價計算

已正確設定訂單總價：

```java
order.setTotalPrice(
        product.getPrice() * request.getQuantity()
);
```

並移除將商品價格轉成 `int` 的寫法，避免小數金額遺失。

### 4. 驗證結果

使用 Swagger 執行：

```http
POST /api/orders
```

Request：

```json
{
  "productId": 16,
  "quantity": 1
}
```

API 回傳：

```json
{
  "id": 4,
  "productId": 16,
  "productName": "機械鍵盤",
  "quantity": 1,
  "totalPrice": 2999.99
}
```

HTTP Status：

```text
200 OK
```

下單前商品資料：

```text
stock = 10
version = 0
```

下單後查詢 PostgreSQL：

```sql
SELECT id, name, stock, version
FROM products
WHERE id = 16;
```

結果：

```text
id = 16
name = 機械鍵盤
stock = 9
version = 1
```

確認：

* 訂單建立成功
* 商品庫存正確扣減
* 商品版本由 `0` 增加為 `1`
* 商品價格 `2999.99` 未遺失小數
* Transaction 與 JPA 樂觀鎖正常運作

### 5. Review 結論

**嚴重程度：🟡 中等**

✅ **已修正並通過基本功能測試**

目前訂單建立與庫存扣減已位於相同 Transaction Boundary，並使用 `@Version` 樂觀鎖防止並發更新造成庫存資料遺失。

後續可再增加自動化並發測試，驗證兩個 Request 同時購買最後一件商品時，只允許其中一筆訂單成功。
```text
fix: ensure atomic order creation and stock deduction
```


---


## CR-012 — 金額資料型別需要確認

### 1. 位置

待完整 Review：

- `Product.java`
- `Order.java`
- `ProductRequest.java`
- Order 金額計算邏輯

### 2. 問題描述

目前 API 曾出現：

```json
"price": 2999.99,
"totalPrice": 5998
```

需要確認 Java 是否使用：

```java
double
```

或：

```java
Double
```

處理金額。

### 3. 為什麼是問題

Floating Point 不適合直接處理需要精確計算的金額。

例如某些小數無法使用 binary floating-point 精確表示。

較安全的方式通常是：

```java
BigDecimal
```

例如：

```java
private BigDecimal price;
private BigDecimal totalPrice;
```

**嚴重程度：🟡 中等**

### 4. 狀態

⚠️ **待確認**

### 5. 後續處理

完整 Review Entity 與 Order 金額計算邏輯後確認。

---

## CR-013 — Port 8080 衝突造成啟動失敗

### 1. 位置

類型：

```text
Environment / Runtime Issue
```

### 2. 問題描述

開發過程曾遇到：

```text
Port 8080 was already in use
```

原因是其他 Java / Spring Boot Process 已占用 Port 8080。

### 3. 為什麼是問題

即使程式碼完全正確，只要 Port 已被其他 Process 使用，Tomcat 就無法 Bind Port，造成 Spring Boot 啟動失敗。

Windows 可使用：

```powershell
netstat -ano | findstr :8080
```

確認占用 Port 的 PID。

**嚴重程度：🟡 中等**

但這不是核心 Java 程式碼缺陷。

### 4. 狀態

✅ **已處理**

目前 Spring Boot 已可正常使用 Port 8080 啟動。

### 5. 後續處理

正式 Code Review 中保留，但分類為：

```text
Environment / Runtime Issue
```

---

## CR-014 — Java / JAVA_HOME 環境設定造成 Maven 無法正常執行

### 1. 位置

類型：

```text
Development Environment Issue
```

使用環境：

- Windows 11
- IntelliJ IDEA
- Maven Wrapper
- Java 21

### 2. 問題描述

曾遇到 Java / JDK 環境設定不一致。

例如：

```powershell
java -version
```

與：

```powershell
.\mvnw.cmd -version
```

可能使用不同 JDK。

### 3. 為什麼是問題

即使程式碼正確，錯誤的 Java Runtime 仍可能造成：

```text
Java Version 不一致
↓
Maven 使用錯誤 JDK
↓
Compile / Runtime 問題
↓
專案無法正常執行
```

需要確認：

```powershell
java -version
echo $env:JAVA_HOME
.\mvnw.cmd -version
```

本專案要求 Java 21。

**嚴重程度：🟡 中等**

但屬於 Development Environment 問題。

### 4. 狀態

✅ **已處理**

目前已統一使用 Java 21。

### 5. 後續處理

不需要修改 Business Logic。

---

## CR-015 — Docker / PostgreSQL 執行環境問題

### 1. 位置

類型：

```text
Development Environment Issue
```

相關：

- Docker Desktop
- `docker-compose.yml`
- PostgreSQL

### 2. 問題描述

曾遇到 Docker Desktop / PostgreSQL Container 尚未正常啟動，導致 Spring Boot 無法連接 Database。

### 3. 為什麼是問題

可能流程：

```text
Docker / PostgreSQL 未啟動
↓
Spring Boot 啟動
↓
DataSource 建立連線
↓
Database Connection Failed
↓
ApplicationContext 啟動失敗
```

可使用：

```powershell
docker compose up -d
docker compose ps
```

確認 PostgreSQL Container 狀態。

**嚴重程度：🟡 中等**

但屬於開發環境問題，而不是核心 Java 程式碼缺陷。

### 4. 狀態

✅ **已處理**

Docker Desktop 與 PostgreSQL Container 已可正常運作。

Spring Boot 目前可成功連線 PostgreSQL。

### 5. 後續處理

正式報告分類為：

```text
Development Environment Issue
```

---

# 三、🟢 輕微／程式品質改善

## CR-016 — Swagger API 文件可以再完善

### 1. 位置

- Swagger / OpenAPI Configuration
- Controller API Documentation

### 2. 問題描述

目前已完成：

```text
Login
↓
取得 JWT
↓
Swagger Authorize
↓
Bearer Token
↓
呼叫受保護 API
```

Swagger JWT Authentication 流程已可以正常運作。

但 API Documentation 仍可以再補充完整。

### 3. 為什麼是問題

目前主要影響的是：

- API 可讀性
- 開發者使用體驗
- API Error Response 說明
- 面試官理解 API 的速度

未來可加入：

```java
@Operation
@ApiResponse
```

例如說明：

```text
200 成功
400 Request 錯誤
401 未登入
403 無權限
404 Resource 不存在
```

**嚴重程度：🟢 輕微**

### 4. 狀態

⚠️ **後續改善**

目前 Swagger 已經足以進行 API 測試，因此不是目前最高優先項目。

### 5. 後續處理

等主要 Security、Validation、Transaction、Money Calculation 問題處理完成後，再完善 Swagger Documentation。

---

# 四、合理取捨與目前不建議修改項目

## Trade-off 001 — `@JsonIgnore` 暫時保留

### 1. 位置

`User.java`

### 2. 目前設計

目前使用：

```java
@JsonIgnore
private String password;
```

避免 API Response 輸出 password。

### 3. 為什麼目前不修改

長期而言：

```text
Entity
↓
Response DTO
↓
JSON
```

會比單純依賴 `@JsonIgnore` 更完整。

但是目前 `@JsonIgnore`：

- 修改範圍小
- 已實際解決 Password 洩漏
- 不影響目前 JWT Login
- 不需要大規模修改 Controller / Service
- 適合作為目前階段的安全修補

### 4. 決定

✅ **目前保留**

等後續進行 Response DTO 重構時再改善。

---

## Trade-off 002 — OrderRequest 僅包含 productId + quantity

### 1. 位置

`OrderRequest.java`

### 2. 目前設計

目前 Request：

```json
{
  "productId": 1,
  "quantity": 2
}
```

### 3. 為什麼這是合理設計

雖然欄位很少，但 Client 不應自行決定：

```text
username
role
product price
totalPrice
```

較合理流程：

```text
JWT
↓
判斷目前登入 User

productId
↓
Database 查詢 Product
↓
取得 DB 中真正 price

quantity × DB price
↓
Backend 計算 totalPrice
```

因此 Request 只傳：

```text
productId
quantity
```

反而可以降低 Client 任意修改敏感 Business Data 的機會。

### 4. 決定

✅ **目前不因為欄位少而修改**

真正需要改善的是 CR-010 的 Input Validation，而不是增加不必要欄位。

---

# 五、目前整體 Review 結論

目前專案已經從：

```text
❌ Compilation Error
↓
❌ Spring Boot 啟動失敗
↓
❌ Security / JWT Bean 問題
↓
❌ Repository / Entity 問題
↓
✅ Spring Boot 正常啟動
↓
✅ Swagger 正常
↓
✅ Login 取得 JWT
↓
✅ Swagger Authorize
↓
✅ POST /api/orders
↓
✅ GET /api/orders
↓
❌ API 暴露 password
↓
✅ @JsonIgnore 修正
↓
❌ 使用者密碼明文儲存
↓
✅ BCrypt 修正
↓
❌ JWT Secret 存在公開 fallback
↓
✅ JWT_SECRET 環境變數化
↓
❌ Database Password 寫死於 application.yml
↓
✅ DB_PASSWORD 環境變數化
↓
✅ IntelliJ Run Configuration 設定完成
↓
✅ Spring Boot 重新啟動成功
↓
✅ PostgreSQL 正常連線
↓
✅ Swagger Login / JWT 再驗證成功
↓
✅ Git Commit + Push
```

目前專案已經從：

```text
無法正常 Build / Run
```

推進到：

```text
核心 API 可以實際執行
↓
Authentication / Authorization 可以運作
↓
主要 Credential Security 問題已處理
↓
進入 Business Logic / Data Consistency Review
```

---

# 六、已完成 P1 問題

| 優先級 | Code Review | 項目 | 狀態 |
|---|---|---|---|
| 🔴 P1 | CR-001 | API Password 洩漏 | ✅ 已修正 |
| 🔴 P1 | CR-002 | Repository / Entity 不一致 | ✅ 已修正 |
| 🔴 P1 | CR-003 | JWT Filter Bean 問題 | ✅ 已修正 |
| 🔴 P1 | CR-004 | JWT Method Signature 問題 | ✅ 已修正 |
| 🔴 P1 | CR-005 | DTO / Service Accessor 問題 | ✅ 已修正 |
| 🔴 P1 | CR-006 | 使用者密碼明文儲存 | ✅ BCrypt 修正 |
| 🔴 P1 | CR-007 | JWT Secret 管理 | ✅ 環境變數化 |
| 🔴 P1 | CR-008 | Database Password 管理 | ✅ 環境變數化 |

---

# 七、下一階段優先順序

| 優先級 | Code Review | 項目 | 狀態 |
|---|---|---|---|
| 🟡 P2 | CR-009 | Order Response DTO | ⚠️ 待改善 |
| 🟡 P2 | CR-010 | Quantity Validation | ⚠️ 待確認 |
| 🟡 P2 | CR-011 | Transaction / 庫存一致性 | ⚠️ 待確認 |
| 🟡 P2 | CR-012 | Money / BigDecimal | ⚠️ 待確認 |
| 🟢 P3 | CR-016 | Swagger 文件完善 | ⚠️ 後續 |

---

# 八、Draft v0.2 結論

目前第一階段主要處理的是：

```text
Build
↓
Startup
↓
Security
↓
Authentication
↓
Credential Protection
```

目前已修正的主要問題包括：

1. API 暴露 Password
2. Repository / Entity 不一致造成啟動失敗
3. JwtAuthenticationFilter Bean 問題
4. JWT Method Signature 編譯問題
5. DTO / Service Accessor 編譯問題
6. 使用者密碼明文儲存
7. JWT Secret 公開 Fallback
8. Database Password 硬編碼

目前下一階段不急著增加新功能。

建議優先順序：

```text
CR-009
Order Response DTO
↓
CR-010
Input Validation
↓
CR-011
Transaction / Stock Consistency
↓
CR-012
Money / BigDecimal
↓
CR-016
Swagger Documentation
```

其中 `Validation`、`Transaction`、`Money Calculation` 與訂單 Business Logic 正確性直接相關，應優先於單純的文件與程式碼美化。

---

> **目前文件狀態：Code Review Draft v0.2**
>
> 本文件仍屬初步 Code Review。
>
> 後續將繼續通讀完整專案原始碼，把所有「⚠️ 待確認」項目逐一改成：
>
> 1. 明確檔案位置
> 2. 實際問題程式碼
> 3. 問題發生原因
> 4. 實際修正方式
> 5. Swagger / Runtime 測試結果
> 6. Git Commit 紀錄
>
> 完成後再整理為正式 Code Review 版本。