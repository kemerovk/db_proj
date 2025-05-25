package panel;

import form.LoginForm;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;

public class AdminPanel {
    private final Connection connection;

    private ComboBox<String> subjectComboBoxForScheduleTab;

    public AdminPanel(Connection connection) {
        this.connection = connection;
    }

    public void start(Stage stage) {
        stage.setTitle("Панель администратора");

        TabPane tabPane = new TabPane();

        Tab addSubjectTab = new Tab("Добавить предмет", createAddSubjectPane());
        Tab scheduleSetup = new Tab("Расписание", createScheduleSetupPane());

        addSubjectTab.setClosable(false);
        scheduleSetup.setClosable(false);

        tabPane.getTabs().addAll(addSubjectTab, scheduleSetup);

        Button backButton = new Button("← Вернуться к входу");
        backButton.setOnAction(e -> {
            stage.close();
            Stage loginStage = new Stage();
            new LoginForm(connection).start(loginStage);
        });

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);

        HBox bottomPane = new HBox(backButton);
        bottomPane.setPadding(new Insets(10));
        bottomPane.setAlignment(Pos.CENTER_RIGHT);

        root.setBottom(bottomPane);

        Scene scene = new Scene(root, 600, 450);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createAddSubjectPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20));

        TextField subjectNameField = new TextField();
        subjectNameField.setPromptText("Название предмета");

        Button addButton = new Button("Добавить предмет");
        addButton.setOnAction(e -> {
            String name = subjectNameField.getText();
            if (name.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Введите название предмета");
                return;
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO subject(subject_name) VALUES (?)")) {
                stmt.setString(1, name);
                stmt.executeUpdate();
                showAlert(Alert.AlertType.INFORMATION, "Предмет успешно добавлен");
                subjectNameField.clear();

                // Обновляем все ComboBox с предметами
                refreshAllSubjectBoxes();

            } catch (SQLException ex) {
                if (ex.getMessage().toLowerCase().contains("unique")) {
                    showAlert(Alert.AlertType.ERROR, "Такой предмет уже существует");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Ошибка при добавлении предмета: " + ex.getMessage());
                }
            }
        });

        box.getChildren().addAll(new Label("Название предмета:"), subjectNameField, addButton);
        return box;
    }

    private VBox createScheduleSetupPane() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));

        ComboBox<String> teacherBox = new ComboBox<>();
        ComboBox<String> subjectBox = new ComboBox<>();
        ComboBox<Integer> dayOfWeekBox = new ComboBox<>();
        ComboBox<Integer> pairNumberBox = new ComboBox<>();

        Button addButton = new Button("Добавить расписание");

        dayOfWeekBox.getItems().addAll(1, 2, 3, 4, 5, 6);
        pairNumberBox.getItems().addAll(1, 2, 3, 4, 5, 6);

        updateTeacherAndSubjectBoxes(teacherBox, subjectBox);
        this.subjectComboBoxForScheduleTab = subjectBox;

        HBox fieldsBox = new HBox(15);
        fieldsBox.getChildren().addAll(
                new VBox(new Label("Преподаватель"), teacherBox),
                new VBox(new Label("Предмет"), subjectBox),
                new VBox(new Label("День недели (1–6)"), dayOfWeekBox),
                new VBox(new Label("Номер пары (1–6)"), pairNumberBox)
        );

        addButton.setOnAction(e -> {
            String teacherItem = teacherBox.getValue();
            String subjectItem = subjectBox.getValue();
            Integer dayOfWeek = dayOfWeekBox.getValue();
            Integer pairNumber = pairNumberBox.getValue();

            if (teacherItem == null || subjectItem == null || dayOfWeek == null || pairNumber == null) {
                showAlert(Alert.AlertType.ERROR, "Заполните все поля");
                return;
            }

            int teacherId = Integer.parseInt(teacherItem.split(":")[0]);
            int subjectId = Integer.parseInt(subjectItem.split(":")[0]);

            try {
                insertScheduleAndGenerateLessons(teacherId, subjectId, dayOfWeek, pairNumber);
                showAlert(Alert.AlertType.INFORMATION, "Расписание и занятия успешно добавлены");
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка при добавлении расписания: " + ex.getMessage());
            }
        });

        box.getChildren().addAll(fieldsBox, addButton);
        return box;
    }

    private void insertScheduleAndGenerateLessons(int teacherId, int subjectId, int dayOfWeek, int pairNumber) throws SQLException {
        connection.setAutoCommit(false); // Начинаем транзакцию

        try {
            String scheduleSQL = "INSERT INTO schedule(subject_id, teacher_id, day_of_week, pair_number) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(scheduleSQL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, subjectId);
                stmt.setInt(2, teacherId);
                stmt.setInt(3, dayOfWeek);
                stmt.setInt(4, pairNumber);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected == 0) {
                    throw new SQLException("Не удалось создать расписание");
                }

                ResultSet rs = stmt.getGeneratedKeys();
                int scheduleId = -1;
                if (rs.next()) {
                    scheduleId = rs.getInt(1);
                } else {
                    throw new SQLException("Не удалось получить ID расписания");
                }

                generateLessonInstances(scheduleId, dayOfWeek, pairNumber);

            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();

            // Проверяем, была ли ошибка из-за нарушения уникальности
            if (e.getMessage().contains("UNIQUE") || e.getSQLState().equals("23505")) {
                showAlert(Alert.AlertType.ERROR, "На этот день и пару уже есть расписание");
            } else {
                showAlert(Alert.AlertType.ERROR, "Ошибка при добавлении расписания: " + e.getMessage());
            }

            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void generateLessonInstances(int scheduleId, int dayOfWeek, int pairNumber) throws SQLException {
        LocalDate startDate = LocalDate.now().with(DayOfWeek.of(dayOfWeek)); // текущая неделя
        if (startDate.isBefore(LocalDate.now())) {
            startDate = startDate.plusWeeks(1); // если уже прошла — берем следующую неделю
        }

        for (int week = 0; week < 16; week++) {
            LocalDate lessonDate = startDate.plusWeeks(week);
            String sql = "INSERT INTO lesson_instance(schedule_id, lesson_date) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, scheduleId);
                stmt.setDate(2, Date.valueOf(lessonDate));
                stmt.executeUpdate();
            }
        }
    }

    // ------------------- Обновление списков -------------------
    private void updateTeacherAndSubjectBoxes(ComboBox<String> teacherBox, ComboBox<String> subjectBox) {
        try {
            // Обновление списка преподавателей
            ObservableList<String> teachers = FXCollections.observableArrayList();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT teacher_id, first_name, last_name FROM teacher")) {
                while (rs.next()) {
                    teachers.add(rs.getInt("teacher_id") + ": " +
                            rs.getString("first_name") + " " +
                            rs.getString("last_name"));
                }
                teacherBox.setItems(teachers);
            }

            updateSubjectBox(subjectBox);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки данных: " + e.getMessage());
        }
    }

    private void updateSubjectBox(ComboBox<String> subjectBox) {
        try {
            ObservableList<String> subjects = FXCollections.observableArrayList();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT subject_id, subject_name FROM subject")) {
                while (rs.next()) {
                    subjects.add(rs.getInt("subject_id") + ": " + rs.getString("subject_name"));
                }
                subjectBox.setItems(subjects);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки предметов: " + e.getMessage());
        }
    }

    private void refreshAllSubjectBoxes() {
        if (subjectComboBoxForScheduleTab != null) {
            updateSubjectBox(subjectComboBoxForScheduleTab);
        }
    }

    // ------------------- Вспомогательные методы -------------------
    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}