# Baasid Backend Interview — README（v3.0）

## 1. 專案簡介

本專案為 Java Spring Boot 交易平台後端，提供使用者登入、JWT 身分驗證、商品管理、商品名稱搜尋、訂單建立、庫存扣減及操作日誌等功能。

### 主要技術

* Java 21
* Spring Boot 4
* Spring Security
* JWT
* Spring Data JPA
* PostgreSQL
* Swagger / OpenAPI
* Maven
* Docker Compose

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
20. Audit Log 查詢結果依 `createdAt` 倒序排列，最新操作優先顯示。
21. 使用獨立資料快照保存完整的 `beforeData`。
22. 使用 `saveAndFlush()` 確保 `afterData` 記錄更新完成後的樂觀鎖版本。
23. 將商品名稱搜尋的 JPQL 字串拼接改為參數化查詢，降低 JPQL Injection 風險。
24. 商品名稱搜尋支援模糊比對及英文大小寫不敏感查詢。
25. 完成 Swagger、JWT、商品搜尋、訂單、庫存、金額及 Audit Log 的實際測試。
26. 保留相關 Git Commit 紀錄，並將修改 Push 至 GitHub。

---

## 3. 第三部分功能選擇與設計理由

### 3.1 選擇項目

本專案第三部分選擇：

**B：操作日誌（Audit Log）**

交易平台中的商品價格、庫存及商品資料變更，可能直接影響訂單與交易結果。因此，系統需要保存操作紀錄，方便確認：

* 由誰執行操作。
* 執行了什麼操作。
* 操作發生的時間。
* 被操作的資料類型與資料 ID。
* 修改前與修改後的資料。

### 3.2 已完成內容

已建立以下 Audit Log 元件：

* `AuditLog` Entity
* `AuditLogRepository`
* `AuditLogService`
* `AuditLogController`
* PostgreSQL `audit_logs` 資料表

商品進行下列操作時，系統會自動寫入日誌：

* `CREATE`：新增商品
* `UPDATE`：修改商品
* `DELETE`：刪除商品

每筆日誌記錄以下欄位：

* `operator`
* `action`
* `entityType`
* `entityId`
* `beforeData`
* `afterData`
* `createdAt`

欄位說明：

* `operator`：由 Spring Security 的 `SecurityContext` 取得目前登入的使用者。
* `action`：記錄 `CREATE`、`UPDATE` 或 `DELETE`。
* `entityType`：記錄被操作的資料類型，目前為 `PRODUCT`。
* `entityId`：記錄被操作的商品 ID。
* `beforeData`：保存修改或刪除前的資料。
* `afterData`：保存新增或修改後的資料。
* `createdAt`：記錄操作發生時間。

修改前後的資料會轉換成 JSON 字串保存，方便追蹤完整的資料變更內容。

更新商品時，系統會先使用獨立的 `Map` 保存修改前的完整資料快照，避免商品 Entity 更新後連帶改變 `beforeData`。

修改前快照包含：

* 商品 ID
* 商品名稱
* 商品價格
* 商品庫存
* 樂觀鎖版本
* 商品建立時間

商品使用 `saveAndFlush()` 完成資料庫更新及樂觀鎖版本遞增後，再寫入操作日誌，以確保 `afterData` 保存的是實際更新完成後的資料。

例如，商品更新前後的版本應為：

```text
beforeData.version = 6
afterData.version  = 7
```

其中：

```text
Product ID      = 100
Product version = 7
AuditLog ID     = 8
```

`Product ID`、`Product version` 和 `AuditLog ID` 是三種不同的數值：

* `Product ID`：商品的固定編號。
* `Product version`：商品每次更新時遞增的樂觀鎖版本。
* `AuditLog ID`：每一筆操作日誌的流水號。

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

查詢結果會依照 `createdAt` 倒序排列，讓最新的操作日誌顯示在回傳陣列最前面：

```java
Sort.by(Sort.Direction.DESC, "createdAt")
```

大致相當於：

```sql
ORDER BY created_at DESC
```

Java 程式使用的是 `AuditLog` Entity 的屬性名稱：

```java
"createdAt"
```

資料庫實際使用的欄位名稱則是：

```sql
created_at
```

兩者由 JPA Entity 的欄位對應負責轉換。

### 3.4 設計理由

選擇 Audit Log 的主要原因如下：

1. 提升商品資料異動的可追蹤性。
2. 發生資料異常時，可以還原操作過程。
3. 明確記錄操作者，強化責任歸屬。
4. 方便調查安全事件或人為操作錯誤。
5. 保留商品修改前後的資料，方便比對差異。
6. 使用 `Specification` 實作動態查詢，未來容易繼續加入日期區間及分頁等條件。
7. 查詢結果依操作時間倒序排列，方便優先查看最新操作。
8. 使用獨立快照保存修改前資料，避免 Entity 更新造成歷史資料失真。
9. 使用 `saveAndFlush()` 確保 Audit Log 保存正確的更新後版本。

---

## 4. 商品名稱搜尋安全修正

### 4.1 問題說明

原本的商品名稱搜尋直接將使用者輸入拼接到 JPQL 字串中。

概念上類似：

```java
String jpql =
        "SELECT p FROM Product p WHERE p.name LIKE '%"
        + keyword
        + "%'";
```

這種方式會讓使用者輸入直接成為 JPQL 字串的一部分，可能造成 JPQL Injection 風險。

### 4.2 修正方式

目前已改為 JPQL 命名參數：

```java
public List<Product> searchByName(String keyword) {
    String jpql = """
            SELECT p
            FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(:keyword)
            """;

    return entityManager
            .createQuery(jpql, Product.class)
            .setParameter("keyword", "%" + keyword.trim() + "%")
            .getResultList();
}
```

查詢結構：

```sql
SELECT p
FROM Product p
WHERE LOWER(p.name) LIKE LOWER(:keyword)
```

實際搜尋值則透過以下方法傳入：

```java
setParameter("keyword", value)
```

例如，當使用者輸入：

```text
phone
```

實際參數值為：

```text
%phone%
```

JPQL 查詢結構與使用者輸入值會分開處理：

```text
JPQL：
SELECT p
FROM Product p
WHERE LOWER(p.name) LIKE LOWER(:keyword)

參數：
keyword = "%phone%"
```

`:keyword` 是命名參數的預留位置。使用者輸入只會被當作參數值處理，不會直接成為 JPQL 語法的一部分。

### 4.3 搜尋行為

查詢使用：

```sql
LOWER(p.name) LIKE LOWER(:keyword)
```

具有以下效果：

* `LIKE` 搭配前後 `%`，支援商品名稱模糊搜尋。
* `LOWER()` 讓英文搜尋不區分大小寫。
* 命名參數降低使用者輸入改變 JPQL 結構的風險。

例如，搜尋：

```text
phone
```

可以找到：

```text
iPhone 16
Phone Case
```

搜尋：

```text
PHONE
```

也可以找到相同商品。

---

## 5. 系統啟動方式

### 5.1 環境需求

啟動前請先安裝：

* JDK 21
* Docker Desktop
* Git

確認 Java 版本：

```bash
java -version
```

應顯示 Java 21。

### 5.2 設定環境變數

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

### 5.3 啟動 PostgreSQL

在專案根目錄執行：

```bash
docker compose up -d
```

確認容器狀態：

```bash
docker compose ps
```

### 5.4 啟動 Spring Boot

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

## 6. 測試方式

### 6.1 執行自動化測試

Windows：

```bash
mvnw.cmd test
```

Linux 或 macOS：

```bash
./mvnw test
```

### 6.2 使用 Swagger 測試

1. 啟動 PostgreSQL。
2. 啟動 Spring Boot。
3. 開啟 Swagger UI。
4. 呼叫登入 API 取得 JWT。
5. 點擊 Swagger 的 `Authorize`。
6. 貼上 JWT 字串並完成授權。
7. 確認 Swagger 產生 `Authorization: Bearer ...` Header。
8. 測試商品、商品搜尋、訂單及 Audit Log API。

Swagger 的 JWT 應產生類似以下 Header：

```http
Authorization: Bearer eyJ...
```

測試或公開分享 Swagger 截圖時，應遮蔽完整 JWT，避免洩漏仍在有效期限內的存取憑證。

### 6.3 Audit Log 測試流程

1. 使用 `POST /api/products` 新增商品。
2. 使用 `PUT /api/products/{id}` 修改商品。
3. 使用 `DELETE /api/products/{id}` 刪除商品。
4. 使用 `GET /api/audit-logs` 查詢全部日誌。
5. 使用 `operator`、`action`、`entityType`、`entityId` 測試單一條件。
6. 同時輸入多個條件，確認組合查詢結果。
7. 使用不存在的 `entityId`，確認系統正常回傳空陣列。
8. 連續修改同一商品，確認最新日誌位於回傳陣列最前面。
9. 確認 `beforeData` 與 `afterData` 的商品版本號正確遞增。

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

### 6.4 Audit Log 測試結果

實際測試已確認：

* 最新操作日誌位於回傳陣列最前面。
* `beforeData` 完整保存商品 ID、名稱、價格、庫存、版本及建立時間。
* `afterData` 保存更新完成後的商品資料。
* 商品樂觀鎖版本由修改前的 `6` 正確增加為修改後的 `7`。
* 不存在的 `entityId` 會正常回傳空陣列。
* 多個條件能以 `AND` 正確組合查詢。

實際測試回傳的 Audit Log 範例：

```json
[
  {
    "id": 8,
    "operator": "admin",
    "action": "UPDATE",
    "entityType": "PRODUCT",
    "entityId": 100,
    "beforeData": "{\"id\":100,\"name\":\"Gaming Keyboard Pro 4\",\"price\":1900.00,\"stock\":2,\"version\":6,\"createdAt\":\"2026-09-06T21:47:31.422899\"}",
    "afterData": "{\"id\":100,\"name\":\"Gaming Keyboard Pro 5\",\"price\":2000,\"stock\":1,\"version\":7,\"createdAt\":\"2026-09-06T21:47:31.422899\"}",
    "createdAt": "2026-09-07T01:06:14.330197"
  }
]
```

### 6.5 商品名稱搜尋測試流程

首先建立以下兩筆測試商品。

第一筆商品：

```json
{
  "name": "iPhone 16",
  "price": 30000,
  "stock": 10
}
```

建立成功後回傳：

```json
{
  "createdAt": "2026-09-07T02:57:21.3819404",
  "id": 128,
  "name": "iPhone 16",
  "price": 30000,
  "stock": 10,
  "version": 0
}
```

第二筆商品：

```json
{
  "name": "Phone Case",
  "price": 500,
  "stock": 20
}
```

建立成功後回傳：

```json
{
  "createdAt": "2026-09-07T02:59:17.3830003",
  "id": 129,
  "name": "Phone Case",
  "price": 500,
  "stock": 20,
  "version": 0
}
```

使用小寫關鍵字進行搜尋：

```http
GET /api/products/search?keyword=phone
```

API 成功回傳：

```http
200 OK
```

搜尋結果包含：

```text
iPhone 16
Phone Case
```

這表示 `%phone%` 模糊搜尋可以找到名稱中包含 `phone` 的商品。

接著使用大寫關鍵字測試：

```http
GET /api/products/search?keyword=PHONE
```

API 同樣成功回傳：

```http
200 OK
```

搜尋結果仍然包含：

```text
iPhone 16
Phone Case
```

這證明：

```sql
LOWER(p.name) LIKE LOWER(:keyword)
```

可以讓英文商品名稱搜尋不區分大小寫。

### 6.6 JPQL 特殊輸入測試

使用以下特殊內容作為搜尋關鍵字：

```text
' OR '1'='1
```

執行：

```http
GET /api/products/search?keyword=' OR '1'='1
```

API 正常回傳：

```http
200 OK
```

回傳內容：

```json
[]
```

測試結果顯示：

* 系統沒有將特殊輸入解讀為 JPQL 指令。
* 系統沒有回傳全部商品。
* 系統沒有發生 JPQL 語法錯誤。
* 特殊輸入只被當作商品名稱搜尋值處理。
* 查無符合商品時正常回傳空陣列。

商品名稱搜尋實際測試結果：

```text
✅ 商品名稱模糊搜尋正常
✅ 英文大小寫不敏感查詢正常
✅ JPQL 命名參數正常套用
✅ 特殊輸入不會改變 JPQL 查詢結構
✅ 查無符合商品時正常回傳空陣列
```

---

## 7. 後續可改進項目

目前 Audit Log 已完成基本記錄、動態條件查詢、Entity ID 查詢及時間倒序排列，後續可以繼續加入：

* 依日期區間查詢。
* 查詢結果分頁。
* 限制只有 `ADMIN` 可以查詢 Audit Log。
* 使用 Audit Log Response DTO，避免直接回傳 Entity。
* 自動遮蔽密碼、JWT 及資料庫憑證等敏感資訊。
* 為 Audit Log 查詢功能增加自動化測試。

商品搜尋後續可以繼續改進：

* 驗證 `keyword` 不得為 `null` 或空字串。
* 限制搜尋關鍵字的最大長度。
* 明確定義 `%` 與 `_` 等萬用字元的處理方式。
* 為商品搜尋加入分頁及排序。
* 為大量商品資料建立適當索引並檢查查詢執行計畫。
* 為商品名稱搜尋增加自動化測試。

---

## 8. Git History

本專案使用 Git 進行版本控制，完整修改紀錄保留於 GitHub Repository 的 Commit History。

Audit Log 基本功能相關 Commit：

```text
b084c95 feat: implement audit logging for product operations
```

Audit Log 動態條件查詢相關 Commit：

```text
b4704cc feat: add dynamic audit log search
```

README v2 更新相關 Commit：

```text
d933b2d docs: update README to v2
```

Audit Log `entityId` 查詢相關 Commit：

```text
55b1e7c feat: add entity ID filter to audit log search
```

商品名稱 JPQL 參數化查詢相關 Commit：

```text
fix: parameterize product name JPQL query
```

## 9. 第四部分：AI 使用說明:
## - A.這份作業你哪些部分借助了 AI？（讀程式碼、找 bug、寫修正、寫測試……）
## 回答:
## 95%借助了AI,它無疑是最大的幫手.

## -B. **你如何驗證 AI 的產出？** 例如：AI 有沒有「自信地改錯」、有沒有引入新的 bug、你怎麼發現的？

## 回答:注意AI的想法是什麼,檢查結果是否合理,基本上現今的AI很少錯,比一年前的能力差別很大,大部分應該都可以信任.

## 有位理科教授說,科學理念是:
## 1.這件事是真的嗎?(驗證謠傳).
## 2.這件事是真的嗎?(驗證事實證據).
## 3.這件事是真的嗎?(絕對要再次懷疑,無真理永遠持久).
## 基本上都會小心求證

## - C.有沒有哪次你**否決**了 AI 的建議？為什麼？
## 回答:
## 沒有否決AI 的建議,因為之前有做過網站"https://www.topfoodai.com/"
## 有些經驗,這些小錯,大多是基本的設計原則,AI處理得很好.我製作的過程中,會遇到相同的疑問.