import config.DatabaseConnection;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.*;

public class DatabaseProject extends Application {

    private final Connection connection = DatabaseConnection.getConnection();
    private Stage primaryStage;
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Вход в систему");
        showLoginScene();
        primaryStage.show();
    }

    private void showLoginScene() {
        VBox loginBox = new VBox(10);
        loginBox.setPadding(new Insets(20));

        Label loginLabel = new Label("Вход");
        TextField loginField = new TextField();
        loginField.setPromptText("Логин");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");

        Button loginButton = new Button("Войти");
        Button switchToRegister = new Button("Зарегистрироваться");
        Label loginMessage = new Label();

        loginButton.setOnAction(e -> {
            String login = loginField.getText().trim();
            String password = passwordField.getText().trim();

            if (login.isEmpty() || password.isEmpty()) {
                loginMessage.setText("Заполните все поля.");
                return;
            }

            try {
                if (validateUser(login, password)) {
                    loginMessage.setText("Вход успешен!");
                } else {
                    loginMessage.setText("Неверный логин или пароль.");
                }
            } catch (SQLException ex) {
                loginMessage.setText("Ошибка входа: " + ex.getMessage());
            }
        });

        switchToRegister.setOnAction(e -> showRegistrationScene());

        loginBox.getChildren().addAll(loginLabel, loginField, passwordField, loginButton, loginMessage, switchToRegister);
        primaryStage.setScene(new Scene(loginBox, 400, 300));
    }

    private void showRegistrationScene() {
        VBox registerBox = new VBox(10);
        registerBox.setPadding(new Insets(20));

        Label welcomeLabel = new Label("Регистрация");
        TextField loginField = new TextField();
        loginField.setPromptText("Логин");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("Преподаватель", "Студент");
        roleBox.setPromptText("Роль");

        TextField firstNameField = new TextField();
        firstNameField.setPromptText("Имя");

        TextField lastNameField = new TextField();
        lastNameField.setPromptText("Фамилия");

        TextField middleNameField = new TextField();
        middleNameField.setPromptText("Отчество (необязательно)");

        ComboBox<DomainItem> domainBox = new ComboBox<>();
        domainBox.setPromptText("Предметная область");

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT domain_id, domain_name FROM academic_domain")) {
            while (rs.next()) {
                domainBox.getItems().add(new DomainItem(rs.getInt("domain_id"), rs.getString("domain_name")));
            }
        } catch (SQLException ex) {
            System.err.println("Ошибка загрузки предметных областей: " + ex.getMessage());
        }

        Button registerButton = new Button("Зарегистрироваться");
        Button backToLoginButton = new Button("Назад ко входу");
        Label regMessage = new Label();

        roleBox.setOnAction(e -> {
            String selected = roleBox.getValue();
            if ("Преподаватель".equals(selected)) {
                if (!registerBox.getChildren().contains(domainBox)) {
                    registerBox.getChildren().add(registerBox.getChildren().size() - 3, domainBox);
                }
            } else {
                registerBox.getChildren().remove(domainBox);
            }
        });

        registerButton.setOnAction(e -> {
            String login = loginField.getText().trim();
            String password = passwordField.getText();
            String roleText = roleBox.getValue();
            String firstName = firstNameField.getText().trim();
            String lastName = lastNameField.getText().trim();
            String middleName = middleNameField.getText().trim().isEmpty() ? null : middleNameField.getText().trim();

            if (login.isEmpty() || password.isEmpty() || roleText == null || firstName.isEmpty() || lastName.isEmpty()) {
                regMessage.setText("Заполните все обязательные поля.");
                return;
            }

            int role = switch (roleText) {
                case "Преподаватель" -> 2;
                case "Студент" -> 3;
                default -> -1;
            };

            try {
                int userId = createUser(login, password, role);

                if (role == 2) {
                    DomainItem selectedDomain = domainBox.getValue();
                    if (selectedDomain == null) {
                        regMessage.setText("Выберите предметную область.");
                        return;
                    }
                    int domainId = selectedDomain.id();
                    int teacherId = createTeacher(firstName, lastName, middleName, domainId);
                    linkTeacherToUser(userId, teacherId);
                } else if (role == 3) {
                    int studentId = createStudent(firstName, lastName, middleName);
                    linkStudentToUser(userId, studentId);
                }

                regMessage.setText("Регистрация успешна!");
            } catch (SQLException ex) {
                regMessage.setText("Ошибка регистрации: " + ex.getMessage());
            }
        });

        backToLoginButton.setOnAction(e -> showLoginScene());

        registerBox.getChildren().addAll(welcomeLabel, loginField, passwordField, roleBox,
                firstNameField, lastNameField, middleNameField, registerButton, regMessage, backToLoginButton);

        primaryStage.setScene(new Scene(registerBox, 400, 500));
    }

    private boolean validateUser(String login, String password) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE login = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, login);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private int createUser(String login, String password, int role) throws SQLException {
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

    private int createTeacher(String firstName, String lastName, String middleName, int domainId) throws SQLException {
        String sql = "INSERT INTO teacher (first_name, last_name, middle_name, academic_domain) VALUES (?, ?, ?, ?) RETURNING teacher_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setObject(3, middleName, Types.VARCHAR);
            stmt.setInt(4, domainId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("teacher_id");
            throw new SQLException("Не удалось создать преподавателя");
        }
    }

    private int createStudent(String firstName, String lastName, String middleName) throws SQLException {
        String sql = "INSERT INTO student (first_name, last_name, middle_name) VALUES (?, ?, ?) RETURNING student_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setObject(3, middleName, Types.VARCHAR);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("student_id");
            throw new SQLException("Не удалось создать студента");
        }
    }

    private void linkStudentToUser(int userId, int studentId) throws SQLException {
        String sql = "INSERT INTO student_user (user_id, student_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, studentId);
            stmt.executeUpdate();
        }
    }

    private void linkTeacherToUser(int userId, int teacherId) throws SQLException {
        String sql = "INSERT INTO teacher_user (user_id, teacher_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, teacherId);
            stmt.executeUpdate();
        }
    }

    private record DomainItem(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
    public static void main(String[] args) {
        launch(args);
    }
}
