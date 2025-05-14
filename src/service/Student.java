package service;

import java.sql.*;

public class Student {
    public static int createStudent(String firstName, String lastName, String middleName, int faculty, Connection connection) throws SQLException {
        String sql = "INSERT INTO student (first_name, last_name, middle_name, faculty) VALUES (?, ?, ?, ?) RETURNING student_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setObject(3, middleName, Types.VARCHAR);
            stmt.setInt(4, faculty);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("student_id");
            throw new SQLException("Не удалось создать студента");
        }
    }

    public static int getStudentIdByName(String fullName, Connection conn) throws SQLException {
        String[] names = fullName.split(" ");
        String query = "SELECT student_id FROM student WHERE first_name = ? AND last_name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, names[0]);
            stmt.setString(2, names[1]);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("student_id");
            }
        }

        return -1; // Студент не найден
    }
}
