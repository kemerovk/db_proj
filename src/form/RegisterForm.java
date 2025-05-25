package form;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import service.AuthService;

import java.sql.SQLException;

public class RegisterForm {
    private final AuthService authService;

    public RegisterForm(AuthService authService) {
        this.authService = authService;
    }

    public void start(Stage stage) {
        stage.setTitle("Регистрация");

        // Выбор роли
        Label roleLabel = new Label("Выберите роль:");
        ComboBox<String> roleComboBox = new ComboBox<>();
        roleComboBox.getItems().addAll("Студент", "Преподаватель");

        // Ввод логина и пароля
        Label loginLabel = new Label("Логин:");
        TextField loginField = new TextField();

        Label passwordLabel = new Label("Пароль:");
        PasswordField passwordField = new PasswordField();

        // Ввод имени и фамилии
        Label firstNameLabel = new Label("Имя:");
        TextField firstNameField = new TextField();

        Label lastNameLabel = new Label("Фамилия:");
        TextField lastNameField = new TextField();

        Label middleNameLabel = new Label("Отчество (необязательно):");
        TextField middleNameField = new TextField();

        Button registerButton = new Button("Зарегистрироваться");

        registerButton.setOnAction(e -> {
            String role = roleComboBox.getValue();
            String login = loginField.getText();
            String password = passwordField.getText();
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String middleName = middleNameField.getText().isEmpty() ? null : middleNameField.getText();

            if (role == null || login.isEmpty() || password.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Пожалуйста, заполните все обязательные поля.");
                return;
            }

            try {
                boolean success = false;
                if (role.equals("Студент")) {
                    success = authService.registerStudent(login, password, firstName, lastName, middleName);
                } else if (role.equals("Преподаватель")) {
                    success = authService.registerTeacher(login, password, firstName, lastName, middleName);
                }

                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "Регистрация прошла успешно!");
                    stage.close();
                }

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });

        VBox layout = new VBox(10,
                roleLabel, roleComboBox,
                loginLabel, loginField,
                passwordLabel, passwordField,
                firstNameLabel, firstNameField,
                lastNameLabel, lastNameField,
                middleNameLabel, middleNameField,
                registerButton
        );
        layout.setPadding(new Insets(20));

        stage.setScene(new Scene(layout, 350, 450));
        stage.show();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}