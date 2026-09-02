### Review 狀態： 初步草稿

```
專案類型： Spring Boot Backend REST API
目前主要技術： Java 21、Spring Boot、Spring Security、JWT、Spring Data JPA、Swagger / OpenAPI、Docker、PostgreSQL

本報告為目前開發過程的初步 Code Review，包含已實際遇到並修正的問題，以及從目前 API 設計觀察到、後續需要進一步確認的項目。完整版本需再通讀整個專案原始碼後更新。
###
1. 🔴 嚴重問題
   CR-001 — API 回傳使用者密碼

位置

src/main/java/com/trading/platform/entity/User.java
Order API Response
問題描述

原本呼叫：

GET /api/orders

Response 會出現：

"user": {
"id": 1,
"username": "admin",
"password": "admin123",
"role": "ADMIN"
}

原因是 Order 關聯 User Entity，而 Jackson 在序列化時直接把 password 一起輸出。

為什麼是問題

API 不應回傳密碼。即使未來改成 BCrypt hash，也不應將 hash 暴露給 Client。

可能造成敏感資訊洩漏。

嚴重程度：🔴 嚴重

狀態：✅ 已修正

在 User.java：

@JsonIgnore
private String password;

重新測試 /api/orders：

"user": {
"id": 1,
"role": "ADMIN",
"username": "admin"
}

password 已經消失。

已提交：

fix: hide user password from API response
CR-002 — Spring Data Repository 方法與 Entity 欄位不一致

位置

ProductRepository.java
Product.java
問題描述

Repository 曾存在依照 Entity 不存在欄位建立的查詢方法，例如：

findByCategory(...)

但 Product Entity 沒有相對應的 category property。

為什麼是問題

Spring Data JPA 會根據 Repository method name 自動解析 Entity property。

如果 property 不存在，Spring ApplicationContext 初始化時就可能失敗。

後果
程式可以通過部分編譯
↓
Spring Boot 初始化 Repository
↓
找不到 Entity property
↓
ApplicationContext 啟動失敗

嚴重程度：🔴 嚴重

狀態：✅ 已修正

Repository method 已調整，使其與 Entity 欄位一致。

CR-003 — JWT Authentication Filter Bean 設定問題造成啟動失敗

位置

security/JwtAuthenticationFilter.java
config/SecurityConfig.java
問題描述

SecurityConfig 需要：

JwtAuthenticationFilter

但 Filter 曾沒有正確註冊成 Spring Bean。

因此出現類似：

required a bean of type
JwtAuthenticationFilter
that could not be found
為什麼是問題

Spring 無法完成 Dependency Injection，因此無法建立 Security Configuration。

後果

整個 Spring Boot Application 無法啟動。

嚴重程度：🔴 嚴重

狀態：✅ 已修正

Filter 已正確納入 Spring Bean 管理。

CR-004 — JWT 方法呼叫與定義不一致造成編譯失敗

位置

service/AuthService.java
security/JwtUtil.java
問題描述

JWT 功能開發期間，generateToken() 的 method signature 與呼叫端曾不同步。

例如呼叫：

generateToken(username, role)

但實際定義可能只有：

generateToken(username)
為什麼是問題

Java 在 compile time 找不到符合參數的方法。

後果
Compilation Error
→ Maven build failure
→ 專案無法執行

嚴重程度：🔴 嚴重

狀態：✅ 已修正

AuthService 與 JwtUtil 已統一。

CR-005 — DTO / Service getter 使用不一致造成編譯問題

位置

ProductService.java
ProductRequest.java
問題描述

Service 與 DTO 的 getter / property 使用曾有不一致，需要依實際 DTO 定義使用正確的方法，例如：

request.getName();
request.getPrice();
request.getStock();
為什麼是問題

如果呼叫 DTO 不存在的方法，Java 會直接產生 compilation error。

嚴重程度：🔴 嚴重

狀態：✅ 已修正

目前專案已能正常編譯及執行。

CR-006 — 使用者密碼儲存方式需要確認

位置

待完整 Review：

User.java
AuthService.java
data.sql
SecurityConfig.java
問題描述

測試過程中曾看到：

admin123

需要確認資料庫中的 password 是否也是明文。

如果 DB 儲存：

admin123

而不是 BCrypt：

$2a$10$....

就屬於嚴重安全問題。

建議

使用：

PasswordEncoder
BCryptPasswordEncoder

嚴重程度：🔴 嚴重

狀態：✅ 已修正：資料庫密碼改為 BCrypt hash，
登入改用 PasswordEncoder.matches() 驗證，
Swagger 實測登入成功並取得 JWT。
CR-007 — JWT Secret 管理方式需要確認

位置

JwtUtil.java
application.yml
問題描述

需要確認 JWT Secret 是否直接寫死在 Java 或 application.yml。

例如不應：

jwt:
secret: my-secret-123

並直接 commit 到 GitHub。

建議：

jwt:
secret: ${JWT_SECRET}

再透過環境變數設定。

嚴重程度：🔴 嚴重

狀態：⚠️ 待完整 Review 確認
2. 🟡 中等問題
   CR-008 — API 直接回傳 JPA Entity

目前 /api/orders Response 結構類似：

Order
├── User
└── Product
問題

API 很可能直接序列化 Order Entity。

這也是前面 password 曾被意外輸出的主要設計原因之一。

Entity 未來增加欄位時，API 可能不小心跟著暴露。

建議

未來建立：

OrderResponse
UserResponse
ProductResponse

形成：

Database
↓
Entity
↓
Service
↓
Response DTO
↓
Controller
↓
JSON

嚴重程度：🟡 中等

狀態：⚠️ 建議後續重構
CR-009 — Order quantity 輸入驗證需要加強

目前 Request：

{
"productId": 1,
"quantity": 2
}

需要確認是否可以輸入：

{
"productId": 1,
"quantity": -100
}
建議
@NotNull
private Long productId;

@NotNull
@Min(1)
private Integer quantity;

Controller 使用：

@Valid @RequestBody OrderRequest request

嚴重程度：🟡 中等

狀態：⚠️ 待確認
CR-010 — 訂單建立與庫存扣減 Transaction 需要確認

目前建立訂單：

POST /api/orders

已成功測試。

例如：

{
"productId": 1,
"quantity": 2
}

但需要進一步確認：

檢查庫存
↓
建立 Order
↓
扣除 Product stock

是否在同一 Transaction。

建議 Service 使用：

@Transactional

否則可能發生訂單建立成功，但庫存更新失敗的資料不一致。

嚴重程度：🟡 中等

狀態：⚠️ 待確認
CR-011 — 金額資料型別需要確認

目前 API 有：

"price": 2999.99,
"totalPrice": 5998

如果 Java 使用：

double
Double

處理金額，可能產生浮點精度問題。

建議

使用：

BigDecimal

例如：

private BigDecimal price;
private BigDecimal totalPrice;

嚴重程度：🟡 中等

狀態：⚠️ 待確認
CR-012 — Port 8080 衝突造成啟動失敗

類型：執行環境問題

曾遇到：

Port 8080 was already in use
原因

其他 Java / Spring Boot process 已經使用 8080。

處理

Windows 可檢查：

netstat -ano | findstr :8080

再停止舊 process 或調整 Spring Boot port。

嚴重程度：🟡 中等

狀態：✅ 已處理

此問題不是程式碼設計錯誤，因此正式 Code Review 應分類為 Environment / Runtime Issue。

CR-013 — Java / JAVA_HOME 環境設定造成 Maven 無法執行

曾遇到 Java/JDK 環境設定問題，使 Terminal 無法正常執行 Maven Wrapper。

後果

即使程式碼正確，也無法：

.\mvnw.cmd spring-boot:run
處理

確認：

java -version
echo $env:JAVA_HOME
.\mvnw.cmd -version

目前使用 Java 21。

嚴重程度：🟡 中等

狀態：✅ 已處理
CR-014 — Docker / Database 執行環境問題

曾遇到 Docker Desktop / container 尚未正常啟動，造成 DB 服務不可用。

後果
Spring Boot
↓
DataSource
↓
Database connection failed
處理

確認 Docker Desktop 已啟動，再檢查：

docker compose up -d
docker compose ps

嚴重程度：🟡 中等

狀態：✅ 已處理

這同樣屬於開發環境問題，而非核心 Java 程式碼缺陷。

3. 🟢 輕微／程式品質改善
   CR-015 — Swagger API 文件可以再完整

目前已完成：

Login
↓
取得 JWT
↓
Swagger Authorize
↓
Bearer Token
↓
受保護 API

這部分已正常運作。

未來可加入：

@Operation
@ApiResponse

例如描述：

200 成功
400 Request 錯誤
401 未登入
403 無權限
404 商品不存在

嚴重程度：🟢 輕微

4. 合理取捨：目前不建議修改
   @JsonIgnore 暫時保留

目前：

@JsonIgnore
private String password;

長期來說，使用：

Entity → DTO → Response

會比單純 @JsonIgnore 更完整。

但是現在 @JsonIgnore：

修改範圍小
已實際解決 password 洩漏
不影響目前 JWT Login
適合作為目前階段的安全修補

因此：

目前保留 @JsonIgnore，之後進行 DTO 重構時再改善。

OrderRequest 只有 productId + quantity 是合理設計

目前：

{
"productId": 1,
"quantity": 2
}

看起來資料很少，但這反而是合理的。

Client 不應該自行決定：

username
role
product price
totalPrice

理想流程：

JWT → 判斷目前 User

productId → DB 查 Product
↓
取得 price

quantity × DB price
↓
後端計算 totalPrice

因此目前這種 Request DTO 設計不建議因為欄位少而修改。

5. 目前整體 Review 結論

目前專案已經從：

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
✅ Git commit + push

因此目前已經從「無法正常 build / run」推進到「核心 API 可以實際測試，開始進入安全性與程式品質 Review」階段。

下一階段優先順序
優先	項目	狀態
🔴 P1	API password 洩漏	✅ 已修正
🔴 P1	編譯／Spring Boot 啟動問題	✅ 已修正
🔴 P1	確認 DB 密碼是否 BCrypt	⚠️ 待查
🔴 P1	確認 JWT Secret 是否寫死	⚠️ 待查
🟡 P2	Order Response DTO	⚠️ 待改善
🟡 P2	quantity Validation	⚠️ 待查
🟡 P2	庫存 Transaction	⚠️ 待查
🟡 P2	金額 BigDecimal	⚠️ 待查
🟢 P3	Swagger 文件完善	後續

Draft v0.1 結論：目前最嚴重的已知 API 密碼暴露問題已修正；下一步不急著增加新功能，優先檢查「密碼加密、JWT Secret、Order DTO、Validation、Transaction」會比較合理。
```
### 這份先當作簡單 Code Review 草稿即可；等之後把整個專案原始碼交給我通讀，再把「⚠️ 待確認」全部變成有實際檔名、程式碼位置與證據的正式報告。