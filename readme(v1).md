# 簡短的 README（v1）更新

## 1. 主要變更

本次已完成的主要修正如下：

1. 修正 API 回傳資料暴露使用者密碼的問題。
2. 修正 Repository 與 Entity 不一致造成的啟動問題。
3. 修正 `JwtAuthenticationFilter` 的 Bean 注入問題。
4. 修正 JWT Method Signature 不一致造成的編譯問題。
5. 修正 DTO 與 Service Accessor 不一致造成的編譯問題。
6. 使用 BCrypt 加密並儲存使用者密碼。
7. 將 JWT Secret 改由環境變數提供。
8. 將資料庫密碼改由環境變數提供。
9. 使用 `OrderResponse` DTO 回傳訂單資料，避免直接暴露 Entity。
10. 使用 Bean Validation 驗證商品 ID 與訂購數量。
11. 將訂單建立與庫存扣減放在同一個 Transaction 中。
12. 使用 `@Version` 樂觀鎖，防止併發交易造成庫存遺失更新。
13. 使用 `BigDecimal` 計算商品價格與訂單總價。
14. 將 PostgreSQL 金額欄位改為 `NUMERIC(19,2)`。
15. 完成 Swagger、JWT、訂單、庫存及金額功能的實際測試。
16. 完成相關 Git Commit，並 Push 至 GitHub。

    ## Git History

本專案使用 Git 進行版本控制，完整的修改紀錄已保留於 GitHub Repository 
的 Commit History 中。

## 2. 第三部分功能選擇與設計理由

### 2.1 選擇項目

選擇 **B：操作日誌（Audit Log）**。

本專案屬於交易平台，商品價格、庫存及狀態的變更可能直接影響交易結果，因此系統必須保留完整的操作紀錄，以便追查：

* 由誰執行操作。
* 操作發生的時間。
* 被修改的商品。
* 被修改的欄位。
* 修改前與修改後的資料。

### 2.2 預計實作內容

1. 建立 `AuditLog` Entity、Repository、Service 與資料表。

2. 在商品新增、修改及刪除時寫入操作日誌。

3. 從 Spring Security 的 `SecurityContext` 取得目前登入的操作者。

4. 記錄以下資料：

    * `operator`
    * `action`
    * `entityType`
    * `entityId`
    * `beforeData`
    * `afterData`
    * `createdAt`

5. 提供 Audit Log 查詢 API：

   ```http
   GET /api/audit-logs
   GET /api/audit-logs/{id}
   GET /api/audit-logs?operator=admin
   GET /api/audit-logs?action=UPDATE
   GET /api/audit-logs?entityId=10
   ```

6. 查詢結果支援分頁，並依操作時間倒序排列。

7. 限制只有具備 `ADMIN` 權限的使用者可以查詢操作日誌。

8. 避免將密碼、JWT、資料庫憑證等敏感資訊寫入日誌。

### 2.3 設計理由

Audit Log 可以完整記錄商品新增、修改及刪除操作，包含操作者、操作時間，以及變更前後的資料。

此功能能提升系統的可追蹤性與責任歸屬能力，並在發生資料異常、安全事件或操作錯誤時，提供後續調查所需的依據。
