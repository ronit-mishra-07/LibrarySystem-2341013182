package com.dbms.library;

import java.sql.*;
import java.util.Scanner;

/**
 * BookService — CRUD operations related to books.
 */
public class BookService {

    /**
     * Adds a new book to the catalogue.
     * Book is marked Available = TRUE by default.
     * Uses PreparedStatement to prevent SQL injection.
     */
    public static void addBook(Connection con, Scanner sc) {
        System.out.print("Enter Book ID     : ");
        int id = sc.nextInt();
        sc.nextLine();
        System.out.print("Enter Book Title  : ");
        String title = sc.nextLine();
        System.out.print("Enter Author Name : ");
        String author = sc.nextLine();

        String sql = "INSERT INTO Books VALUES (?, ?, ?, TRUE)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.executeUpdate();
            System.out.println("Book Added Successfully.");
        } catch (SQLException e) {
            DBHelper.handleSQLError(e);
        }
    }
}
