package form;import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import panel.AdminPanel;
import panel.StudentPanel;
import panel.TeacherPanel;
import service.AuthService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginForm {
    private final Connection connection;

    public LoginForm(Connection connection) {
        this.connection = connection;
    }

    public void start(Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        TextField loginField = new TextField();
        loginField.setPromptText("Логин");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");

        Button loginButton = new Button("Войти");
        Button registerButton = new Button("Зарегистрироваться");

        loginButton.setOnAction(e -> {
            String login = loginField.getText();
            String password = passwordField.getText();

            try {
                PreparedStatement stmt = connection.prepareStatement("SELECT role, user_id FROM users WHERE login = ? AND password = ?");
                stmt.setString(1, login);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int role = rs.getInt("role");
                    int userId = rs.getInt("user_id");
                    stage.close();

                    switch (role) {
                        case 1 -> new AdminPanel(connection).start(new Stage());
                        case 2 -> {
                            int teacherId = getTeacherIdByUserId(userId);
                            if (teacherId != -1) {
                                new TeacherPanel(connection, teacherId).start(new Stage());
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Преподаватель не найден");
                            }
                        }
                        case 3 -> {
                            int studentId = getStudentIdByUserId(userId);
                            if (studentId != -1) {
                                new StudentPanel(connection, studentId).start(new Stage());
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Студент не найден");
                            }
                        }
                        default -> showAlert(Alert.AlertType.ERROR, "Неизвестная роль");
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Неверный логин или пароль");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Ошибка входа в систему");
            }
        });

        registerButton.setOnAction(e -> {
            new RegisterForm(new AuthService(connection)).start(new Stage());
        });

        root.getChildren().addAll(
                new Label("Логин:"), loginField,
                new Label("Пароль:"), passwordField,
                loginButton, registerButton
        );

        stage.setScene(new Scene(root, 300, 250));
        stage.setTitle("Вход в систему");
        stage.show();
    }

    private int getTeacherIdByUserId(int userId) throws SQLException {
        String sql = "SELECT teacher_id FROM teacher_user WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("teacher_id");
            }
        }
        return -1;
    }

    private int getStudentIdByUserId(int userId) throws SQLException {
        String sql = "SELECT student_id FROM student_user WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("student_id");
            }
        }
        return -1;
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}
