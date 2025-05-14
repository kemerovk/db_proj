package scene;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static scene.UsersScene.*;
import static service.Group.*;
import static service.Student.createStudent;
import static service.Teacher.createTeacher;
import static service.User.*;

public class AuthScene {
    public static void showLoginScene(Stage primaryStage, Connection conn) {
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
                int role = getUserRole(login, password, conn);
                if (role > 0) {
                    loginMessage.setText("Вход успешен!");
                    switch (role) {
                        case 1 -> showAdminSceneWithGroupCreation(primaryStage, conn);
                        case 2 -> showTeacherScene(primaryStage);
                        case 3 -> showStudentScene(primaryStage);
                        default -> loginMessage.setText("Неизвестная роль.");
                    }
                } else {
                    loginMessage.setText("Неверный логин или пароль.");
                }
            } catch (SQLException ex) {
                loginMessage.setText("Ошибка входа: " + ex.getMessage());
            }
        });

        switchToRegister.setOnAction(e -> showRegistrationScene(primaryStage, conn));

        loginBox.getChildren().addAll(loginLabel, loginField, passwordField, loginButton, loginMessage, switchToRegister);
        primaryStage.setScene(new Scene(loginBox, 400, 300));
    }

    public static void showRegistrationScene(Stage primaryStage, Connection conn) {
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

        ComboBox<FacultyItem> facultyBox = new ComboBox<>();
        facultyBox.setPromptText("Направление (факультет)");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT faculty_id, faculty_name FROM faculty")) {
            while (rs.next()) {
                facultyBox.getItems().add(new FacultyItem(rs.getInt("faculty_id"), rs.getString("faculty_name")));
            }
        } catch (SQLException ex) {
            System.err.println("Ошибка загрузки направлений: " + ex.getMessage());
        }

        Button registerButton = new Button("Зарегистрироваться");
        Button backToLoginButton = new Button("Назад ко входу");
        Label regMessage = new Label();

        // Показ/скрытие поля "Направление" по роли
        roleBox.setOnAction(e -> {
            String selected = roleBox.getValue();
            registerBox.getChildren().remove(facultyBox);
            if ("Студент".equals(selected)) {
                if (!registerBox.getChildren().contains(facultyBox)) {
                    registerBox.getChildren().add(registerBox.getChildren().size() - 3, facultyBox);
                }
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
                int userId = createUser(login, password, role, conn);

                if (role == 2) {
                    int teacherId = createTeacher(firstName, lastName, middleName, conn);
                    linkTeacherToUser(userId, teacherId, conn);
                } else if (role == 3) {
                    FacultyItem selectedFaculty = facultyBox.getValue();
                    if (selectedFaculty == null) {
                        regMessage.setText("Выберите направление.");
                        return;
                    }

                    int studentId = createStudent(firstName, lastName, middleName, selectedFaculty.id, conn);
                    linkStudentToUser(userId, studentId, conn);

                }

                regMessage.setText("Регистрация успешна!");
            } catch (SQLException ex) {
                regMessage.setText("Ошибка регистрации: " + ex.getMessage());
            }
        });

        backToLoginButton.setOnAction(e -> showLoginScene(primaryStage, conn));

        registerBox.getChildren().addAll(welcomeLabel, loginField, passwordField, roleBox,
                firstNameField, lastNameField, middleNameField, registerButton, regMessage, backToLoginButton);

        primaryStage.setScene(new Scene(registerBox, 400, 500));
    }
    private record FacultyItem(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static void showAdminSceneWithGroupCreation(Stage primaryStage, Connection conn) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label title = new Label("Создание новой группы");

        ComboBox<String> facultyBox = new ComboBox<>();
        Map<String, Integer> facultyMap = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT faculty_id, faculty_name FROM faculty")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("faculty_id");
                String name = rs.getString("faculty_name");
                facultyBox.getItems().add(name);
                facultyMap.put(name, id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Label nextGroupNumberLabel = new Label("Номер следующей группы: ");

        Button createGroupButton = new Button("Создать группу");
        Label createStatus = new Label();

        facultyBox.setOnAction(e -> {
            String facultyName = facultyBox.getValue();
            if (facultyName != null) {
                int facultyId = facultyMap.get(facultyName);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT MAX(group_number) AS max_number FROM \"group\" WHERE faculty = ?")) {
                    stmt.setInt(1, facultyId);
                    ResultSet rs = stmt.executeQuery();
                    int nextNumber = 1;
                    if (rs.next()) {
                        nextNumber = rs.getInt("max_number") + 1;
                    }
                    nextGroupNumberLabel.setText("Номер следующей группы: " + nextNumber);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });

        createGroupButton.setOnAction(e -> {
            String facultyName = facultyBox.getValue();
            if (facultyName == null) {
                createStatus.setText("Выберите факультет.");
                return;
            }

            int facultyId = facultyMap.get(facultyName);
            int nextNumber = 1;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT MAX(group_number) AS max_number FROM \"group\" WHERE faculty = ?")) {
                stmt.setInt(1, facultyId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    nextNumber = rs.getInt("max_number") + 1;
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO \"group\" (faculty, group_number) VALUES (?, ?)")) {
                stmt.setInt(1, facultyId);
                stmt.setInt(2, nextNumber);
                stmt.executeUpdate();
                createStatus.setText("Группа " + nextNumber + " создана.");
                facultyBox.fireEvent(new ActionEvent()); // обновить nextGroupNumberLabel
                updateStudentAssignmentSection(root, conn); // обновить студентов
            } catch (SQLException ex) {
                createStatus.setText("Ошибка создания группы: " + ex.getMessage());
            }
        });

        // Верхняя часть
        VBox createGroupBox = new VBox(10,
                title, facultyBox, nextGroupNumberLabel, createGroupButton, createStatus);
        createGroupBox.setPadding(new Insets(0, 0, 10, 0));

        root.getChildren().add(createGroupBox);

        // Нижняя часть – назначение студентов
        updateStudentAssignmentSection(root, conn);

        primaryStage.setScene(new Scene(root, 600, 700));
    }

}
