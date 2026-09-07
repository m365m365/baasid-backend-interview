# Baasid Backend Interview — README（v3.3）

## 1. 專案簡介

本專案為 Java Spring Boot 交易平台後端，提供以下功能：

* 使用者登入
* JWT 身分驗證
* 角色權限管理
* 商品管理
* 商品名稱搜尋
* 訂單建立
* 庫存扣減
* 操作日誌（Audit Log）

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

## 2. 主要修改內容

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
12. 使用 `@Version` 樂觀鎖，降低併發下單造成超賣的風險。
13. 使用 `BigDecimal` 計算商品價格與訂單總價。
14. 將 PostgreSQL 金額欄位改為 `NUMERIC(19,2)`。
15. 完成商品新增、修改及刪除的操作日誌。
16. 從 Spring Security `SecurityContext` 取得目前登入的操作者。
17. 記錄操作類型、資料類型、資料 ID，以及修改前後的 JSON 資料。
18. 使用 `JpaSpecificationExecutor` 完成 Audit Log 動態條件查詢。
19. Audit Log 支援依 `operator`、`action`、`entityType`、`entityId` 查詢。
20. Audit Log 查詢結果依 `createdAt` 倒序排列。
21. 使用獨立資料快照保存完整的 `beforeData`。
22. 使用 `saveAndFlush()` 確保 `afterData` 包含更新後的樂觀鎖版本。
23. 將商品名稱搜尋的 JPQL 字串拼接改為參數化查詢。
24. 商品名稱搜尋支援模糊比對及英文大小寫不敏感查詢。
25. 限制只有 `ADMIN` 可以查詢 Audit Log。
26. 新增 `AuditLogResponse` DTO，避免 Audit Log API 直接回傳 Entity。
27. 在 Service 層將 `List<AuditLog>` 轉換為 `List<AuditLogResponse>`。
28. 為商品搜尋的 `keyword` 加入 `@NotBlank` 與 `@Size(max = 100)` 輸入驗證。
29. 修正 Controller 使用 `@Validated` 時驗證失敗回傳 `500` 的問題，改由 Spring MVC 內建方法參數驗證回傳 `400 Bad Request`。
30. 將不安全的 `GET /api/products/delete/{id}` 改為 `DELETE /api/products/{id}`。
31. 確認商品刪除 API 正確套用 ADMIN 權限限制。
32. 完成 Swagger、JWT、角色權限、DTO、商品搜尋、訂單、庫存及 Audit Log 測試。
33. 保留相關 Git Commit 紀錄並推送至 GitHub。

---

## 3. 第三部分功能選擇與設計理由

### 3.1 選擇項目

本專案第三部分選擇：

**B：操作日誌（Audit Log）**

交易平台中的商品價格、庫存及商品資料變更，可能直接影響訂單與交易結果。

因此，系統需要保存操作紀錄，以便確認：

* 由誰執行操作。
* 執行了什麼操作。
* 操作發生的時間。
* 被操作的資料類型。
* 被操作的資料 ID。
* 修改前與修改後的資料。

### 3.2 已完成的 Audit Log 元件

本專案已建立：

* `AuditLog` Entity
* `AuditLogRepository`
* `AuditLogService`
* `AuditLogController`
* `AuditLogResponse` DTO
* PostgreSQL `audit_logs` 資料表

商品進行下列操作時，系統會自動寫入日誌：

* `CREATE`：新增商品
* `UPDATE`：修改商品
* `DELETE`：刪除商品

每筆 Audit Log 記錄以下欄位：

* `operator`
* `action`
* `entityType`
* `entityId`
* `beforeData`
* `afterData`
* `createdAt`

欄位說明：

* `operator`：目前登入的使用者名稱。
* `action`：操作類型，例如 `CREATE`、`UPDATE` 或 `DELETE`。
* `entityType`：被操作的資料類型，目前為 `PRODUCT`。
* `entityId`：被操作的商品 ID。
* `beforeData`：修改或刪除前的資料。
* `afterData`：新增或修改後的資料。
* `createdAt`：操作發生時間。

---

## 4. Audit Log 資料快照

修改商品時，系統會先使用獨立的 `Map` 保存修改前的完整資料。

這樣可以避免 Product Entity 更新後，連帶改變 `beforeData` 的內容。

修改前快照包含：

* 商品 ID
* 商品名稱
* 商品價格
* 商品庫存
* 樂觀鎖版本
* 商品建立時間

商品使用 `saveAndFlush()` 完成資料庫更新後，再建立 `afterData`。

這樣可以確保 Audit Log 記錄的是實際更新完成後的商品版本。

例如：

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

三者代表不同的數值：

* `Product ID`：商品的固定編號。
* `Product version`：商品每次更新時遞增的樂觀鎖版本。
* `AuditLog ID`：操作日誌的流水號。

---

## 5. Audit Log 查詢 API

Audit Log 查詢 API：

```http
GET /api/audit-logs
```

不帶條件時，查詢全部日誌：

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

組合條件查詢：

```http
GET /api/audit-logs?operator=admin&action=DELETE
```

```http
GET /api/audit-logs?entityType=PRODUCT&entityId=100
```

```http
GET /api/audit-logs?operator=admin&action=CREATE&entityType=PRODUCT&entityId=100
```

查詢功能使用：

```java
JpaSpecificationExecutor<AuditLog>
```

搭配：

```java
Specification<AuditLog>
```

動態建立查詢條件。

只有實際傳入的參數才會加入查詢，多個條件之間使用 `AND` 組合。

查詢結果依照 `createdAt` 倒序排列：

```java
Sort.by(Sort.Direction.DESC, "createdAt")
```

大致相當於：

```sql
ORDER BY created_at DESC
```

因此最新的操作日誌會顯示在回傳陣列最前面。

---

## 6. Audit Log 權限限制

Audit Log 可能包含敏感的商品異動紀錄，因此只允許 `ADMIN` 查詢。

`SecurityConfig` 設定如下：

```java
.requestMatchers(
        HttpMethod.GET,
        "/api/audit-logs"
).hasRole("ADMIN")
```

JWT 驗證成功後，`JwtAuthenticationFilter` 會讀取 Token 中的角色：

```java
String role = jwtUtil.getRole(token);
```

然後建立 Spring Security Authority：

```java
SimpleGrantedAuthority authority =
        new SimpleGrantedAuthority("ROLE_" + role);
```

如果 JWT 中的角色是：

```text
ADMIN
```

建立的權限就是：

```text
ROLE_ADMIN
```

這個權限可以通過：

```java
.hasRole("ADMIN")
```

一般使用者雖然已登入，但只有：

```text
ROLE_USER
```

因此查詢 Audit Log 時會得到：

```text
403 Forbidden
```

### 權限測試結果

| 使用者   | 角色    | 查詢結果            |
| ----- | ----- | --------------- |
| admin | ADMIN | `200 OK`        |
| alice | USER  | `403 Forbidden` |

實際測試結果：

```text
✅ ADMIN 可以查詢 Audit Log
✅ 一般 USER 無法查詢 Audit Log
✅ JWT 角色能正確轉換為 Spring Security Authority
✅ hasRole("ADMIN") 規則正常生效
```

---

## 7. AuditLogResponse DTO

### 7.1 為什麼需要 DTO？

原本 `AuditLogController` 直接回傳：

```java
List<AuditLog>
```

這代表 Controller 直接將 Entity 序列化後傳給前端。

直接回傳 Entity 可能造成以下問題：

* Entity 與 API Response 高度耦合。
* Entity 新增欄位後，可能意外暴露給前端。
* API 格式容易受到資料庫結構變更影響。
* 不容易控制哪些欄位可以公開。

因此建立專門的：

```text
AuditLogResponse
```

作為對外回傳格式。

### 7.2 AuditLogResponse.java

```java
package com.trading.platform.dto;

import com.trading.platform.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String operator,
        String action,
        String entityType,
        Long entityId,
        String beforeData,
        String afterData,
        LocalDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getOperator(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getBeforeData(),
                auditLog.getAfterData(),
                auditLog.getCreatedAt()
        );
    }
}
```

### 7.3 Entity 轉換成 DTO

Repository 查詢後取得：

```java
List<AuditLog>
```

Service 使用以下程式進行轉換：

```java
return auditLogRepository.findAll(
                specification,
                Sort.by(Sort.Direction.DESC, "createdAt")
        )
        .stream()
        .map(AuditLogResponse::from)
        .toList();
```

這段程式的執行流程如下：

```text
List<AuditLog>
      ↓
stream()
      ↓
map(AuditLogResponse::from)
      ↓
toList()
      ↓
List<AuditLogResponse>
```

各方法的作用：

* `.stream()`：把查詢結果轉換成 Stream，準備逐筆處理。
* `.map()`：將每個 `AuditLog` 轉換成 `AuditLogResponse`。
* `AuditLogResponse::from`：呼叫 DTO 的靜態轉換方法。
* `.toList()`：把轉換完成的 DTO 收集成 List。

以下兩種寫法的意思相同：

```java
.map(AuditLogResponse::from)
```

```java
.map(auditLog -> AuditLogResponse.from(auditLog))
```

第一種稱為 Method Reference，寫法較簡潔。

### 7.4 Controller 回傳 DTO

修改前：

```java
@GetMapping
public List<AuditLog> search(...) {
    return auditLogService.search(...);
}
```

修改後：

```java
@GetMapping
public List<AuditLogResponse> search(...) {
    return auditLogService.search(...);
}
```

因為 Controller 使用：

```java
@RestController
```

Spring Boot 會自動將 `List<AuditLogResponse>` 轉換成 JSON，放入 HTTP Response Body 回傳給前端。

完整流程：

```text
前端或 Swagger 發出 GET Request
                ↓
AuditLogController.search()
                ↓
AuditLogService.search()
                ↓
AuditLogRepository 查詢資料庫
                ↓
取得 List<AuditLog>
                ↓
轉換成 List<AuditLogResponse>
                ↓
Controller 回傳 DTO
                ↓
Spring Boot 轉換成 JSON
                ↓
前端收到 HTTP Response
```

### 7.5 DTO 的好處

使用 DTO 可以：

1. 隔離 Entity 與 API Response。
2. 控制允許回傳給前端的欄位。
3. 避免 Entity 新增欄位後自動暴露。
4. 降低 API 與資料庫結構的耦合。
5. 讓 API 格式更容易維護。
6. 讓 Swagger Schema 更清楚。

---

## 8. 商品名稱搜尋安全修正

### 8.1 原本的問題

原本的商品名稱搜尋直接將使用者輸入拼接到 JPQL：

```java
String jpql =
        "SELECT p FROM Product p WHERE p.name LIKE '%"
        + keyword
        + "%'";
```

這種方式會讓使用者輸入直接成為 JPQL 字串的一部分，可能造成 JPQL Injection 風險。

### 8.2 修正方式

修改後使用 JPQL 命名參數：

```java
public List<Product> searchByName(String keyword) {
    String jpql = """
            SELECT p
            FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(:keyword)
            """;

    return entityManager
            .createQuery(jpql, Product.class)
            .setParameter(
                    "keyword",
                    "%" + keyword.trim() + "%"
            )
            .getResultList();
}
```

JPQL 查詢：

```sql
SELECT p
FROM Product p
WHERE LOWER(p.name) LIKE LOWER(:keyword)
```

實際搜尋值透過以下方法傳入：

```java
setParameter("keyword", value)
```

例如使用者輸入：

```text
phone
```

實際參數值為：

```text
%phone%
```

JPQL 結構與使用者輸入值會分開處理：

```text
JPQL：
SELECT p
FROM Product p
WHERE LOWER(p.name) LIKE LOWER(:keyword)

參數：
keyword = "%phone%"
```

`:keyword` 是命名參數的位置。

使用者輸入只會被當成參數值，不會直接成為 JPQL 語法的一部分。

### 8.3 搜尋行為

查詢使用：

```sql
LOWER(p.name) LIKE LOWER(:keyword)
```

具有以下效果：

* `LIKE` 搭配前後 `%`，支援模糊搜尋。
* `LOWER()` 讓英文搜尋不區分大小寫。
* 命名參數降低 JPQL Injection 風險。

搜尋：

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

### 8.4 特殊輸入測試

測試關鍵字：

```text
' OR '1'='1
```

執行：

```http
GET /api/products/search?keyword=' OR '1'='1
```

實際結果：

```text
200 OK
```

回傳：

```json
[]
```

測試結果：

```text
✅ 特殊輸入沒有被解讀為 JPQL 指令
✅ 系統沒有回傳全部商品
✅ 系統沒有發生 JPQL 語法錯誤
✅ 特殊輸入只被當成搜尋值
✅ 查無符合商品時正常回傳空陣列
```

### 8.5 商品搜尋輸入驗證

商品搜尋的 `keyword` 加入以下驗證：

```java
@GetMapping("/search")
public List<Product> search(
        @RequestParam
        @NotBlank(message = "keyword must not be blank")
        @Size(
                max = 100,
                message = "keyword must not exceed 100 characters"
        )
        String keyword
) {

    return productService.searchByName(keyword);
}
```

驗證規則：

* `keyword` 必須存在。
* `keyword` 不得為空字串。
* `keyword` 不得只包含空白。
* `keyword` 最長為 100 個字元。

最初在 `ProductController` 加入類別層級的：

```java
@Validated
```

驗證雖然成功攔截空白字串，但因為 Spring AOP 拋出的：

```text
ConstraintViolationException
```

沒有對應的錯誤處理，因此 API 回傳：

```text
500 Internal Server Error
```

輸入驗證失敗是 Client 端傳入不合法資料，不應被視為伺服器內部錯誤。

移除 Controller 的 `@Validated` 後，改由 Spring MVC 內建的方法參數驗證處理。

空白輸入會正確回傳：

```text
400 Bad Request
```

實際測試結果：

```text
✅ 正常 keyword 可以成功搜尋
✅ 空白 keyword 回傳 400 Bad Request
✅ 超過 100 字元會被 Swagger 驗證攔截
✅ 驗證失敗不再回傳 500 Internal Server Error
```

### 8.6 商品刪除 API 安全修正

原本刪除商品使用：

```java
@GetMapping("/delete/{id}")
```

實際 API 為：

```http
GET /api/products/delete/{id}
```

但 `SecurityConfig` 限制的是 HTTP DELETE：

```java
.requestMatchers(
        HttpMethod.DELETE,
        "/api/products/**"
).hasRole("ADMIN")
```

由於 Controller 使用 GET，而 `SecurityConfig` 檢查 DELETE，兩者沒有對上。

一般登入使用者的請求可能落入：

```java
.anyRequest().authenticated()
```

造成非 ADMIN 使用者也能刪除商品的權限漏洞。

另外，GET 原則上只應用於查詢資料，不應修改或刪除資料。

修正後改為：

```java
@DeleteMapping("/{id}")
```

實際 API 為：

```http
DELETE /api/products/{id}
```

修正後 Controller 與 `SecurityConfig` 使用相同的 HTTP Method，只有具備 `ROLE_ADMIN` 的使用者可以刪除商品。

使用測試商品進行驗證：

```text
商品 ID：145
商品名稱：Delete Permission Test
```

一般使用者 `alice` 執行：

```http
DELETE /api/products/145
```

結果：

```text
403 Forbidden
```

管理員 `admin` 執行相同 API，結果：

```text
200 OK
deleted
```

接著查詢：

```http
GET /api/audit-logs?action=DELETE&entityId=145
```

成功取得 DELETE Audit Log：

```json
{
  "id": 12,
  "operator": "admin",
  "action": "DELETE",
  "entityType": "PRODUCT",
  "entityId": 145,
  "beforeData": "{\"id\":145,\"name\":\"Delete Permission Test\",\"price\":100.00,\"stock\":1,\"version\":0}",
  "afterData": null
}
```

測試結果：

```text
✅ 原本不安全的 GET 刪除 API 已移除
✅ DELETE API 正確受到 ADMIN 權限保護
✅ 一般 USER 無法刪除商品
✅ ADMIN 可以刪除商品
✅ 刪除前資料完整寫入 beforeData
✅ 刪除後資料正確為 null
✅ DELETE Audit Log 正常產生
```

---

## 9. 系統啟動方式

### 9.1 環境需求

啟動前請先安裝：

* JDK 21
* Docker Desktop
* Git

確認 Java 版本：

```bash
java -version
```

應顯示 Java 21。

### 9.2 設定環境變數

系統需要以下環境變數：

```text
JWT_SECRET
DB_PASSWORD
```

`JWT_SECRET` 應使用足夠長度的安全字串，不應直接寫入 Git Repository。

在 IntelliJ IDEA 中可透過以下位置設定：

```text
Run
→ Edit Configurations
→ Environment variables
```

### 9.3 啟動 PostgreSQL

在專案根目錄執行：

```bash
docker compose up -d
```

確認容器狀態：

```bash
docker compose ps
```

### 9.4 啟動 Spring Boot

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

啟動成功後，系統預設位址：

```text
http://localhost:8080
```

Swagger UI：

```text
http://localhost:8080/swagger-ui/index.html
```

---

## 10. 測試方式

### 10.1 執行自動化測試

Windows：

```bash
mvnw.cmd test
```

Linux 或 macOS：

```bash
./mvnw test
```

### 10.2 Swagger 測試流程

1. 啟動 PostgreSQL。
2. 啟動 Spring Boot。
3. 開啟 Swagger UI。
4. 呼叫登入 API 取得 JWT。
5. 點擊 Swagger 的 `Authorize`。
6. 貼上 JWT 並完成授權。
7. 確認 Swagger 產生 `Authorization` Header。
8. 測試商品、商品搜尋、訂單及 Audit Log API。

JWT Header 類似：

```http
Authorization: Bearer eyJ...
```

公開分享 Swagger 截圖時，應遮蔽：

* JWT
* 使用者密碼
* 資料庫密碼
* JWT Secret
* 其他存取憑證

### 10.3 Audit Log 功能測試

測試流程：

1. 使用 `POST /api/products` 新增商品。
2. 使用 `PUT /api/products/{id}` 修改商品。
3. 使用 `DELETE /api/products/{id}` 刪除商品。
4. 使用 `GET /api/audit-logs` 查詢全部日誌。
5. 測試 `operator` 條件。
6. 測試 `action` 條件。
7. 測試 `entityType` 條件。
8. 測試 `entityId` 條件。
9. 測試多個條件組合。
10. 使用不存在的 `entityId`，確認回傳空陣列。
11. 確認最新日誌位於最前面。
12. 確認 `beforeData` 與 `afterData` 正確。
13. 使用 ADMIN 查詢，確認回傳 `200 OK`。
14. 使用一般 USER 查詢，確認回傳 `403 Forbidden`。
15. 確認 API 回傳的是 `AuditLogResponse` DTO。

### 10.4 Audit Log 測試結果

```text
✅ CREATE 日誌正常
✅ UPDATE 日誌正常
✅ DELETE 日誌正常
✅ operator 查詢正常
✅ action 查詢正常
✅ entityType 查詢正常
✅ entityId 查詢正常
✅ 多條件 AND 查詢正常
✅ createdAt 倒序排列正常
✅ beforeData 快照正常
✅ afterData 快照正常
✅ 樂觀鎖版本遞增正常
✅ ADMIN 查詢回傳 200 OK
✅ 一般 USER 查詢回傳 403 Forbidden
✅ AuditLogResponse DTO 正常回傳
```

實際回傳範例：

```json
[
  {
    "id": 10,
    "operator": "admin",
    "action": "CREATE",
    "entityType": "PRODUCT",
    "entityId": 129,
    "beforeData": null,
    "afterData": "{\"id\":129,\"name\":\"Phone Case\",\"price\":500,\"stock\":20,\"version\":0}",
    "createdAt": "2026-09-07T02:59:17.387028"
  }
]
```

### 10.5 商品搜尋與刪除權限測試結果

```text
✅ 正常商品名稱搜尋回傳 200 OK
✅ 空白 keyword 回傳 400 Bad Request
✅ 超過 100 字元的 keyword 被拒絕
✅ 一般 USER 刪除商品回傳 403 Forbidden
✅ ADMIN 刪除商品回傳 200 OK
✅ 商品刪除後成功產生 DELETE Audit Log
```

---

## 11. 後續可改進項目

Audit Log 後續可改進：

* 加入日期區間查詢。
* 加入查詢結果分頁。
* 將 `beforeData` 與 `afterData` 改為結構化 JSON Response。
* 自動遮蔽敏感資訊。
* 增加 Audit Log 自動化測試。

商品搜尋後續可改進：

* 明確定義 `%` 和 `_` 等萬用字元的處理方式。
* 加入分頁及排序。
* 為大量商品建立適當索引。
* 使用 `EXPLAIN ANALYZE` 檢查查詢執行計畫。
* 增加商品搜尋自動化測試。

---

## 12. Git History

本專案使用 Git 進行版本控制，完整修改紀錄保留於 GitHub Repository 的 Commit History。

Audit Log 基本功能：

```text
b084c95 feat: implement audit logging for product operations
```

Audit Log 動態條件查詢：

```text
b4704cc feat: add dynamic audit log search
```

README v2 更新：

```text
d933b2d docs: update README to v2
```

Audit Log `entityId` 查詢：

```text
55b1e7c feat: add entity ID filter to audit log search
```

Audit Log 管理員權限限制：

```text
86710b7 fix: restrict audit log access to admin
```

Audit Log Response DTO：

```text
c67f426 refactor: return audit log response DTO
```

商品名稱 JPQL 參數化查詢：

```text
fix: parameterize product name JPQL query
```

商品搜尋輸入驗證與刪除 API 權限修正：

```text
fix: validate product search and secure delete endpoint
```

尚未填入的 Commit ID，可在完成提交後執行：

```bash
git log --oneline
```

再將實際 Commit ID 補到 Commit 訊息前方。

---

## 13. 第四部分：AI 使用說明

### A. 這份作業有哪些部分借助了 AI？

本次作業在程式碼閱讀、問題分析、Bug 排查、修正建議、測試流程及文件整理等部分使用了 AI 協助。

主要包含：

* 閱讀及整理原始專案結構。
* 找出編譯錯誤及啟動失敗原因。
* 分析 Spring Security 與 JWT 設定。
* 檢查 Entity、Repository、DTO 與 Service 之間的不一致。
* 分析 Transaction、自我呼叫及庫存扣減問題。
* 建議使用 BCrypt、環境變數、BigDecimal 及樂觀鎖。
* 協助設計與實作 Audit Log。
* 協助將 JPQL 字串拼接改為參數化查詢。
* 協助建立 Audit Log Response DTO。
* 協助加入商品搜尋輸入驗證。
* 協助發現並修正商品刪除 API 的權限漏洞。
* 規劃 Swagger 測試流程。
* 整理 Code Review 與 README。

AI 是本次專案的重要輔助工具，但程式碼仍由我實際修改、執行及測試。

### B. 如何驗證 AI 的產出？

我不會只根據 AI 的文字說明判斷修改是否正確，而是透過實際執行結果進行驗證。

驗證方式包括：

1. 重新編譯及啟動 Spring Boot。
2. 觀察 Console 是否出現編譯、Bean 注入或資料庫錯誤。
3. 使用 Swagger 實際登入並取得 JWT。
4. 使用不同角色測試 API 權限。
5. 實際新增、修改及刪除商品。
6. 確認 Audit Log 正確產生。
7. 比較 `beforeData` 與 `afterData`。
8. 確認 Product 樂觀鎖版本正確遞增。
9. 使用正常關鍵字、大寫關鍵字及特殊輸入測試搜尋。
10. 使用空白與超長關鍵字測試輸入驗證。
11. 檢查 PostgreSQL 欄位型別及資料內容。
12. 檢查 Swagger Response Schema。
13. 使用 Git Commit 保存每個階段的修改。

例如，在 Audit Log 權限測試中：

* ADMIN 查詢回傳 `200 OK`。
* 一般 USER 查詢回傳 `403 Forbidden`。

在 DTO 測試中：

* Spring Boot 正常啟動。
* Hibernate 正常查詢 `audit_logs`。
* Controller 成功回傳 `List<AuditLogResponse>`。
* Swagger Response Body 正常顯示所有預期欄位。

在商品刪除權限測試中：

* 一般 USER 刪除商品回傳 `403 Forbidden`。
* ADMIN 刪除商品回傳 `200 OK`。
* 刪除成功後正確產生 DELETE Audit Log。

這表示修改不只停留在程式碼層面，也通過了實際執行與 API 測試。

### C. 有沒有否決或調整 AI 的建議？

本次作業沒有完全否決 AI 提出的主要修改方向，但我並非直接接受所有內容，而是依照專案實際狀況逐項確認及調整。

例如：

* 根據實際 Entity 欄位調整 Repository 方法。
* 根據 JWT 中的角色格式確認 `ROLE_` 前綴。
* 根據實際 API 路徑設定 Spring Security 規則。
* 根據 Swagger 回傳結果確認 ADMIN 與 USER 的權限差異。
* 根據資料庫實際狀況調整 PostgreSQL 欄位。
* 根據目前專案架構決定 Audit Log 的實作方式。
* 根據 Entity 欄位建立 `AuditLogResponse` DTO。
* 根據實際測試結果確認 DTO 沒有改變 API 原有欄位。
* 發現 `@Validated` 造成輸入錯誤回傳 `500` 後，改用 Spring MVC 內建方法參數驗證。
* 發現 Controller 的 GET 刪除方法沒有符合 SecurityConfig 的 DELETE 權限規則後，改成正確的 `@DeleteMapping`。

我先前曾完成 TopFoodAI 網站專案：

```text
https://www.topfoodai.com/
```

因此對 Spring Boot、資料庫、AWS 部署及 API 測試已有實作經驗。

這些經驗可以協助我判斷 AI 建議是否符合基本設計原則。

最終仍以實際執行結果、測試結果及 Git 修改紀錄作為判斷依據，而不是直接將 AI 的回答視為一定正確。
