package com.dbms.LibrarySystem_2341013182;

import java.sql.*;
import java.util.Scanner;

public class LibrarySystem {

    static final String DB_URL = "jdbc:derby:LibraryDB;create=true";

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(DB_URL);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Database Connected Successfully.");
            createTables(con);

            while (true) {
                System.out.println("\n========== LIBRARY LOAN MANAGEMENT ==========");
                System.out.println("1. Register Member");
                System.out.println("2. Add Book");
                System.out.println("3. Process Loan");
                System.out.println("4. Return Book");
                System.out.println("5. View Active Loans");
                System.out.println("6. Performance Test");
                System.out.println("7. Exit");
                System.out.print("Enter Choice: ");

                int choice = sc.nextInt();
                sc.nextLine();

                switch (choice) {
                    case 1 -> registerMember(con, sc);
                    case 2 -> addBook(con, sc);
                    case 3 -> processLoan(con, sc);
                    case 4 -> returnBook(con, sc);
                    case 5 -> viewLoans(con);
                    case 6 -> performanceTest(con);
                    case 7 -> {
                        shutdownDatabase();
                        System.out.println("Goodbye!");
                        System.exit(0);
                    }
                    default -> System.out.println("Invalid choice. Please enter 1-7.");
                }
            }

        } catch (SQLException e) {
            handleSQLError(e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 1: DATABASE INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────
    static void createTables(Connection con) {
        String[] queries = {
            "CREATE TABLE Members (" +
                "MemberID INT PRIMARY KEY, " +
                "Name VARCHAR(100) NOT NULL, " +
                "ActiveLoans INT DEFAULT 0, " +
                "CONSTRAINT chk_loans CHECK (ActiveLoans >= 0)" +
            ")",
            "CREATE TABLE Books (" +
                "BookID INT PRIMARY KEY, " +
                "Title VARCHAR(200) NOT NULL, " +
                "Author VARCHAR(100) NOT NULL, " +
                "Available BOOLEAN DEFAULT TRUE" +
            ")",
            "CREATE TABLE Loans (" +
                "LoanID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                "MemberID INT REFERENCES Members(MemberID), " +
                "BookID INT REFERENCES Books(BookID), " +
                "LoanDate DATE, " +
                "ReturnDate DATE" +
            ")",
            "CREATE INDEX idx_loans_member ON Loans(MemberID)",
            "CREATE INDEX idx_loans_book   ON Loans(BookID)",
            "CREATE INDEX idx_loans_return ON Loans(ReturnDate)",
            "CREATE INDEX idx_books_isbn   ON Books(BookID)"
        };

        try (Statement st = con.createStatement()) {
            for (String q : queries) {
                try {
                    st.executeUpdate(q);
                    System.out.println("Created: " + q.substring(0, Math.min(40, q.length())) + "...");
                } catch (SQLException e) {
                    // X0Y32 = table/index already exists — safe to skip
                    if (!"X0Y32".equals(e.getSQLState())) throw e;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 3: CORE BUSINESS LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    static void registerMember(Connection con, Scanner sc) {
        System.out.print("Enter Member ID: ");
        int id = sc.nextInt();
        sc.nextLine();
        System.out.print("Enter Member Name: ");
        String name = sc.nextLine();

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Members VALUES (?, ?, 0)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
            System.out.println("Member Registered Successfully.");
        } catch (SQLException e) {
            handleSQLError(e);
        }
    }

    static void addBook(Connection con, Scanner sc) {
        System.out.print("Enter Book ID: ");
        int id = sc.nextInt();
        sc.nextLine();
        System.out.print("Enter Book Title: ");
        String title = sc.nextLine();
        System.out.print("Enter Author Name: ");
        String author = sc.nextLine();

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Books VALUES (?, ?, ?, TRUE)")) {
            ps.setInt(1, id);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.executeUpdate();
            System.out.println("Book Added Successfully.");
        } catch (SQLException e) {
            handleSQLError(e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 2: TRANSACTION MANAGEMENT — processLoan
    // setAutoCommit(false) → 3 atomic steps → commit / rollback
    // Savepoint after step 1 for partial rollback if step 2 or 3 fails
    // ─────────────────────────────────────────────────────────────────────────
    static void processLoan(Connection con, Scanner sc) {
        System.out.print("Enter Member ID: ");
        int mid = sc.nextInt();
        System.out.print("Enter Book ID: ");
        int bid = sc.nextInt();

        Savepoint sp = null;

        try {
            con.setAutoCommit(false);

            // Step 1: verify book availability
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT Available FROM Books WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    System.out.println("Book not found.");
                    con.rollback();
                    return;
                }
                if (!rs.getBoolean(1)) {
                    System.out.println("Book is currently unavailable.");
                    con.rollback();
                    return;
                }
            }

            // Savepoint after availability confirmed
            sp = con.setSavepoint("AFTER_CHECK");

            // Step 2: insert loan record
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Loans (MemberID, BookID, LoanDate) VALUES (?, ?, CURRENT_DATE)")) {
                ps.setInt(1, mid);
                ps.setInt(2, bid);
                ps.executeUpdate();
            }

            // Step 3: update Books and Members
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Books SET Available = FALSE WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Members SET ActiveLoans = ActiveLoans + 1 WHERE MemberID = ?")) {
                ps.setInt(1, mid);
                ps.executeUpdate();
            }

            con.commit();
            System.out.println("Loan Processed Successfully.");

        } catch (SQLException e) {
            try {
                if (sp != null) {
                    con.rollback(sp); // partial rollback to savepoint
                    System.out.println("Partial rollback to savepoint performed.");
                } else {
                    con.rollback();
                }
            } catch (Exception ex) { ex.printStackTrace(); }
            handleSQLError(e);
        } finally {
            try { con.setAutoCommit(true); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRANSACTION MANAGEMENT — returnBook
    // ─────────────────────────────────────────────────────────────────────────
    static void returnBook(Connection con, Scanner sc) {
        System.out.print("Enter Loan ID: ");
        int lid = sc.nextInt();

        try {
            con.setAutoCommit(false);

            int mid, bid;

            // fetch loan details
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT MemberID, BookID FROM Loans WHERE LoanID = ? AND ReturnDate IS NULL")) {
                ps.setInt(1, lid);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    System.out.println("Active loan not found.");
                    con.rollback();
                    return;
                }
                mid = rs.getInt(1);
                bid = rs.getInt(2);
            }

            // update ReturnDate
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Loans SET ReturnDate = CURRENT_DATE WHERE LoanID = ?")) {
                ps.setInt(1, lid);
                ps.executeUpdate();
            }

            // mark book available
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Books SET Available = TRUE WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ps.executeUpdate();
            }

            // decrement active loans
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Members SET ActiveLoans = ActiveLoans - 1 WHERE MemberID = ?")) {
                ps.setInt(1, mid);
                ps.executeUpdate();
            }

            con.commit();
            System.out.println("Book Returned Successfully.");

        } catch (SQLException e) {
            try { con.rollback(); } catch (Exception ex) { ex.printStackTrace(); }
            handleSQLError(e);
        } finally {
            try { con.setAutoCommit(true); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    static void viewLoans(Connection con) {
        System.out.println("\n===== ACTIVE LOANS =====");
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT L.LoanID, M.Name, B.Title, L.LoanDate " +
                "FROM Loans L " +
                "JOIN Members M ON L.MemberID = M.MemberID " +
                "JOIN Books B ON L.BookID = B.BookID " +
                "WHERE L.ReturnDate IS NULL")) {

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("LoanID: %d | Member: %s | Book: %s | Date: %s%n",
                    rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4));
            }
            if (!found) System.out.println("No active loans.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 4: PERFORMANCE EVALUATION
    // ─────────────────────────────────────────────────────────────────────────
    static void performanceTest(Connection con) {
        System.out.println("\n===== PERFORMANCE BENCHMARK =====");

        // warm-up: discard cold-start results
        warmUp(con);

        // ── Test 1: Individual inserts vs batch inserts ──────────────────────
        System.out.println("\n[Test 1] Insert strategy — 100 records");

        long s1 = System.currentTimeMillis();
        for (int i = 1; i <= 100; i++) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate(
                    "INSERT INTO Members VALUES (" + (30000 + i) + ", 'User" + i + "', 0)");
            } catch (SQLException e) { /* skip duplicates */ }
        }
        long individual = System.currentTimeMillis() - s1;
        System.out.printf("  Individual inserts : %d ms%n", individual);

        long s2 = System.currentTimeMillis();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Members VALUES (?, ?, 0)")) {
            for (int i = 1; i <= 100; i++) {
                ps.setInt(1, 40000 + i);
                ps.setString(2, "Batch" + i);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { /* skip duplicates */ }
        long batch = System.currentTimeMillis() - s2;
        System.out.printf("  Batch inserts      : %d ms%n", batch);
        System.out.printf("  Speedup            : %.1fx%n", (double) individual / Math.max(batch, 1));

        // ── Test 2: Statement vs PreparedStatement ───────────────────────────
        System.out.println("\n[Test 2] Statement vs PreparedStatement — 50 queries");

        long s3 = System.currentTimeMillis();
        for (int i = 1; i <= 50; i++) {
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT * FROM Members WHERE MemberID = " + (30000 + i))) {
                while (rs.next()) { /* consume */ }
            } catch (SQLException e) { /* ignore */ }
        }
        long stmtTime = System.currentTimeMillis() - s3;
        System.out.printf("  Statement          : %d ms%n", stmtTime);

        long s4 = System.currentTimeMillis();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM Members WHERE MemberID = ?")) {
            for (int i = 1; i <= 50; i++) {
                ps.setInt(1, 30000 + i);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) { /* consume */ }
                }
            }
        } catch (SQLException e) { /* ignore */ }
        long psTime = System.currentTimeMillis() - s4;
        System.out.printf("  PreparedStatement  : %d ms%n", psTime);

        // ── Test 3: Per-op commit vs batched commit ──────────────────────────
        System.out.println("\n[Test 3] Transaction granularity — 20 inserts");

        long s5 = System.currentTimeMillis();
        for (int i = 1; i <= 20; i++) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Members VALUES (?, ?, 0)")) {
                ps.setInt(1, 50000 + i);
                ps.setString(2, "PerOp" + i);
                ps.executeUpdate();
                // commit after every single insert
            } catch (SQLException e) { /* skip */ }
        }
        long perOpTime = System.currentTimeMillis() - s5;
        System.out.printf("  Per-op commit      : %d ms%n", perOpTime);

        long s6 = System.currentTimeMillis();
        try {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Members VALUES (?, ?, 0)")) {
                for (int i = 1; i <= 20; i++) {
                    ps.setInt(1, 60000 + i);
                    ps.setString(2, "Batched" + i);
                    ps.executeUpdate();
                }
            }
            con.commit();
        } catch (SQLException e) {
            try { con.rollback(); } catch (Exception ex) { ex.printStackTrace(); }
        } finally {
            try { con.setAutoCommit(true); } catch (Exception e) { e.printStackTrace(); }
        }
        long batchedCommitTime = System.currentTimeMillis() - s6;
        System.out.printf("  Batched commit     : %d ms%n", batchedCommitTime);

        // ── Throughput report ────────────────────────────────────────────────
        System.out.println("\n===== SUMMARY REPORT =====");
        System.out.printf("%-30s %10s %15s%n", "Operation", "Time (ms)", "Throughput");
        System.out.println("-".repeat(58));
        printRow("Individual inserts (100)", individual, 100);
        printRow("Batch inserts (100)",      batch,      100);
        printRow("Statement queries (50)",   stmtTime,   50);
        printRow("PreparedStatement (50)",   psTime,     50);
        printRow("Per-op commit (20)",       perOpTime,  20);
        printRow("Batched commit (20)",      batchedCommitTime, 20);
    }

    static void warmUp(Connection con) {
        System.out.print("Warming up JVM... ");
        long end = System.currentTimeMillis() + 100;
        while (System.currentTimeMillis() < end) {
            try (Statement st = con.createStatement()) {
                st.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1");
            } catch (SQLException ignored) { break; }
        }
        System.out.println("done.");
    }

    static void printRow(String label, long ms, int ops) {
        double tps = ms > 0 ? (ops * 1000.0 / ms) : 0;
        System.out.printf("%-30s %10d %12.1f ops/s%n", label, ms, tps);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────────
    static void handleSQLError(SQLException e) {
        switch (e.getSQLState() == null ? "" : e.getSQLState()) {
            case "XSDB6" -> System.out.println("[DB LOCKED] Delete .lck files in LibraryDB/ and retry.");
            case "23505" -> System.out.println("[DUPLICATE] That ID already exists.");
            case "23503" -> System.out.println("[FK ERROR] Referenced member or book does not exist.");
            case "42X05" -> System.out.println("[TABLE MISSING] Run option 0 to reinitialise schema.");
            default      -> {
                System.out.println("[SQL ERROR] State=" + e.getSQLState() + " — " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static void shutdownDatabase() {
        try {
            DriverManager.getConnection("jdbc:derby:LibraryDB;shutdown=true");
        } catch (SQLException e) {
            if ("XJ015".equals(e.getSQLState())) {
                System.out.println("Derby shutdown cleanly.");
            }
        }
    }
}