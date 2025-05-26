package panel;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

public class StudentPanel {
    private final Connection connection;
    private final int studentId;

    public StudentPanel(Connection connection, int studentId) {
        this.connection = connection;
        this.studentId = studentId;
    }

    public void start(Stage stage) {
        stage.setTitle("Личный кабинет студента");

        TabPane tabPane = new TabPane();

        // --- Вкладка "Расписание" ---
        Tab scheduleTab = new Tab("Расписание", createScheduleView());
        scheduleTab.setClosable(false);

        // --- Вкладка "Оценки" ---
        Tab gradesTab = new Tab("Оценки", createGradesView());
        gradesTab.setClosable(false);

        tabPane.getTabs().addAll(scheduleTab, gradesTab);

        // --- Выход ---
        Button backButton = new Button("← Выйти");
        backButton.setOnAction(e -> {
            stage.close();
            Stage loginStage = new Stage();
            new form.LoginForm(connection).start(loginStage);
        });

        VBox root = new VBox(15, tabPane, backButton);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    // ================== РАСПИСАНИЕ ==================

    private VBox createScheduleView() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        Label title = new Label("Ваше расписание на текущий семестр");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        box.getChildren().add(title);

        TableView<ScheduleItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ScheduleItem, String> subjectCol = new TableColumn<>("Предмет");
        subjectCol.setCellValueFactory(data -> data.getValue().subjectProperty());

        TableColumn<ScheduleItem, String> teacherCol = new TableColumn<>("Преподаватель");
        teacherCol.setCellValueFactory(data -> data.getValue().teacherProperty());

        TableColumn<ScheduleItem, Integer> dayCol = new TableColumn<>("День недели");
        dayCol.setCellValueFactory(data -> data.getValue().dayProperty().asObject());
        dayCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DayOfWeek day = DayOfWeek.of(item);
                    String dayRu = day.getDisplayName(TextStyle.FULL, new Locale("ru", "RU"));
                    setText(dayRu);
                }
            }
        });

        TableColumn<ScheduleItem, Integer> pairCol = new TableColumn<>("Номер пары");
        pairCol.setCellValueFactory(data -> data.getValue().pairProperty().asObject());

        table.getColumns().addAll(subjectCol, teacherCol, dayCol, pairCol);

        try {
            ObservableList<ScheduleItem> scheduleItems = loadSchedule(studentId);
            table.setItems(scheduleItems);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки расписания: " + e.getMessage());
        }

        box.getChildren().add(table);
        return box;
    }

    private static class ScheduleItem {
        private final SimpleStringProperty subject = new SimpleStringProperty();
        private final SimpleStringProperty teacher = new SimpleStringProperty();
        private final SimpleIntegerProperty day = new SimpleIntegerProperty();
        private final SimpleIntegerProperty pair = new SimpleIntegerProperty();

        public ScheduleItem(String subject, String teacher, int day, int pair) {
            setSubject(subject);
            setTeacher(teacher);
            setDay(day);
            setPair(pair);
        }

        public String getSubject() { return subject.get(); }
        public SimpleStringProperty subjectProperty() { return subject; }
        public void setSubject(String subject) { this.subject.set(subject); }

        public String getTeacher() { return teacher.get(); }
        public SimpleStringProperty teacherProperty() { return teacher; }
        public void setTeacher(String teacher) { this.teacher.set(teacher); }

        public int getDay() { return day.get(); }
        public SimpleIntegerProperty dayProperty() { return day; }
        public void setDay(int day) { this.day.set(day); }

        public int getPair() { return pair.get(); }
        public SimpleIntegerProperty pairProperty() { return pair; }
        public void setPair(int pair) { this.pair.set(pair); }
    }

    private ObservableList<ScheduleItem> loadSchedule(int studentId) throws SQLException {
        ObservableList<ScheduleItem> result = FXCollections.observableArrayList();

        String sql = """
            SELECT DISTINCT s.subject_name, t.first_name || ' ' || t.last_name AS teacher_name,
                   sch.day_of_week, sch.pair_number
            FROM schedule sch
            JOIN lesson_instance li ON sch.schedule_id = li.schedule_id
            JOIN grades_semester g ON li.lesson_id = g.lesson_id AND g.student_id = ?
            JOIN subject s ON sch.subject_id = s.subject_id
            JOIN teacher t ON sch.teacher_id = t.teacher_id
            ORDER BY sch.day_of_week, sch.pair_number
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String subject = rs.getString("subject_name");
                String teacher = rs.getString("teacher_name");
                int day = rs.getInt("day_of_week");
                int pair = rs.getInt("pair_number");
                result.add(new ScheduleItem(subject, teacher, day, pair));
            }
        }

        return result;
    }

    // ================== ОЦЕНКИ ==================

    private VBox createGradesView() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        Label title = new Label("Ваши оценки");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        box.getChildren().add(title);

        ComboBox<String> subjectComboBox = new ComboBox<>();
        subjectComboBox.setPromptText("Выберите предмет");

        try {
            ObservableList<String> subjects = FXCollections.observableArrayList();
            String sqlSubjects = """
                SELECT DISTINCT s.subject_name
                FROM grades_semester g
                JOIN lesson_instance li ON g.lesson_id = li.lesson_id
                JOIN schedule sch ON li.schedule_id = sch.schedule_id
                JOIN subject s ON sch.subject_id = s.subject_id
                WHERE g.student_id = ?
                ORDER BY s.subject_name
            """;
            try (PreparedStatement stmt = connection.prepareStatement(sqlSubjects)) {
                stmt.setInt(1, studentId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    subjects.add(rs.getString("subject_name"));
                }
            }
            subjectComboBox.setItems(subjects);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки предметов: " + e.getMessage());
        }

        // --- Таблица оценок ---
        TableView<SubjectGrade> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<SubjectGrade, String> subjectCol = new TableColumn<>("Предмет");
        subjectCol.setCellValueFactory(cellData -> cellData.getValue().subjectProperty());

        TableColumn<SubjectGrade, Integer> gradeCol = new TableColumn<>("Оценка");
        gradeCol.setCellValueFactory(cellData -> cellData.getValue().gradeProperty().asObject());

        TableColumn<SubjectGrade, String> dateCol = new TableColumn<>("Дата");
        dateCol.setCellValueFactory(cellData -> cellData.getValue().dateProperty());




        table.getColumns().addAll(subjectCol, gradeCol, dateCol);

        try {
            ObservableList<SubjectGrade> grades = loadGradesBySubject(studentId);
            table.setItems(grades);

            subjectComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null) {
                    table.setItems(grades);
                } else {
                    ObservableList<SubjectGrade> filtered = grades.filtered(g -> g.getSubject().equals(newVal));
                    table.setItems(filtered);
                }
            });
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки оценок: " + e.getMessage());
        }

        box.getChildren().addAll(new Label("Фильтр по предмету:"), subjectComboBox, table);
        return box;
    }

    // Загрузка оценок
    public ObservableList<SubjectGrade> loadGradesBySubject(int studentId) throws SQLException {
        ObservableList<SubjectGrade> result = FXCollections.observableArrayList();

        String sql = """
            SELECT s.subject_name, g.grade, g.date_assigned
            FROM grades_semester g
            JOIN lesson_instance li ON g.lesson_id = li.lesson_id
            JOIN schedule sch ON li.schedule_id = sch.schedule_id
            JOIN subject s ON sch.subject_id = s.subject_id
            WHERE g.student_id = ?
            ORDER BY s.subject_name, g.date_assigned
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String subject = rs.getString("subject_name");
                int grade = rs.getInt("grade");
                String date = rs.getDate("date_assigned").toString();
                result.add(new SubjectGrade(subject, grade, date));
            }
        }

        return result;
    }

    // Модель для оценок
    public static class SubjectGrade {
        private final SimpleStringProperty subject = new SimpleStringProperty();
        private final SimpleIntegerProperty grade = new SimpleIntegerProperty();
        private final SimpleStringProperty date = new SimpleStringProperty();

        public SubjectGrade(String subject, int grade, String date) {
            setSubject(subject);
            setGrade(grade);
            setDate(date);
        }

        public String getSubject() { return subject.get(); }
        public SimpleStringProperty subjectProperty() { return subject; }
        public void setSubject(String subject) { this.subject.set(subject); }

        public int getGrade() { return grade.get(); }
        public SimpleIntegerProperty gradeProperty() { return grade; }
        public void setGrade(int grade) { this.grade.set(grade); }

        public String getDate() { return date.get(); }
        public SimpleStringProperty dateProperty() { return date; }
        public void setDate(String date) { this.date.set(date); }
    }

    // Статистика оценок (уже была)
    private static class GradeStats {
        String subject;
        double avgGrade;
        int count5, count4, count3, count2;

        public GradeStats(String subject, double avgGrade, int count5, int count4, int count3, int count2) {
            this.subject = subject;
            this.avgGrade = avgGrade;
            this.count5 = count5;
            this.count4 = count4;
            this.count3 = count3;
            this.count2 = count2;
        }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}