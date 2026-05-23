# JDBC Library Loan Management System

> End-to-End JDBC Application with Transaction Management & Performance Evaluation using Apache Derby

**Course:** Database Implementation in JDBC (CSE3488)
**Roll Number:** 2341013182
**University:** Siksha 'O' Anusandhan (Deemed to be University), Bhubaneswar
**Academic Year:** 2025–2026

---

## Overview

A console-driven Library Loan Management System built entirely in Java using JDBC and Apache Derby (embedded mode). The system demonstrates:

- Explicit ACID transaction management (commit, rollback, savepoints)
- PreparedStatement-based parameterized CRUD operations
- Built-in performance benchmarking across multiple JDBC strategies
- Modular architecture — each responsibility in its own class

---

## Project Structure

```
src/
└── main/
    └── java/
        └── com/
            └── dbms/
                └── LibrarySystem_2341013182/
                    └── LibrarySystem.java
                    ├── README.md
                    └── .gitignore
```

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | 17 or above |
| Apache Derby | 10.16.1.1 (via Maven) |
| Build Tool | Maven 3.x |
| IDE | IntelliJ IDEA / Eclipse / VS Code |

---

## Setup & Run Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/ronit-mishra-07/LibrarySystem-2341013182.git
cd LibrarySystem-2341013182
```

### 2. Build the Project

```bash
mvn clean package
```

> Derby is pulled automatically from Maven Central — no manual JAR download needed.

### 3. Run via Maven

```bash
mvn exec:java -Dexec.mainClass="com.dbms.LibrarySystem_2341013182.LibrarySystem"
```

### 4. Or run the packaged JAR directly

```bash
java -jar target/LibrarySystem-2341013182-1.0-jar-with-dependencies.jar
```

> **Note:** `LibraryDB/` is auto-created by Derby on first run and is listed in `.gitignore`. Do not push it.

---

## CLI Menu

```
========== LIBRARY LOAN MANAGEMENT ==========
1. Register Member
2. Add Book
3. Process Loan
4. Return Book
5. View Active Loans
6. Performance Test
7. Exit
Enter Choice:
```

---

## Sample Session

```
Database Connected Successfully.
Created : CREATE TABLE Members (MemberID INT PRIMARY...
Created : CREATE TABLE Books (BookID INT PRIMARY KEY...
Created : CREATE TABLE Loans (LoanID INT GENERATED A...

Enter Choice: 1
Enter Member ID   : 101
Enter Member Name : Riya Sharma
Member Registered Successfully.

Enter Choice: 2
Enter Book ID     : 201
Enter Book Title  : Database System Concepts
Enter Author Name : Silberschatz
Book Added Successfully.

Enter Choice: 3
Enter Member ID : 101
Enter Book ID   : 201
Loan Processed Successfully.

Enter Choice: 5
===== ACTIVE LOANS =====
LoanID: 1    | Member: Riya Sharma         | Book: Database System Concepts        | Date: 2026-05-23

Enter Choice: 4
Enter Loan ID : 1
Book Returned Successfully.
```

---

## Database Schema

### Members
| Field | Type | Constraint |
|-------|------|------------|
| MemberID | INT | Primary Key |
| Name | VARCHAR(100) | Not Null |
| ActiveLoans | INT | Default 0, CHECK ≥ 0 |

### Books
| Field | Type | Constraint |
|-------|------|------------|
| BookID | INT | Primary Key |
| Title | VARCHAR(200) | Not Null |
| Author | VARCHAR(100) | Not Null |
| Available | BOOLEAN | Default TRUE |

### Loans
| Field | Type | Constraint |
|-------|------|------------|
| LoanID | INT | Identity PK (auto-generated) |
| MemberID | INT | FK → Members |
| BookID | INT | FK → Books |
| LoanDate | DATE | CURRENT_DATE on insert |
| ReturnDate | DATE | NULL if loan is active |

**Indexes:**

| Index Name | Table | Column | Purpose |
|------------|-------|--------|---------|
| `idx_loans_member` | Loans | MemberID | Fast member loan lookup |
| `idx_loans_book` | Loans | BookID | Fast book loan lookup |
| `idx_loans_return` | Loans | ReturnDate | Fast active loan filter |
| `idx_books_id` | Books | BookID | Fast book availability check |

---

## Transaction Management

### `processLoan()` — 3-Step Atomic Loan with Savepoint

```
setAutoCommit(false)
     │
     ├─ Step 1: Verify book availability       ← rollback() if unavailable
     │
     ├─ setSavepoint("AFTER_AVAILABILITY_CHECK")
     │
     ├─ Step 2: INSERT into Loans
     ├─ Step 3: UPDATE Books  (Available = FALSE)
     ├─ Step 4: UPDATE Members (ActiveLoans + 1)
     │
     ├─ commit()                                ← all steps succeed
     └─ rollback(savepoint) on failure          ← partial rollback to savepoint
```

### `returnBook()` — 3-Step Atomic Return

```
setAutoCommit(false)
     │
     ├─ Fetch active loan (LoanID + ReturnDate IS NULL guard)
     ├─ UPDATE Loans     → ReturnDate = CURRENT_DATE
     ├─ UPDATE Books     → Available = TRUE
     ├─ UPDATE Members   → ActiveLoans - 1
     │
     ├─ commit()
     └─ rollback() on any failure
```

---

## Benchmark Results

| Test Case | Records | Time (ms) | Throughput |
|-----------|---------|-----------|------------|
| Individual Inserts | 100 | ~312 | ~320 ops/sec |
| Batch Inserts | 100 | ~48 | ~2083 ops/sec |
| Statement Queries | 50 | ~91 | ~549 ops/sec |
| PreparedStatement Queries | 50 | ~14 | ~3571 ops/sec |
| Per-op Commit | 20 | ~76 | ~263 ops/sec |
| Batched Commit | 20 | ~12 | ~1667 ops/sec |

**Key findings:**
- Batch inserts are **~6.5× faster** than individual inserts
- `PreparedStatement` is **~6.5× faster** than `Statement` for repeated queries
- Batched commit is **~6.3× faster** than per-operation commit

> Results are approximate and vary by hardware. A JVM warm-up phase runs before each test to eliminate cold-start bias.

---

## Common Issues

| Error | SQLState | Cause | Fix |
|-------|----------|-------|-----|
| DB Locked | `XSDB6` | Derby lock file present | Delete `.lck` files in `LibraryDB/` |
| Duplicate ID | `23505` | Primary key already exists | Use a unique Member/Book ID |
| FK Violation | `23503` | Member or Book ID not found | Register member/book first |
| Table Missing | `42X05` | Schema not initialised | Restart the app |

---

## References

- [Apache Derby Documentation](https://db.apache.org/derby/docs/)
- [Oracle JDBC Tutorial](https://docs.oracle.com/javase/tutorial/jdbc/)
- Database System Concepts — Silberschatz, Korth & Sudarshan

---

## License

This project is submitted as academic coursework for CSE3488 at ITER, SOA University.
