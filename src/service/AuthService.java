package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {
    private final Connection connection;

    public AuthService(Connection connection) {
        this.connection = connection;
    }

    public boolean login(String login, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE login = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // вход успешен, если пользователь найден
        }
    }

    public boolean registerStudent(String login, String password,
                                   String firstName, String lastName, String middleName) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int userId;

            // 1. Создаем пользователя
            String userSql = "INSERT INTO users(login, password, role) VALUES (?, ?, 3) RETURNING user_id";
            try (PreparedStatement stmt = connection.prepareStatement(userSql)) {
                stmt.setString(1, login);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                } else {
                    connection.rollback();
                    return false;
                }
            }

            // 2. Создаем студента
            String studentSql = "INSERT INTO student(first_name, last_name, middle_name) VALUES (?, ?, ?) RETURNING student_id";
            int studentId;
            try (PreparedStatement stmt = connection.prepareStatement(studentSql)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, middleName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    studentId = rs.getInt("student_id");
                } else {
                    connection.rollback();
                    return false;
                }
            }

            // 3. Связь user <-> student
            String linkSql = "INSERT INTO student_user(user_id, student_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(linkSql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, studentId);
                stmt.executeUpdate();
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            connection.rollback();

            String message = e.getMessage().toLowerCase();
            if (message.contains("users_login_key")) {
                throw new SQLException("Пользователь с таким логином уже существует.");
            } else if (message.contains("student_first_name_last_name_middle_name_key")) {
                throw new SQLException("Студент с таким ФИО уже существует.");
            } else {
                throw e;
            }

        } finally {
            connection.setAutoCommit(true);
        }
    }
    public boolean registerTeacher(String login, String password,
                                   String firstName, String lastName, String middleName) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int userId;

            // 1. Создаем пользователя
            String userSql = "INSERT INTO users(login, password, role) VALUES (?, ?, 2) RETURNING user_id";
            try (PreparedStatement stmt = connection.prepareStatement(userSql)) {
                stmt.setString(1, login);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                } else {
                    connection.rollback();
                    return false;
                }
            }

            // 2. Создаем преподавателя
            String teacherSql = "INSERT INTO teacher(first_name, last_name, middle_name) VALUES (?, ?, ?) RETURNING teacher_id";
            int teacherId;
            try (PreparedStatement stmt = connection.prepareStatement(teacherSql)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, middleName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    teacherId = rs.getInt("teacher_id");
                } else {
                    connection.rollback();
                    return false;
                }
            }

            // 3. Связь user <-> teacher
            String linkSql = "INSERT INTO teacher_user(user_id, teacher_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(linkSql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, teacherId);
                stmt.executeUpdate();
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            connection.rollback();

            String message = e.getMessage().toLowerCase();
            if (message.contains("users_login_key")) {
                throw new SQLException("Пользователь с таким логином уже существует.");
            } else if (message.contains("teacher_first_name_last_name_middle_name_key")) {
                throw new SQLException("Преподаватель с таким ФИО уже существует.");
            } else {
                throw e;
            }

        } finally {
            connection.setAutoCommit(true);
        }
    }
}