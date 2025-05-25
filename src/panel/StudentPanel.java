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

import java.sql.*;

public class StudentPanel {
    private final Connection connection;
    private final int studentId;

    public StudentPanel(Connection connection, int studentId) {
        this.connection = connection;
        this.studentId = studentId;
    }

    public void start(Stage stage) {
        stage.setTitle("Личный кабинет студента");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // --- Статистика ---
        Label statsLabel = new Label("Статистика оценок");
        statsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        VBox statsBox = new VBox(10);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-border-color: #ccc; -fx-background-color: #f9f9f9;");

        try {
            ObservableList<GradeStats> stats = loadGradeStatistics(studentId);
            VBox styledStats = createStyledStatisticsDisplay(stats);
            statsBox.getChildren().add(styledStats);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки статистики: " + e.getMessage());
        }

        // --- Таблица оценок по предметам ---
        TableView<SubjectGrade> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Ошибка загрузки оценок: " + e.getMessage());
        }

        // --- Выход ---
        Button backButton = new Button("← Выйти");
        backButton.setOnAction(e -> {
            stage.close();
            Stage loginStage = new Stage();
            new form.LoginForm(connection).start(loginStage);
        });

        root.getChildren().addAll(statsLabel, statsBox, new Label("Ваши оценки:"), table, backButton);

        Scene scene = new Scene(root, 700, 500);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createStyledStatisticsDisplay(ObservableList<GradeStats> stats) {
        VBox container = new VBox(10);

        for (GradeStats stat : stats) {
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(5);
            grid.setPadding(new Insets(5));
            grid.setStyle("""
            -fx-background-color: #e9f5ff;
            -fx-border-color: #d0e4f7;
            -fx-border-radius: 5;
            -fx-background-radius: 5;
            -fx-padding: 10;
        """);

            Label subjectLabel = new Label(stat.subject);
            subjectLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            Label avgLabel = new Label(String.format("Средний балл: %.2f", stat.avgGrade));
            avgLabel.setStyle("-fx-text-fill: #333;");

            Label countLabel = new Label(String.format("Оценки — 5: %d, 4: %d, 3: %d, 2: %d",
                    stat.count5, stat.count4, stat.count3, stat.count2));
            countLabel.setStyle("-fx-text-fill: #555;");

            grid.add(subjectLabel, 0, 0);
            grid.add(avgLabel, 1, 0);
            grid.add(countLabel, 0, 1, 2, 1);

            container.getChildren().add(grid);
        }

        return container;
    }

    // Загрузка статистики оценок
    private ObservableList<SubjectGrade> loadGradesBySubject(int studentId) throws SQLException {
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

    // Подсчёт общей статистики
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

        @Override
        public String toString() {
            return String.format("%s: %.2f | 5: %d | 4: %d | 3: %d | 2: %d", subject, avgGrade, count5, count4, count3, count2);
        }
    }

    // Загрузка общей статистики по каждому предмету
    private ObservableList<GradeStats> loadGradeStatistics(int studentId) throws SQLException {
        ObservableList<GradeStats> result = FXCollections.observableArrayList();

        String sql = """
            SELECT s.subject_name,
                   AVG(g.grade) AS avg_grade,
                   SUM(CASE WHEN g.grade = 5 THEN 1 ELSE 0 END) AS count_5,
                   SUM(CASE WHEN g.grade = 4 THEN 1 ELSE 0 END) AS count_4,
                   SUM(CASE WHEN g.grade = 3 THEN 1 ELSE 0 END) AS count_3,
                   SUM(CASE WHEN g.grade = 2 THEN 1 ELSE 0 END) AS count_2
            FROM grades_semester g
            JOIN lesson_instance li ON g.lesson_id = li.lesson_id
            JOIN schedule sch ON li.schedule_id = sch.schedule_id
            JOIN subject s ON sch.subject_id = s.subject_id
            WHERE g.student_id = ?
            GROUP BY s.subject_name
            ORDER BY s.subject_name
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String subject = rs.getString("subject_name");
                double avgGrade = rs.getDouble("avg_grade");
                int count5 = rs.getInt("count_5");
                int count4 = rs.getInt("count_4");
                int count3 = rs.getInt("count_3");
                int count2 = rs.getInt("count_2");

                result.add(new GradeStats(subject, avgGrade, count5, count4, count3, count2));
            }
        }

        return result;
    }

    // Модель для таблицы
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

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}