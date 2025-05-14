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
}
