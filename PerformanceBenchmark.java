package com.dbms.library;

import java.sql.*;

/**
 * PerformanceBenchmark — Phase 4: JDBC Performance Evaluation.
 *
 * Runs three comparative tests and prints a throughput summary:
 *   Test 1 — Individual inserts  vs  Batch inserts       (100 records)
 *   Test 2 — Statement           vs  PreparedStatement   (50 queries)
 *   Test 3 — Per-op commit       vs  Batched commit      (20 inserts)
 *
 * A JVM warm-up phase runs before all tests to eliminate cold-start bias.
 */
public class PerformanceBenchmark {

    public static void run(Connection con) {
        System.out.println("\n===== PERFORMANCE BENCHMARK =====");

        warmUp(con);

        long individual        = test1_individualInserts(con);
        long batch             = test1_batchInserts(con);
        long stmtTime          = test2_statementQueries(con);
        long psTime            = test2_preparedStatementQueries(con);
        long perOpCommitTime   = test3_perOpCommit(con);
        long batchedCommitTime = test3_batchedCommit(con);

        printSummary(individual, batch, stmtTime, psTime, perOpCommitTime, batchedCommitTime);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — Insert Strategy (100 records)
    // ─────────────────────────────────────────────────────────────────────────

    private static long test1_individualInserts(Connection con) {
        System.out.println("\n[Test 1] Insert strategy — 100 records");
        long start = System.currentTimeMillis();

        for (int i = 1; i <= 100; i++) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate(
                    "INSERT INTO Members VALUES (" + (30000 + i) + ", 'User" + i + "', 0)");
            } catch (SQLException e) { /* skip duplicates on re-run */ }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  Individual inserts : %d ms%n", elapsed);
        return elapsed;
    }

    private static long test1_batchInserts(Connection con) {
        long start = System.currentTimeMillis();

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Members VALUES (?, ?, 0)")) {
            for (int i = 1; i <= 100; i++) {
                ps.setInt(1, 40000 + i);
                ps.setString(2, "Batch" + i);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { /* skip duplicates on re-run */ }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  Batch inserts      : %d ms%n", elapsed);
        System.out.printf("  Speedup            : %.1fx%n",
            (double) test1_individualInserts_cached / Math.max(elapsed, 1));
        return elapsed;
    }

    // cached result for speedup display
    private static long test1_individualInserts_cached = 1;

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — Statement vs PreparedStatement (50 queries)
    // ─────────────────────────────────────────────────────────────────────────

    private static long test2_statementQueries(Connection con) {
        System.out.println("\n[Test 2] Statement vs PreparedStatement — 50 queries");
        long start = System.currentTimeMillis();

        for (int i = 1; i <= 50; i++) {
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT * FROM Members WHERE MemberID = " + (30000 + i))) {
                while (rs.next()) { /* consume result */ }
            } catch (SQLException e) { /* ignore */ }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  Statement          : %d ms%n", elapsed);
        return elapsed;
    }

    private static long test2_preparedStatementQueries(Connection con) {
        long start = System.currentTimeMillis();

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM Members WHERE MemberID = ?")) {
            for (int i = 1; i <= 50; i++) {
                ps.setInt(1, 30000 + i);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) { /* consume result */ }
                }
            }
        } catch (SQLException e) { /* ignore */ }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  PreparedStatement  : %d ms%n", elapsed);
        return elapsed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Transaction Granularity (20 inserts)
    // ─────────────────────────────────────────────────────────────────────────

    private static long test3_perOpCommit(Connection con) {
        System.out.println("\n[Test 3] Transaction granularity — 20 inserts");
        long start = System.currentTimeMillis();

        // autoCommit is ON → every insert commits immediately
        for (int i = 1; i <= 20; i++) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Members VALUES (?, ?, 0)")) {
                ps.setInt(1, 50000 + i);
                ps.setString(2, "PerOp" + i);
                ps.executeUpdate();
            } catch (SQLException e) { /* skip duplicates */ }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  Per-op commit      : %d ms%n", elapsed);
        return elapsed;
    }

    private static long test3_batchedCommit(Connection con) {
        long start = System.currentTimeMillis();

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
            try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        } finally {
            try { con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  Batched commit     : %d ms%n", elapsed);
        return elapsed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUMMARY REPORT
    // ─────────────────────────────────────────────────────────────────────────

    private static void printSummary(long individual, long batch,
                                     long stmt, long ps,
                                     long perOp, long batched) {
        System.out.println("\n===== SUMMARY REPORT =====");
        System.out.printf("%-35s %10s %15s%n", "Operation", "Time (ms)", "Throughput");
        System.out.println("-".repeat(62));
        printRow("Individual inserts (100)", individual, 100);
        printRow("Batch inserts (100)",      batch,      100);
        printRow("Statement queries (50)",   stmt,       50);
        printRow("PreparedStatement (50)",   ps,         50);
        printRow("Per-op commit (20)",       perOp,      20);
        printRow("Batched commit (20)",      batched,    20);
        System.out.println("-".repeat(62));
        System.out.printf("  Batch speedup (inserts)  : %.1fx%n",
            (double) individual / Math.max(batch, 1));
        System.out.printf("  PS speedup (queries)     : %.1fx%n",
            (double) stmt / Math.max(ps, 1));
        System.out.printf("  Batched commit speedup   : %.1fx%n",
            (double) perOp / Math.max(batched, 1));
    }

    private static void printRow(String label, long ms, int ops) {
        double tps = ms > 0 ? (ops * 1000.0 / ms) : 0;
        System.out.printf("%-35s %10d %12.1f ops/s%n", label, ms, tps);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WARM-UP
    // ─────────────────────────────────────────────────────────────────────────

    /** Runs lightweight queries for ~100 ms to warm up the JVM and Derby's plan cache. */
    private static void warmUp(Connection con) {
        System.out.print("Warming up JVM... ");
        long end = System.currentTimeMillis() + 100;
        while (System.currentTimeMillis() < end) {
            try (Statement st = con.createStatement()) {
                st.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1");
            } catch (SQLException e) { break; }
        }
        System.out.println("done.");
    }
}
