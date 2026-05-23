package com.dbms.library;

import java.sql.*;
import java.util.Scanner;

/**
 * MemberService — CRUD operations related to library members.
 */
public class MemberService {

    /**
     * Registers a new member by inserting a row into the Members table.
     * Uses PreparedStatement to prevent SQL injection.
     */
    public static void registerMember(Connection con, Scanner sc) {
        System.out.print("Enter Member ID   : ");
        int id = sc.nextInt();
        sc.nextLine();
        System.out.print("Enter Member Name : ");
        String name = sc.nextLine();

        String sql = "INSERT INTO Members VALUES (?, ?, 0)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
            System.out.println("Member Registered Successfully.");
        } catch (SQLException e) {
            DBHelper.handleSQLError(e);
        }
    }
}
