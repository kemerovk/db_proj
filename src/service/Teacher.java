package service;

import java.sql.*;

public class Teacher {
    public static int createTeacher(String firstName, String lastName, String middleName, Connection connection) throws SQLException {
        String sql = "INSERT INTO teacher (first_name, last_name, middle_name) VALUES (?, ?, ?) RETURNING teacher_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setObject(3, middleName, Types.VARCHAR);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("teacher_id");
            throw new SQLException("Не удалось создать преподавателя");
        }
    }

    public static int getTeacherIdByName(String fullName, Connection conn) throws SQLException {
        String[] names = fullName.split(" ");
        String query = "SELECT teacher_id FROM teacher WHERE first_name = ? AND last_name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, names[0]);
            stmt.setString(2, names[1]);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("teacher_id");
            }
        }

        return -1; // Преподаватель не найден
    }
}
