package com.dbms.library;

import java.sql.*;

/**
 * DBHelper — Central database utility class.
 *
 * Responsibilities:
 *  - Holds the Derby connection URL
 *  - Creates the schema (Members, Books, Loans + indexes) on startup
 *  - Provides shared SQL-error handler and graceful shutdown
 */
public class DBHelper {

    /** Embedded Derby database URL — creates the DB folder on first run */
    public static final String DB_URL = "jdbc:derby:LibraryDB;create=true";

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEMA INITIALISATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates all tables and indexes if they do not already exist.
     * Derby error X0Y32 ("object already exists") is silently skipped.
     */
    public static void createTables(Connection con) {
        String[] ddl = {
            // Members table
            "CREATE TABLE Members (" +
                "MemberID    INT          PRIMARY KEY, " +
                "Name        VARCHAR(100) NOT NULL, " +
                "ActiveLoans INT          DEFAULT 0, " +
                "CONSTRAINT chk_loans CHECK (ActiveLoans >= 0)" +
            ")",

            // Books table
            "CREATE TABLE Books (" +
                "BookID    INT          PRIMARY KEY, " +
                "Title     VARCHAR(200) NOT NULL, " +
                "Author    VARCHAR(100) NOT NULL, " +
                "Available BOOLEAN      DEFAULT TRUE" +
            ")",

            // Loans table — LoanID is auto-generated
            "CREATE TABLE Loans (" +
                "LoanID     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " +
                "MemberID   INT  REFERENCES Members(MemberID), " +
                "BookID     INT  REFERENCES Books(BookID), " +
                "LoanDate   DATE, " +
                "ReturnDate DATE" +
            ")",

            // Indexes for faster lookups
            "CREATE INDEX idx_loans_member ON Loans(MemberID)",
            "CREATE INDEX idx_loans_book   ON Loans(BookID)",
            "CREATE INDEX idx_loans_return ON Loans(ReturnDate)",
            "CREATE INDEX idx_books_id     ON Books(BookID)"
        };

        try (Statement st = con.createStatement()) {
            for (String sql : ddl) {
                try {
                    st.executeUpdate(sql);
                    System.out.println("Created : " + sql.substring(0, Math.min(45, sql.length())) + "...");
                } catch (SQLException e) {
                    if (!"X0Y32".equals(e.getSQLState())) throw e; // skip "already exists"
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR HANDLER
    // ─────────────────────────────────────────────────────────────────────────

    /** Maps Derby SQL states to human-readable console messages. */
    public static void handleSQLError(SQLException e) {
        switch (e.getSQLState() == null ? "" : e.getSQLState()) {
            case "XSDB6" -> System.out.println("[DB LOCKED]    Delete .lck files in LibraryDB/ and retry.");
            case "23505" -> System.out.println("[DUPLICATE]    That ID already exists.");
            case "23503" -> System.out.println("[FK ERROR]     Referenced member or book does not exist.");
            case "42X05" -> System.out.println("[TABLE MISSING] Restart the app to reinitialise the schema.");
            default      -> {
                System.out.println("[SQL ERROR] State=" + e.getSQLState() + " — " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GRACEFUL SHUTDOWN
    // ─────────────────────────────────────────────────────────────────────────

    /** Sends the Derby shutdown signal; XJ015 is the expected "clean shutdown" state. */
    public static void shutdown() {
        try {
            DriverManager.getConnection("jdbc:derby:LibraryDB;shutdown=true");
        } catch (SQLException e) {
            if ("XJ015".equals(e.getSQLState())) {
                System.out.println("Derby shut down cleanly.");
            }
        }
    }
}
