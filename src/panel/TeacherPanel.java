package panel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TeacherPanel {
    private final Connection connection;
    private final int teacherId;

    // Хранит все даты занятий для текущего предмета
    private List<LessonDate> allLessons = new ArrayList<>();
    private int currentWeekIndex = 0;
    private static final int WEEKS_PER_PAGE = 4; // Отображается по 4 недели

    public TeacherPanel(Connection connection, int teacherId) {
        this.connection = connection;
        this.teacherId = teacherId;
    }

    public void start(Stage stage) {
        stage.setTitle("Панель преподавателя");

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        // Выбор предмета
        ComboBox<String> subjectBox = new ComboBox<>();
        Label selectSubjectLabel = new Label("Выберите предмет:");
        Button loadButton = new Button("Загрузить ведомость");

        updateSubjectBox(subjectBox);

        // Таблица ведомости
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        // Кнопки управления
        Button saveButton = new Button("Сохранить оценки");
        saveButton.setDisable(true);

        Button showMoreButton = new Button("→ Показать ещё 4 недели");
        showMoreButton.setDisable(true); // Сначала отключена

        // --- КНОПКА ВЫХОДА ---
        Button backButton = new Button("← Выйти");
        backButton.setOnAction(e -> {
            stage.close();
            Stage loginStage = new Stage();
            new form.LoginForm(connection).start(loginStage);
        });

        // Загрузка данных при выборе предмета
        loadButton.setOnAction(e -> {
            String selectedItem = subjectBox.getValue();
            if (selectedItem == null) {
                showAlert(Alert.AlertType.ERROR, "Выберите предмет");
                return;
            }
            int subjectId = Integer.parseInt(selectedItem.split(":")[0]);

            try {
                loadFirst4Weeks(grid, subjectId, saveButton, showMoreButton);
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка загрузки ведомости: " + ex.getMessage());
            }
        });

        // Сохранение оценок
        saveButton.setOnAction(e -> {
            try {
                saveGrades(grid);
                showAlert(Alert.AlertType.INFORMATION, "Оценки успешно сохранены");
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка сохранения оценок: " + ex.getMessage());
            }
        });

        // Переход к следующим 4 неделям
        showMoreButton.setOnAction(e -> {
            try {
                loadNext4Weeks(grid, subjectBox.getValue(), saveButton, showMoreButton);
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Ошибка загрузки следующих занятий: " + ex.getMessage());
            }
        });

        // Добавляем элементы на форму
        root.getChildren().addAll(
                selectSubjectLabel,
                subjectBox,
                loadButton,
                grid,
                saveButton,
                showMoreButton,
                backButton
        );

        stage.setScene(new Scene(root, 1200, 600));
        stage.show();
    }

    // Обновляет список предметов, доступных преподавателю
    private void updateSubjectBox(ComboBox<String> subjectBox) {
        try {
            ObservableList<String> subjects = FXCollections.observableArrayList();
            String sql = """
                SELECT s.subject_id, s.subject_name
                FROM subject s
                JOIN schedule sch ON s.subject_id = sch.subject_id
                WHERE sch.teacher_id = ?
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, teacherId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    subjects.add(rs.getInt("subject_id") + ": " + rs.getString("subject_name"));
                }
            }
            subjectBox.setItems(subjects);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки предметов: " + e.getMessage());
        }
    }

    // Загружает первые 4 недели
    private void loadFirst4Weeks(GridPane grid, int subjectId, Button saveButton, Button showMoreButton) throws SQLException {
        grid.getChildren().clear();
        currentWeekIndex = 0;
        allLessons.clear();

        // Получаем все занятия по выбранному предмету
        String lessonSql = """
            SELECT li.lesson_id, li.lesson_date
            FROM lesson_instance li
            JOIN schedule s ON li.schedule_id = s.schedule_id
            WHERE s.subject_id = ?
            ORDER BY li.lesson_date
            """;

        try (PreparedStatement stmt = connection.prepareStatement(lessonSql)) {
            stmt.setInt(1, subjectId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LessonDate ld = new LessonDate(rs.getInt("lesson_id"), rs.getDate("lesson_date").toLocalDate());
                allLessons.add(ld);
            }
        }

        displayWeeks(grid, subjectId, saveButton, showMoreButton);
        saveButton.setDisable(false);
        showMoreButton.setDisable(allLessons.size() <= WEEKS_PER_PAGE);
    }

    // Загружает следующие 4 недели
    private void loadNext4Weeks(GridPane grid, String selectedSubject, Button saveButton, Button showMoreButton) throws SQLException {
        if (currentWeekIndex >= allLessons.size()) {
            showAlert(Alert.AlertType.INFORMATION, "Больше занятий нет");
            return;
        }

        displayWeeks(grid, Integer.parseInt(selectedSubject.split(":")[0]), saveButton, showMoreButton);
    }

    // Универсальный метод отображения 4 недель
    private void displayWeeks(GridPane grid, int subjectId, Button saveButton, Button showMoreButton) throws SQLException {
        grid.getChildren().clear();

        int displayedCount = Math.min(WEEKS_PER_PAGE, allLessons.size() - currentWeekIndex);

        // Добавляем заголовки с датами
        int colIndex = 1;
        for (int i = currentWeekIndex; i < currentWeekIndex + displayedCount && i < allLessons.size(); i++) {
            LessonDate ld = allLessons.get(i);
            grid.add(new Label(ld.date.toString()), colIndex++, 0);
        }

        // Добавляем студентов и поля для оценок
        String studentSql = "SELECT student_id, first_name, last_name FROM student ORDER BY last_name";
        try (PreparedStatement stmt = connection.prepareStatement(studentSql);
             ResultSet rs = stmt.executeQuery()) {

            int rowIndex = 1;
            while (rs.next()) {
                int studentId = rs.getInt("student_id");
                String studentName = rs.getString("last_name") + " " + rs.getString("first_name");
                grid.add(new Label(studentName), 0, rowIndex);

                colIndex = 1;
                for (int i = currentWeekIndex; i < currentWeekIndex + displayedCount && i < allLessons.size(); i++) {
                    LessonDate ld = allLessons.get(i);

                    ComboBox<Integer> gradeCombo = new ComboBox<>();
                    gradeCombo.getItems().addAll(2, 3, 4, 5);
                    gradeCombo.setPromptText("Оценка");

                    // Подгружаем текущую оценку, если есть
                    String gradeSql = """
                        SELECT grade FROM grades_semester
                        WHERE student_id = ? AND lesson_id = ?
                        """;
                    try (PreparedStatement gStmt = connection.prepareStatement(gradeSql)) {
                        gStmt.setInt(1, studentId);
                        gStmt.setInt(2, ld.id);
                        ResultSet grs = gStmt.executeQuery();
                        if (grs.next()) {
                            gradeCombo.setValue(grs.getObject("grade", Integer.class));
                        }
                    }

                    gradeCombo.getProperties().put("student_id", studentId);
                    gradeCombo.getProperties().put("lesson_id", ld.id);

                    grid.add(gradeCombo, colIndex++, rowIndex);
                }
                rowIndex++;
            }
        }

        currentWeekIndex += displayedCount;

        if (showMoreButton != null) {
            showMoreButton.setDisable(currentWeekIndex >= allLessons.size());
        }
    }

    private void saveGrades(GridPane grid) throws SQLException {
        for (javafx.scene.Node node : grid.getChildren()) {
            if (node instanceof ComboBox<?>) {
                @SuppressWarnings("unchecked")
                ComboBox<Integer> combo = (ComboBox<Integer>) node;
                Object rawStudentId = combo.getProperties().get("student_id");
                Object rawLessonId = combo.getProperties().get("lesson_id");

                if (rawStudentId != null && rawLessonId != null && combo.getValue() != null) {
                    int studentId = (Integer) rawStudentId;
                    int lessonId = (Integer) rawLessonId;
                    int grade = combo.getValue();

                    // Получаем дату занятия
                    LocalDate lessonDate = getLessonDateById(lessonId);

                    String upsertSql = """
                        INSERT INTO grades_semester (student_id, lesson_id, grade, date_assigned)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (student_id, lesson_id) DO UPDATE SET
                        grade = EXCLUDED.grade, date_assigned = EXCLUDED.date_assigned
                        """;

                    try (PreparedStatement stmt = connection.prepareStatement(upsertSql)) {
                        stmt.setInt(1, studentId);
                        stmt.setInt(2, lessonId);
                        stmt.setInt(3, grade);
                        stmt.setDate(4, Date.valueOf(lessonDate));
                        stmt.executeUpdate();
                    }
                }
            }
        }
    }

    // Получаем дату занятия по lesson_id
    private LocalDate getLessonDateById(int lessonId) throws SQLException {
        String sql = "SELECT lesson_date FROM lesson_instance WHERE lesson_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, lessonId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDate("lesson_date").toLocalDate();
                }
            }
        }
        return LocalDate.now(); // Резервное значение, можно изменить на throw new RuntimeException(...)
    }

    // Модель хранения id и даты занятия
    private static class LessonDate {
        int id;
        LocalDate date;

        LessonDate(int id, LocalDate date) {
            this.id = id;
            this.date = date;
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}