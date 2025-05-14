package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class User {
    public static int getUserRole(String login, String password, Connection connection) throws SQLException {
        String sql = "SELECT role FROM users WHERE login = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("role");
            return -1;
        }
    }

    public static int createUser(String login, String password, int role, Connection connection) throws SQLException {
        String sql = "INSERT INTO users (login, password, role) VALUES (?, ?, ?) RETURNING user_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            stmt.setInt(3, role);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
            throw new SQLException("Не удалось создать пользователя");
        }
    }

    public static void linkStudentToUser(int userId, int studentId, Connection connection) throws SQLException {
        String sql = "INSERT INTO student_user (user_id, student_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, studentId);
            stmt.executeUpdate();
        }
    }

    public static void linkTeacherToUser(int userId, int teacherId, Connection connection) throws SQLException {
        String sql = "INSERT INTO teacher_user (user_id, teacher_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, teacherId);
            stmt.executeUpdate();
        }
    }
}
