package com.dbms.library;

import java.sql.*;
import java.util.Scanner;

/**
 * LoanManager — Handles all loan lifecycle operations.
 *
 * All write operations use explicit transaction management:
 *   setAutoCommit(false) → steps → commit() / rollback()
 *
 * processLoan() additionally uses a Savepoint for partial rollback.
 */
public class LoanManager {

    // ─────────────────────────────────────────────────────────────────────────
    // PROCESS LOAN
    // 3 atomic steps:
    //   1. Verify book availability         → rollback if unavailable
    //   2. INSERT loan record               ← Savepoint set here
    //   3. UPDATE Books + Members           → rollback(savepoint) on failure
    // ─────────────────────────────────────────────────────────────────────────
    public static void processLoan(Connection con, Scanner sc) {
        System.out.print("Enter Member ID : ");
        int mid = sc.nextInt();
        System.out.print("Enter Book ID   : ");
        int bid = sc.nextInt();
        sc.nextLine();

        Savepoint sp = null;

        try {
            con.setAutoCommit(false);

            // ── Step 1: Check book availability ──────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT Available FROM Books WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    System.out.println("Book not found.");
                    con.rollback();
                    return;
                }
                if (!rs.getBoolean("Available")) {
                    System.out.println("Book is currently unavailable.");
                    con.rollback();
                    return;
                }
            }

            // ── Savepoint: availability confirmed ────────────────────────────
            sp = con.setSavepoint("AFTER_AVAILABILITY_CHECK");

            // ── Step 2: Insert loan record ────────────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Loans (MemberID, BookID, LoanDate) " +
                    "VALUES (?, ?, CURRENT_DATE)")) {
                ps.setInt(1, mid);
                ps.setInt(2, bid);
                ps.executeUpdate();
            }

            // ── Step 3a: Mark book as unavailable ─────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Books SET Available = FALSE WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ps.executeUpdate();
            }

            // ── Step 3b: Increment member's active loan count ─────────────────
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
                    con.rollback(sp);  // partial rollback — undo steps 2 & 3 only
                    System.out.println("Partial rollback to savepoint performed.");
                } else {
                    con.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            DBHelper.handleSQLError(e);
        } finally {
            try { con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETURN BOOK
    // 3 atomic steps:
    //   1. Fetch active loan (ReturnDate IS NULL guard)
    //   2. Set ReturnDate = CURRENT_DATE
    //   3. Mark book available + decrement ActiveLoans
    // ─────────────────────────────────────────────────────────────────────────
    public static void returnBook(Connection con, Scanner sc) {
        System.out.print("Enter Loan ID : ");
        int lid = sc.nextInt();
        sc.nextLine();

        try {
            con.setAutoCommit(false);

            int mid, bid;

            // ── Step 1: Fetch active loan details ─────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT MemberID, BookID FROM Loans " +
                    "WHERE LoanID = ? AND ReturnDate IS NULL")) {
                ps.setInt(1, lid);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    System.out.println("Active loan not found for Loan ID: " + lid);
                    con.rollback();
                    return;
                }
                mid = rs.getInt("MemberID");
                bid = rs.getInt("BookID");
            }

            // ── Step 2: Record return date ────────────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Loans SET ReturnDate = CURRENT_DATE WHERE LoanID = ?")) {
                ps.setInt(1, lid);
                ps.executeUpdate();
            }

            // ── Step 3a: Mark book as available ───────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Books SET Available = TRUE WHERE BookID = ?")) {
                ps.setInt(1, bid);
                ps.executeUpdate();
            }

            // ── Step 3b: Decrement member's active loan count ─────────────────
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Members SET ActiveLoans = ActiveLoans - 1 WHERE MemberID = ?")) {
                ps.setInt(1, mid);
                ps.executeUpdate();
            }

            con.commit();
            System.out.println("Book Returned Successfully.");

        } catch (SQLException e) {
            try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            DBHelper.handleSQLError(e);
        } finally {
            try { con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEW ACTIVE LOANS
    // 3-table JOIN: Loans ⋈ Members ⋈ Books, filtered by ReturnDate IS NULL
    // ─────────────────────────────────────────────────────────────────────────
    public static void viewLoans(Connection con) {
        System.out.println("\n===== ACTIVE LOANS =====");

        String sql =
            "SELECT L.LoanID, M.Name, B.Title, L.LoanDate " +
            "FROM   Loans L " +
            "JOIN   Members M ON L.MemberID = M.MemberID " +
            "JOIN   Books   B ON L.BookID   = B.BookID   " +
            "WHERE  L.ReturnDate IS NULL";

        try (Statement st  = con.createStatement();
             ResultSet rs  = st.executeQuery(sql)) {

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf(
                    "LoanID: %-4d | Member: %-20s | Book: %-30s | Date: %s%n",
                    rs.getInt("LoanID"),
                    rs.getString("Name"),
                    rs.getString("Title"),
                    rs.getDate("LoanDate")
                );
            }
            if (!found) System.out.println("No active loans.");

        } catch (SQLException e) {
            DBHelper.handleSQLError(e);
        }
    }
}
