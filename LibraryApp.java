package com.dbms.library;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * LibraryApp — Entry point for the JDBC Library Loan Management System.
 * Handles the main menu loop and delegates to service classes.
 *
 * Course  : Database Management Systems Lab
 * Roll No : 2341013229
 */
public class LibraryApp {

    public static void main(String[] args) {

        try (Connection con = DriverManager.getConnection(DBHelper.DB_URL);
             Scanner sc  = new Scanner(System.in)) {

            System.out.println("Database Connected Successfully.");
            DBHelper.createTables(con);

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
                    case 1 -> MemberService.registerMember(con, sc);
                    case 2 -> BookService.addBook(con, sc);
                    case 3 -> LoanManager.processLoan(con, sc);
                    case 4 -> LoanManager.returnBook(con, sc);
                    case 5 -> LoanManager.viewLoans(con);
                    case 6 -> PerformanceBenchmark.run(con);
                    case 7 -> {
                        DBHelper.shutdown();
                        System.out.println("Goodbye!");
                        System.exit(0);
                    }
                    default -> System.out.println("Invalid choice. Please enter 1-7.");
                }
            }

        } catch (SQLException e) {
            DBHelper.handleSQLError(e);
        }
    }
}
