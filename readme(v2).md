# Baasid Backend Interview — README（v2）

## 1. 專案簡介

本專案為 Java Spring Boot 交易平台後端，提供使用者登入、JWT 身分驗證、商品管理、訂單建立、庫存扣減及操作日誌等功能。

### 主要技術

- Java 21
- Spring Boot 4
- Spring Security
- JWT
- Spring Data JPA
- PostgreSQL
- Swagger / OpenAPI
- Maven
- Docker Compose

---

## 2. 主要變更

本次完成的主要修正與功能如下：

1. 修正 API 回傳資料暴露使用者密碼的問題。
2. 修正 Repository 與 Entity 不一致造成的啟動問題。
3. 修正 `JwtAuthenticationFilter` 的 Bean 注入問題。
4. 修正 JWT Method Signature 不一致造成的編譯問題。
5. 修正 DTO 與 Service Accessor 不一致造成的編譯問題。
6. 使用 BCrypt 加密及驗證使用者密碼。
7. 將 JWT Secret 改由 `JWT_SECRET` 環境變數提供。
8. 將資料庫密碼改由 `DB_PASSWORD` 環境變數提供。
9. 使用 `OrderResponse` DTO 回傳訂單資料，避免直接暴露 Entity。
10. 使用 Bean Validation 驗證商品 ID 與訂購數量。
11. 將訂單建立與庫存扣減放在同一個 Transaction 中。
12. 使用 `@Version` 樂觀鎖，降低併發下單造成庫存遺失更新或超賣的風險。
13. 使用 `BigDecimal` 計算商品價格與訂單總價。
14. 將 PostgreSQL 金額欄位改為 `NUMERIC(19,2)`。
15. 完成商品新增、修改及刪除的操作日誌。
16. 從 Spring Security `SecurityContext` 取得目前登入的操作者。
17. 記錄操作類型、資料類型、資料 ID，以及修改前後的 JSON 資料。
18. 使用 `JpaSpecificationExecutor` 完成 Audit Log 動態條件查詢。
19. Audit Log 支援依 `operator`、`action`、`entityType`、`entityId` 單獨或組合查詢。
20. 完成 Swagger、JWT、商品、訂單、庫存、金額及 Audit Log 的實際測試。
21. 保留相關 Git Commit 紀錄，並將修改 Push 至 GitHub。

---

## 3. 第三部分功能選擇與設計理由

### 3.1 選擇項目

本專案第三部分選擇：

**B：操作日誌（Audit Log）**

交易平台中的商品價格、庫存及商品資料變更，可能直接影響訂單與交易結果。因此，系統需要保存操作紀錄，方便確認：

- 由誰執行操作。
- 執行了什麼操作。
- 操作發生的時間。
- 被操作的資料類型與資料 ID。
- 修改前與修改後的資料。

### 3.2 已完成內容

已建立以下 Audit Log 元件：

- `AuditLog` Entity
- `AuditLogRepository`
- `AuditLogService`
- `AuditLogController`
- PostgreSQL `audit_logs` 資料表

商品進行下列操作時，系統會自動寫入日誌：

- `CREATE`：新增商品
- `UPDATE`：修改商品
- `DELETE`：刪除商品

每筆日誌記錄以下欄位：

- `operator`
- `action`
- `entityType`
- `entityId`
- `beforeData`
- `afterData`
- `createdAt`

欄位說明：

- `operator`：由 Spring Security 的 `SecurityContext` 取得目前登入的使用者。
- `action`：記錄 `CREATE`、`UPDATE` 或 `DELETE`。
- `entityType`：記錄被操作的資料類型，目前為 `PRODUCT`。
- `entityId`：記錄被操作的商品 ID。
- `beforeData`：保存修改或刪除前的資料。
- `afterData`：保存新增或修改後的資料。
- `createdAt`：記錄操作發生時間。

修改前後的資料會轉換成 JSON 字串保存，方便追蹤完整的資料變更內容。

### 3.3 Audit Log 查詢 API

Audit Log 查詢 API：

```http
GET /api/audit-logs
```

不帶任何條件時，查詢全部日誌：

```http
GET /api/audit-logs
```

依操作者查詢：

```http
GET /api/audit-logs?operator=admin
```

依操作類型查詢：

```http
GET /api/audit-logs?action=UPDATE
```

依資料類型查詢：

```http
GET /api/audit-logs?entityType=PRODUCT
```

依資料 ID 查詢：

```http
GET /api/audit-logs?entityId=100
```

多個條件也可以組合查詢：

```http
GET /api/audit-logs?operator=admin&action=DELETE
```

```http
GET /api/audit-logs?entityType=PRODUCT&entityId=100
```

```http
GET /api/audit-logs?operator=admin&action=CREATE&entityType=PRODUCT&entityId=100
```

查詢條件使用 Spring Data JPA 的：

```java
JpaSpecificationExecutor<AuditLog>
```

以及：

```java
Specification<AuditLog>
```

進行動態建立。

只有實際傳入的參數才會加入查詢條件，多個條件之間使用 `AND` 組合。

### 3.4 設計理由

選擇 Audit Log 的主要原因如下：

1. 提升商品資料異動的可追蹤性。
2. 發生資料異常時，可以還原操作過程。
3. 明確記錄操作者，強化責任歸屬。
4. 方便調查安全事件或人為操作錯誤。
5. 保留商品修改前後的資料，方便比對差異。
6. 使用 `Specification` 實作動態查詢，未來容易繼續加入日期、分頁及排序等條件。

---

## 4. 系統啟動方式

### 4.1 環境需求

啟動前請先安裝：

- JDK 21
- Docker Desktop
- Git

確認 Java 版本：

```bash
java -version
```

應顯示 Java 21。

### 4.2 設定環境變數

系統需要以下環境變數：

```text
JWT_SECRET
DB_PASSWORD
```

`JWT_SECRET` 應使用足夠長度的安全字串，不應直接寫入 Git Repository。

在 IntelliJ IDEA 中，可以透過以下位置設定：

```text
Run
→ Edit Configurations
→ Environment variables
```

### 4.3 啟動 PostgreSQL

在專案根目錄執行：

```bash
docker compose up -d
```

確認容器狀態：

```bash
docker compose ps
```

### 4.4 啟動 Spring Boot

Windows：

```bash
mvnw.cmd spring-boot:run
```

Linux 或 macOS：

```bash
./mvnw spring-boot:run
```

也可以直接在 IntelliJ IDEA 執行：

```text
TradeApplication
```

啟動成功後，系統預設位址為：

```text
http://localhost:8080
```

Swagger UI：

```text
http://localhost:8080/swagger-ui/index.html
```

---

## 5. 測試方式

### 5.1 執行自動化測試

Windows：

```bash
mvnw.cmd test
```

Linux 或 macOS：

```bash
./mvnw test
```

### 5.2 使用 Swagger 測試

1. 啟動 PostgreSQL。
2. 啟動 Spring Boot。
3. 開啟 Swagger UI。
4. 呼叫登入 API 取得 JWT。
5. 點擊 Swagger 的 `Authorize`。
6. 貼上 JWT 並完成授權。
7. 測試商品、訂單及 Audit Log API。

### 5.3 Audit Log 測試流程

1. 使用 `POST /api/products` 新增商品。
2. 使用 `PUT /api/products/{id}` 修改商品。
3. 使用 `DELETE /api/products/{id}` 刪除商品。
4. 使用 `GET /api/audit-logs` 查詢全部日誌。
5. 使用 `operator`、`action`、`entityType`、`entityId` 測試單一條件。
6. 同時輸入多個條件，確認組合查詢結果。
7. 使用不存在的 `entityId`，確認系統正常回傳空陣列。

單一條件測試：

```http
GET /api/audit-logs?action=CREATE
```

```http
GET /api/audit-logs?operator=admin
```

```http
GET /api/audit-logs?entityType=PRODUCT
```

```http
GET /api/audit-logs?entityId=100
```

不存在的資料 ID：

```http
GET /api/audit-logs?entityId=999
```

預期回傳：

```json
[]
```

組合條件測試：

```http
GET /api/audit-logs?operator=admin&action=CREATE&entityType=PRODUCT&entityId=100
```

上述查詢已透過 Swagger 實際驗證，並成功回傳：

```text
200 OK
```

實際測試回傳的 Audit Log 範例：

```json
[
  {
    "id": 1,
    "operator": "admin",
    "action": "CREATE",
    "entityType": "PRODUCT",
    "entityId": 100,
    "beforeData": null,
    "afterData": "{\"id\":100,\"name\":\"Keyboard\",\"price\":1200,\"stock\":10}",
    "createdAt": "2026-09-06T21:47:31"
  }
]
```

---

## 6. 後續可改進項目

目前 Audit Log 已完成基本記錄及動態條件查詢，後續可以繼續加入：

- 依日期區間查詢。
- 查詢結果分頁。
- 依操作時間倒序排列。
- 限制只有 `ADMIN` 可以查詢 Audit Log。
- 使用 Audit Log Response DTO，避免直接回傳 Entity。
- 自動遮蔽密碼、JWT 及資料庫憑證等敏感資訊。
- 為 Audit Log 查詢功能增加自動化測試。

---

## 7. Git History

本專案使用 Git 進行版本控制，完整修改紀錄保留於 GitHub Repository 的 Commit History。

Audit Log 基本功能相關 Commit：

```text
b084c95 feat: implement audit logging for product operations
```

Audit Log 動態條件查詢相關 Commit：

```text
b4704cc feat: add dynamic audit log search
```