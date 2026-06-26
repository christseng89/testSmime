# S/MIME Email Test Suite

驗證「以 S/MIME 數位簽章(及加密)寄送郵件」的 Java 測試工具。  
技術堆疊:**javax.mail + BouncyCastle 1.70**(對應專案 `pom.xml`),JDK 21、Maven、Git Bash。

提供三支測試腳本,由淺到深。**三支皆可獨立執行,不用管先後順序**——keystore 不存在時會自動用 `keytool` 產生:

| 腳本 | 環境 | 是否真寄出 | 用途 |
|------|------|-----------|------|
| `bash test-greenmail.sh` | 本機記憶體 SMTP(GreenMail) | 否 | 離線驗證簽章流程,CI / 開發首選 |
| `bash test-gmail.sh` | Gmail SMTP | 是 | 用 Gmail 真寄,驗證實際投遞 |
| `bash test-bank.sh` | 銀行 SMTP 閘道 | 是 | 簽章(+ 加密)寄到銀行,正式整合 |

> `gen-test-certs.sh` 是**選用**的進階工具(產生完整 CA 鏈憑證,見第 5b 節)。它非必要;若要用,請在三支測試腳本**之前**執行,因為它會覆寫 `smime-test-certs/`。

---

## 1. 前置需求

- JDK 21(內含 `keytool`)、Maven 3.9+、Git Bash(Windows)
- 首次執行任一腳本會自動把相依套件抓到 `./lib/`(`mvn dependency:copy-dependencies`)
- 憑證由腳本用 `keytool` 自動產生,無需手動安裝 openssl

---

## 2. `test-greenmail.sh` — 離線驗證(不寄出)

```
bash test-greenmail.sh
```

它會:自動產生自簽簽章憑證 `smime-test-certs/our-signing.p12` → 在 `127.0.0.1:3025` 起一個記憶體 SMTP → 簽章寄出 → 收回 → **用 BouncyCastle 實際驗章**。

成功會印:

```
GreenMail received 1 message(s).
SIGNATURE VERIFIED — offline round-trip succeeded.
```

整個過程不連網路、不需任何帳號。這是確認「簽章程式正確」最快的方式。

---

## 3. `test-gmail.sh` — 用 Gmail 真寄

需要 Gmail 帳號開啟**兩步驟驗證**並建立 **App Password**(一般密碼不能用於 SMTP)。簽章 keystore 若不存在會自動產生,所以不必先跑 `test-greenmail.sh`。

在同層建立 `.env`:

```dotenv
GMAIL_USER=samfire5200@gmail.com
GMAIL_APP_PASSWORD=oxar vder xknk wxyz   # 16 碼,可含空格(會自動去除)
```

執行:

```
bash test-gmail.sh
```

收件人預設為 `samfire5201@gmail.com,njpm@chinasystems.com`(可在 `.env` 用 `TO_EMAIL=` 覆寫,逗號分隔)。

> 注意:消費者版 Gmail 網頁**不支援 S/MIME 驗章顯示**,收件匣只會看到 `smime.p7s` 附件,這是正常的。要看「已驗證」需用 GreenMail 模式、openssl、或 Thunderbird/Outlook、或 Workspace hosted S/MIME。

---

## 4. `test-bank.sh` — 寄到銀行(簽章 + 加密)

銀行整合獨立在自己的資料夾 `bank-certs/`,與上面兩支測試的 `smime-test-certs/` **互不干涉**。

複製範本並填入銀行提供的設定:

```
cp .env.bank.example .env.bank
```

`.env.bank` 重點欄位:

```dotenv
BANK_SMTP_HOST=smtp.bank-gateway.example
BANK_SMTP_PORT=587
BANK_SMTP_USER=your-smtp-username
BANK_SMTP_PASSWORD=your-smtp-password
FROM_EMAIL=ops@yourcompany.example
TO_EMAIL=host2host@bank.example

BANK_ENCRYPT=true                       # true=簽章+AES256加密;false=只簽章
BANK_CERT=bank-certs/bank-public.cer    # 銀行的「公開憑證」(.cer/.crt),非 .key
```

執行:

```
bash test-bank.sh
```

腳本流程:讀 `.env.bank` → **檢查銀行 `.crt` 是否就緒**(加密模式下找不到會擋下並提示)→ 若 `bank-certs/our-signing.p12` 不存在則用 `keytool` 自動產生 → 編譯並執行 `BankSmimeTest`。

**建議順序**:先設 `BANK_ENCRYPT=false` 測通 SMTP 連線與認證,再改 `true` 測加密。

---

## 5. 憑證策略:先用公司憑證測試,之後銀行只需更換

S/MIME 有兩種憑證角色,測試與上線的處理不同:

**簽章憑證(你的身分)** — 一直都是你/公司的,不會換成銀行的。可先由**公司 Domain 管理員(IT)提供公司的憑證**(或自簽測試憑證)來驗證流程;正式上線時換成「銀行指定 CA 簽發」的版本即可,程式不動。

**加密用收件憑證(`BANK_CERT`)** — 測試階段先放一張**公司提供的公開憑證**當替身收件人,把加密邏輯跑通;等銀行給了 `bank-public.cer`,**只要把 `.env.bank` 的 `BANK_CERT` 指過去**,程式碼完全不用改。

> 也就是說:**現在用公司的 crts 先測試(可向 Domain 管理員索取),測試通過後,銀行端只需更換成銀行的憑證即可。** 因為所有設定都走 `.env.bank` 環境變數,換憑證 = 改一行設定。

**提醒**:用公司憑證測通,只證明「自己這邊程式正確」,不代表「已與銀行互通」。最終仍需用**銀行的 UAT 憑證 + 銀行測試主機**做一次端到端驗收(銀行會驗它要求的演算法、格式、來源 IP、CA 信任等)。

---

## 5b. `gen-test-certs.sh` — 產生完整 CA 鏈測試憑證(進階)

`test-greenmail.sh` / `test-bank.sh` 預設用 `keytool` 產**自簽**憑證,足以測「簽章 / 加密 / 寄送」機制。若你想更貼近真實情境(有 Root CA → leaf 的**信任鏈**,或要做「簽章 + 加密 + 解密 + 驗章」完整往返),可改用這支 openssl 腳本:

```
bash gen-test-certs.sh                # 預設輸出到 ./smime-test-certs
```

它會產生:測試 CA(`testca.crt`)、你方 `our-signing.p12`(alias `our-alias`)、模擬銀行的 `bank.p12`(alias `bank-alias`)與其公開憑證 `bank-public.cer`。三張 leaf 憑證都帶正確 S/MIME 屬性(`emailProtection` EKU、email SAN),並通過 CA 鏈驗證。

> 用途:`bank-public.cer` 可當 `BANK_CERT` 測加密;`bank.p12` 可用來在收件端解密 / 驗章,做完整離線往返。需要 Git Bash 內含 openssl。

---

## 6. 要向誰索取什麼

| 項目 | 來源 |
|------|------|
| 你的簽章憑證(或公司測試憑證) | 公司 Domain 管理員 / IT(或 CA 申請) |
| 將銀行 CA 加入受信任清單、啟用 S/MIME | 公司 Domain 管理員(Google Workspace) |
| 銀行公開憑證 `bank-public.cer` + 銀行 Root/Intermediate CA | **銀行**(公開憑證,非 `.key`) |
| 銀行 SMTP host/port/TLS/認證、來源 IP 白名單 | **銀行** |
| 要求的簽章雜湊(SHA-256+)、加密演算法、S/MIME 格式 | **銀行** |

---

## 7. 檔案說明

| 檔案 | 說明 |
|------|------|
| `GreenMailSmimeTest.java` | GreenMail / Gmail 測試(簽章) |
| `BankSmimeTest.java` | 銀行測試(簽章 + 加密),獨立 |
| `test-greenmail.sh` / `test-gmail.sh` / `test-bank.sh` | 三支測試腳本 |
| `gen-test-certs.sh` | 用 openssl 產生**完整 CA 鏈**測試憑證(見下) |
| `.env` / `.env.bank` | 認證與設定(**請勿 commit**) |
| `.env.bank.example` | 銀行設定範本 |
| `smime-test-certs/` | GreenMail/Gmail 用憑證 |
| `bank-certs/` | 銀行用憑證(隔離) |

> 建議將 `.env`、`.env.bank`、`lib/`、`smime-test-certs/`、`bank-certs/`、`*.class` 加入 `.gitignore`。
