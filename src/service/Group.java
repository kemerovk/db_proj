package service;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import scene.AuthScene;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Group {
    public static int getNextGroupNumber(int facultyId, Connection conn) throws SQLException {
    String query = "SELECT COALESCE(MAX(group_number), 0) + 1 AS next_group FROM \"group\" WHERE faculty = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setInt(1, facultyId);
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_group");
            }
        }
    }
    return 1; // по умолчанию
}

    public static int createGroup(int facultyId, int groupNumber, Connection conn) throws SQLException {
        String sql = "INSERT INTO \"group\" (faculty, group_number) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, facultyId);
            stmt.setInt(2, groupNumber);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Не удалось создать группу.");
    }

    public static void assignStudentToGroup(int studentId, int groupId, Connection conn) throws SQLException {
        String sql = "UPDATE student SET group_id = ? WHERE student_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, studentId);
            stmt.executeUpdate();
        }
    }

    public static void updateStudentAssignmentSection(VBox root, Connection conn) {
        ObservableList<Node> children = root.getChildren();
        children.removeIf(node -> node.getUserData() != null && node.getUserData().equals("assignSection"));

        VBox assignSection = new VBox(10);
        assignSection.setUserData("assignSection"); // помечаем для последующего удаления

        Label assignTitle = new Label("Назначение студентов в группы");
        VBox studentsBox = new VBox(5);

        Map<Integer, ComboBox<String>> studentGroupBoxes = new HashMap<>();
        Map<String, Integer> groupNameToIdMap = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT s.student_id, s.first_name, s.last_name, s.middle_name, s.faculty
            FROM student s
            WHERE s.group_id IS NULL
    """)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int studentId = rs.getInt("student_id");
                String fullName = rs.getString("last_name") + " " + rs.getString("first_name") +
                        (rs.getString("middle_name") != null ? " " + rs.getString("middle_name") : "");
                int facultyId = rs.getInt("faculty");

                HBox row = new HBox(10);
                Label nameLabel = new Label(fullName);

                ComboBox<String> groupBox = new ComboBox<>();
                try (PreparedStatement gstmt = conn.prepareStatement("""
                    SELECT g.group_id, g.group_number, f.faculty_name
                    FROM "group" g
                    JOIN faculty f ON g.faculty = f.faculty_id
                    WHERE g.faculty = ?
                """)) {
                    gstmt.setInt(1, facultyId);
                    ResultSet grs = gstmt.executeQuery();
                    while (grs.next()) {
                        int gid = grs.getInt("group_id");
                        int gnum = grs.getInt("group_number");
                        String facultyName = grs.getString("faculty_name");
                        String gName = facultyName + " " + gnum;
                        groupBox.getItems().add(gName);
                        groupNameToIdMap.put(gName, gid);
                    }
                }

                studentGroupBoxes.put(studentId, groupBox);
                row.getChildren().addAll(nameLabel, groupBox);
                studentsBox.getChildren().add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        assignSection.getChildren().add(assignTitle);

        // Добавляем список студентов, если он не пуст
        if (!studentGroupBoxes.isEmpty()) {
            assignSection.getChildren().add(studentsBox);

            Button assignButton = new Button("Назначить студентов в выбранные группы");
            Label assignStatus = new Label();

            assignButton.setOnAction(e -> {
                for (Map.Entry<Integer, ComboBox<String>> entry : studentGroupBoxes.entrySet()) {
                    int studentId = entry.getKey();
                    String groupName = entry.getValue().getValue();
                    if (groupName != null) {
                        int groupId = groupNameToIdMap.get(groupName);
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE student SET group_id = ? WHERE student_id = ?")) {
                            stmt.setInt(1, groupId);
                            stmt.setInt(2, studentId);
                            stmt.executeUpdate();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                assignStatus.setText("Студенты назначены в выбранные группы.");
                updateStudentAssignmentSection(root, conn);
            });

            assignSection.getChildren().addAll(assignButton, assignStatus);
        }
        Button logoutButton = new Button("Выйти");
        logoutButton.setOnAction(e -> {
            Node source = (Node) e.getSource();
            Stage stage = (Stage) source.getScene().getWindow();
            AuthScene.showLoginScene(stage, conn); // вызываем метод показа сцены входа
        });

        assignSection.getChildren().add(logoutButton);

        root.getChildren().add(assignSection);
    }

    public static void updateTeacherAssignmentSection(VBox root, Connection conn) {
        // Удаляем предыдущую секцию, если она была
        ObservableList<Node> children = root.getChildren();
        children.removeIf(node -> node.getUserData() != null && node.getUserData().equals("assignTeacherSection"));

        VBox assignSection = new VBox(10);
        assignSection.setUserData("assignTeacherSection");

        Label title = new Label("Назначение преподавателей на предметы");

        VBox teacherBox = new VBox(5);
        Map<Integer, ComboBox<String>> teacherSubjectBoxes = new HashMap<>();
        Map<String, Integer> subjectNameToIdMap = new HashMap<>();

        try (PreparedStatement teacherStmt = conn.prepareStatement("SELECT teacher_id, first_name, last_name FROM teacher");
             PreparedStatement subjectStmt = conn.prepareStatement("SELECT subject_id, subject.subject_name FROM subject")) {

            ResultSet subjectRs = subjectStmt.executeQuery();
            List<String> subjectNames = new ArrayList<>();
            while (subjectRs.next()) {
                int sid = subjectRs.getInt("subject_id");
                String name = subjectRs.getString("subject_name");
                subjectNames.add(name);
                subjectNameToIdMap.put(name, sid);
            }

            ResultSet teacherRs = teacherStmt.executeQuery();
            while (teacherRs.next()) {
                int teacherId = teacherRs.getInt("teacher_id");
                String name = teacherRs.getString("last_name") + " " + teacherRs.getString("first_name");

                HBox row = new HBox(10);
                Label nameLabel = new Label(name);

                ComboBox<String> subjectBox = new ComboBox<>();
                subjectBox.getItems().addAll(subjectNames);

                teacherSubjectBoxes.put(teacherId, subjectBox);
                row.getChildren().addAll(nameLabel, subjectBox);
                teacherBox.getChildren().add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        Button assignButton = new Button("Назначить преподавателей");
        Label statusLabel = new Label();

        assignButton.setOnAction(e -> {
            boolean assigned = false;
            for (Map.Entry<Integer, ComboBox<String>> entry : teacherSubjectBoxes.entrySet()) {
                int teacherId = entry.getKey();
                String subjectName = entry.getValue().getValue();
                if (subjectName != null) {
                    int subjectId = subjectNameToIdMap.get(subjectName);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO teacher_subject (teacher_id, subject_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                        stmt.setInt(1, teacherId);
                        stmt.setInt(2, subjectId);
                        stmt.executeUpdate();
                        assigned = true;
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            }

            statusLabel.setText(assigned ? "✅ Преподаватели назначены на предметы" : "⚠️ Ничего не выбрано для назначения");
        });

        assignSection.getChildren().addAll(title, teacherBox, assignButton, statusLabel);
        root.getChildren().add(assignSection);
    }
}
