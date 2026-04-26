# GST Reconciliation Tool

A full-stack application to reconcile GST data between **Tally** and **GST Portal Excel files**.
The tool compares records based on **GSTIN + Invoice Number**, identifies missing entries, and highlights mismatches in tax values.

---

## 🚀 Features

* Upload **Tally** and **GST Portal** `.xlsx` files
* Compare based on:

  * GSTIN
  * Invoice Number
* Identify:

  * Missing records in Tally
  * Missing records in GST
  * Value mismatches (Taxable Value, IGST, CGST)
* Generate a **single Excel report** with:

  * `Missing_In_Tally` sheet
  * `Missing_In_GST` sheet
  * `Mismatch_Report` sheet (with highlighted differences)
* Download report directly from UI
* Clean and responsive UI using Bootstrap

---

## 🛠 Tech Stack

### Backend

* Java 17+
* Spring Boot
* Apache POI (Excel processing)

### Frontend

* HTML
* CSS (Bootstrap)
* JavaScript (Fetch API)

---

## 📂 Project Structure

```
gst-reconciliation/
│
├── src/main/java/org/tally/gst_reconcillation/
│   ├── controller/
│   ├── service/
│   ├── model/
│   ├── dto/
│   └── util/
│
├── src/main/resources/
│   └── application.properties
│
├── frontend/
│   └── index.html
│
├── pom.xml
└── README.md
```

---

## ⚙️ Setup & Run Locally

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
java -jar target/*.jar
```

App will start at:

```
http://localhost:8080
```

---

## 🌐 API Endpoints

### 🔹 Upload & Reconcile

```
POST /api/reconcile
```

**Form Data:**

* `tally` → Tally Excel file
* `gst` → GST Excel file

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

### 🔹 Download Report

```
GET /api/download?fileName=<fileName>
```

---

## 📊 Output Report Format

### 1. Missing_In_Tally

Records present in GST but not in Tally

### 2. Missing_In_GST

Records present in Tally but not in GST

### 3. Mismatch_Report

* Highlights mismatched fields in **red**
* Includes a **Status column**:

  * `Less in Tally (IGST|Tax. Value)`
  * `More in Tally (CGST)`

---

## 💡 Key Logic

* Unique Key:

```
GSTIN + "_" + InvoiceNumber
```

* Mismatch Condition:

```
|GST Value - Tally Value| > 0.01
```

---

## ⚠️ Important Notes

* Files are temporarily stored in:

```
System.getProperty("java.io.tmpdir")
```

* Reports are **not permanently stored**
* Download immediately after generation

---

## ☁️ Deployment (Render)

### Steps:

1. Push code to GitHub
2. Create Web Service on Render
3. Use:

   * Build Command: `mvn clean package`
   * Start Command: `java -jar target/*.jar`
4. Set environment variable:

```
PORT=8080
```

---

## 🔐 CORS Configuration (if needed)

```java
registry.addMapping("/api/**")
        .allowedOrigins("*")
        .allowedMethods("*");
```

---

## 🧪 Future Improvements

* Drag & drop file upload
* Progress indicator for large files
* Authentication & user sessions
* Persistent storage (S3 / DB)
* Support for large datasets (100k+ rows)

---

## 👨‍💻 Author

**Ankit S. Bose**

---

## 📄 License

This project is for internal/learning use. You can modify and extend as needed.

---
