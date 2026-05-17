# GST Reconciliation Tool

A full-stack application to reconcile GST data between **Tally** and **GST Portal Excel files**.
The tool compares records based on **GSTIN + Invoice Number**, identifies missing entries, and highlights mismatches in tax values.

---

## Features

- Upload **Tally** and **GST Portal** `.xlsx` files
- **GSTIN-level aggregate exclusion** — if the selected aggregate field totals match for a GSTIN across both files, that GSTIN is excluded entirely from all report sheets
- Configurable **tolerance limit** — acceptable difference threshold for value comparisons (0 = exact match)
- Identify:
  - Records missing in Tally
  - Records missing in GST Portal
  - Value mismatches (Taxable Value, IGST, SGST, CGST)
- Generate a **single Excel report** with three sheets:
  - `Missing_In_Tally`
  - `Missing_In_GST`
  - `Mismatch_Report` (mismatched fields highlighted in red)
- Download report directly from the UI
- Clean and responsive UI using Bootstrap 5

---

## Tech Stack

### Backend
- Java 17
- Spring Boot 4
- Apache POI 5.2.5 — Excel processing (`XSSFWorkbook` for reading, `SXSSFWorkbook` for writing)

### Frontend
- HTML + CSS (Bootstrap 5.3)
- JavaScript (Fetch API)

---

## Project Structure

```
gst-reconciliation/
│
├── src/main/java/org/tally/gst_reconcillation/
│   ├── controller/
│   │   ├── ReconciliationController.java
│   │   └── DownloadController.java
│   ├── service/
│   │   └── ReconciliationService.java
│   ├── model/
│   │   └── InvoiceRecord.java
│   ├── dto/
│   │   └── ReconciliationResultDto.java
│   └── util/
│       └── GeneralUtility.java
│
├── src/main/resources/
│   ├── static/
│   │   └── index.html
│   └── application.properties
│
├── pom.xml
└── README.md
```

---

## Setup & Run Locally

### 1. Clone Repository

```bash
git clone https://github.com/<your-username>/gst-reconciliation.git
cd gst-reconciliation
```

### 2. Build Project

```bash
mvn clean package
```

### 3. Run Application

```bash
java -Xmx380m -jar target/*.jar
```

App will start at `http://localhost:8080`

---

## API Endpoints

### Upload & Reconcile

```
POST /api/reconcile
```

**Form Data:**

| Field | Type | Description |
|---|---|---|
| `tally` | File | Tally Excel file (.xlsx) |
| `gst` | File | GST Portal Excel file (.xlsx) |
| `aggregateField` | String | Field for GSTIN-level exclusion (`taxableValue`, `igst`, `sgst`, `cgst`) |
| `tolerance` | double | Acceptable value difference (e.g. `0.5`). Use `0` for exact match |

**Response:**

```json
{
  "missingInTally": 10,
  "missingInGST": 5,
  "mismatches": 3,
  "fileName": "Reconciliation_Report_123456.xlsx"
}
```

---

### Download Report

```
GET /api/download?fileName=<fileName>
```

Serves the generated report from the server's temp directory.

---

## Excel File Format

Both Tally and GST Portal files must follow this exact column layout:

| Col | Field |
|---|---|
| A | Month |
| B | GSTIN |
| C | Party Name |
| D | Invoice Number |
| E | Invoice Date |
| F | Taxable Value |
| G | IGST |
| H | SGST |
| I | CGST |

A sample template is available for download from the UI at `/templates/gst-template.xlsx`.

---

## Output Report Format

### Missing_In_Tally
Records present in the GST Portal file but absent in Tally.

### Missing_In_GST
Records present in Tally but absent in the GST Portal file.

### Mismatch_Report
Side-by-side comparison of GST and Tally values for the same invoice:
- Mismatched fields highlighted in **red**
- Status column indicates direction and affected fields, e.g. `Less in Tally (IGST | SGST)`

---

## Key Logic

**Unique Key:**
```
normalizeKeyPart(GSTIN) + "_" + normalizeKeyPart(InvoiceNumber)
```
Key normalization strips all non-alphanumeric characters and uppercases — handles formatting inconsistencies between files.

**GSTIN-level Exclusion:**
```
If |sum(aggregateField in Tally) - sum(aggregateField in GST)| < 0.0001 → exclude GSTIN entirely
```

**Mismatch Condition:**
```
|GST Value - Tally Value| > tolerance
```

**Duplicate Invoice Handling:**
Multiple records with the same GSTIN + Invoice Number are supported. Each GST record is matched to the closest Tally record; unmatched leftovers are reported as missing.

---

## Important Notes

- Uploaded files and generated reports are stored in `System.getProperty("java.io.tmpdir")` — **not permanently stored**
- Download the report immediately after generation — it will not survive a server restart
- Formula cells in Excel are evaluated using Apache POI's `FormulaEvaluator`

---

## Recommended application.properties

```properties
spring.application.name=gst_reconcillation
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB
```

---

## CORS Configuration

```java
@CrossOrigin(origins = "*")
```

Currently applied at the controller level. For global config:

```java
registry.addMapping("/api/**")
        .allowedOrigins("*")
        .allowedMethods("*");
```

---

## Author

**Ankit S. Bose**

---

## License

This project is for internal/learning use. You can modify and extend as needed.
