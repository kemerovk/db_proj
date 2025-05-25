package scene;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static scene.UsersScene.*;
import static service.Group.*;
import static service.Student.createStudent;
import static service.Student.getStudentIdByName;
import static service.Teacher.createTeacher;
import static service.Teacher.getTeacherIdByName;
import static service.User.*;

public class AuthScene {
    public static void showLoginScene(Stage primaryStage, Connection conn) {
        VBox loginBox = new VBox(10);
        loginBox.setPadding(new Insets(20));
        loginBox.setStyle("-fx-alignment: center;"); // Центрируем все элементы внутри VBox

        Label loginLabel = new Label("Вход");
        TextField loginField = new TextField();
        loginField.setPromptText("Логин");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");

        Button loginButton = new Button("Войти");
        Button switchToRegister = new Button("Зарегистрироваться");
        Label loginMessage = new Label();

        // Кнопки в строке и центрированы
        HBox buttonsBox = new HBox(10, loginButton, switchToRegister);
        buttonsBox.setStyle("-fx-alignment: center;");

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

        loginBox.getChildren().addAll(
                loginLabel,
                loginField,
                passwordField,
                buttonsBox,
                loginMessage
        );

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

    public static void showAdminSceneWithGroupCreation(Stage primaryStage, Connection conn) {
        // Основной контейнер с вкладками
        TabPane tabPane = new TabPane();

        // Вкладка для создания группы
        Tab createGroupTab = new Tab("Создание группы");
        createGroupTab.setClosable(false); // Отключаем возможность закрытия вкладки
        VBox createGroupBox = createGroupTabContent(conn);
        createGroupTab.setContent(createGroupBox);

        // Вкладка для назначения студентов
        Tab studentAssignmentTab = new Tab("Назначение студентов");
        studentAssignmentTab.setClosable(false); // Отключаем возможность закрытия вкладки
        VBox studentAssignmentBox = createStudentAssignmentTabContent(conn);
        studentAssignmentTab.setContent(studentAssignmentBox);

        // Вкладка для назначения преподавателей
        Tab teacherAssignmentTab = new Tab("Назначение преподавателей");
        teacherAssignmentTab.setClosable(false); // Отключаем возможность закрытия вкладки
        VBox teacherAssignmentBox = createTeacherAssignmentTabContent(conn);
        teacherAssignmentTab.setContent(teacherAssignmentBox);

        // Добавление вкладок в TabPane
        tabPane.getTabs().addAll(createGroupTab, studentAssignmentTab, teacherAssignmentTab);

        // Устанавливаем начальную вкладку
        tabPane.getSelectionModel().select(0); // Выбираем вкладку "Создание группы" по умолчанию

        // Устанавливаем сцену с TabPane
        primaryStage.setScene(new Scene(tabPane, 800, 600));
    }

    // Метод для создания контента вкладки "Создание группы"
    private static VBox createGroupTabContent(Connection conn) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        // Секция создания группы
        Label groupTitle = new Label("Создание новой группы");
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
            } catch (SQLException ex) {
                createStatus.setText("Ошибка создания группы: " + ex.getMessage());
            }
        });

        // Секция создания семестра
        Label semesterTitle = new Label("Создание семестра");
        DatePicker semesterStartDate = new DatePicker();
        semesterStartDate.setPromptText("Дата начала семестра");

        // ComboBox для выбора времени пары
        ComboBox<Integer> pairTimeBox = new ComboBox<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM pair_time")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                pairTimeBox.getItems().add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        pairTimeBox.setPromptText("Номер пары");

        // ComboBox для выбора дня недели для каждого предмета
        Map<Integer, ComboBox<Integer>> subjectDayMap = new HashMap<>();
        VBox subjectDayBox = new VBox(5);
        try (PreparedStatement stmt = conn.prepareStatement("SELECT subject_id, subject_name FROM subject")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int subjectId = rs.getInt("subject_id");
                String subjectName = rs.getString("subject_name");
                ComboBox<Integer> dayBox = new ComboBox<>();
                dayBox.getItems().addAll(1, 2, 3, 4, 5, 6);
                dayBox.setPromptText("День недели для " + subjectName);
                subjectDayMap.put(subjectId, dayBox);
                subjectDayBox.getChildren().add(new HBox(10, new Label(subjectName + ":"), dayBox));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Button createSemesterButton = new Button("Начать семестр");
        Label semesterStatus = new Label();

        createSemesterButton.setOnAction(e -> {
            LocalDate startDate = semesterStartDate.getValue();
            Integer pairNumber = pairTimeBox.getValue();

            if (startDate == null || pairNumber == null || subjectDayMap.values().stream().anyMatch(cb -> cb.getValue() == null)) {
                semesterStatus.setText("Выберите дату начала, номер пары и дни для всех предметов.");
                return;
            }

            try {
                createSemesterSchedule(conn, startDate, pairNumber, subjectDayMap);
                semesterStatus.setText("Расписание семестра успешно создано!");
            } catch (SQLException ex) {
                semesterStatus.setText("Ошибка создания расписания: " + ex.getMessage());
            }
        });

        // Верхняя часть
        VBox createGroupBox = new VBox(10,
                groupTitle, facultyBox, nextGroupNumberLabel, createGroupButton, createStatus);
        createGroupBox.setPadding(new Insets(0, 0, 20, 0));

        // Нижняя часть
        VBox createSemesterBox = new VBox(10,
                semesterTitle, semesterStartDate, pairTimeBox, subjectDayBox, createSemesterButton, semesterStatus);
        createSemesterBox.setPadding(new Insets(20, 0, 0, 0));

        root.getChildren().addAll(createGroupBox, createSemesterBox);

        return root;
    }

    private static void createSemesterSchedule(Connection conn, LocalDate startDate, int pairNumber, Map<Integer, ComboBox<Integer>> subjectDayMap) throws SQLException {
        // Для каждой группы создаем расписание на 16 недель
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT group_id, faculty FROM \"group\"")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int groupId = rs.getInt("group_id");
                int facultyId = rs.getInt("faculty");

                // Получаем все предметы для данного факультета
                try (PreparedStatement subjectStmt = conn.prepareStatement(
                        "SELECT subject_id FROM faculty_subject WHERE faculty_id = ?")) {
                    subjectStmt.setInt(1, facultyId);
                    ResultSet subjectRs = subjectStmt.executeQuery();

                    while (subjectRs.next()) {
                        int subjectId = subjectRs.getInt("subject_id");
                        Integer dayOfWeek = subjectDayMap.get(subjectId).getValue();

                        if (dayOfWeek != null) {
                            // Получаем всех преподавателей, назначенных на этот предмет
                            try (PreparedStatement teacherStmt = conn.prepareStatement(
                                    "SELECT teacher_id FROM teacher_subject WHERE subject_id = ?")) {
                                teacherStmt.setInt(1, subjectId);
                                ResultSet teacherRs = teacherStmt.executeQuery();

                                if (teacherRs.next()) {
                                    int teacherId = teacherRs.getInt("teacher_id");

                                    // Создаем записи в таблице schedule
                                    try (PreparedStatement scheduleStmt = conn.prepareStatement(
                                            "INSERT INTO schedule (group_id, subject_id, teacher_id, day_of_week, pair_number) VALUES (?, ?, ?, ?, ?)")) {
                                        scheduleStmt.setInt(1, groupId);
                                        scheduleStmt.setInt(2, subjectId);
                                        scheduleStmt.setInt(3, teacherId);
                                        scheduleStmt.setInt(4, dayOfWeek);
                                        scheduleStmt.setInt(5, pairNumber);
                                        scheduleStmt.executeUpdate();
                                    }

                                    // Получаем только что созданный schedule_id
                                    int scheduleId;
                                    try (PreparedStatement getScheduleId = conn.prepareStatement(
                                            "SELECT schedule_id FROM schedule WHERE group_id = ? AND subject_id = ? AND teacher_id = ? AND day_of_week = ? AND pair_number = ?")) {
                                        getScheduleId.setInt(1, groupId);
                                        getScheduleId.setInt(2, subjectId);
                                        getScheduleId.setInt(3, teacherId);
                                        getScheduleId.setInt(4, dayOfWeek);
                                        getScheduleId.setInt(5, pairNumber);
                                        ResultSet scheduleRs = getScheduleId.executeQuery();
                                        scheduleRs.next();
                                        scheduleId = scheduleRs.getInt("schedule_id");
                                    }

                                    // Создаем lesson_instance для каждой из 16 недель
                                    LocalDate currentDate = startDate;
                                    for (int i = 0; i < 16; i++) {
                                        // Находим ближайший день недели, соответствующий выбранному дню
                                        while (currentDate.getDayOfWeek().getValue() != dayOfWeek) {
                                            currentDate = currentDate.plusDays(1);
                                        }

                                        try (PreparedStatement lessonStmt = conn.prepareStatement(
                                                "INSERT INTO lesson_instance (schedule_id, lesson_date) VALUES (?, ?)")) {
                                            lessonStmt.setInt(1, scheduleId);
                                            lessonStmt.setDate(2, Date.valueOf(currentDate));
                                            lessonStmt.executeUpdate();
                                        }

                                        currentDate = currentDate.plusWeeks(1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static VBox createStudentAssignmentTabContent(Connection conn) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label title = new Label("Назначение студентов на группы");

        ComboBox<String> studentGroupBox = new ComboBox<>();
        // Загрузка студентов для назначения
        try (PreparedStatement stmt = conn.prepareStatement("SELECT student_id, first_name, last_name FROM student")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                studentGroupBox.getItems().add(rs.getString("first_name") + " " + rs.getString("last_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ComboBox<String> groupBox = new ComboBox<>();
        // Загрузка групп с названиями направлений
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT g.group_number, f.faculty_name FROM \"group\" g " +
                        "INNER JOIN faculty f ON g.faculty = f.faculty_id")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groupBox.getItems().add(rs.getString("faculty_name") + " №" + rs.getInt("group_number"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Button assignStudentButton = new Button("Назначить студента в группу");
        Label studentAssignmentStatus = new Label();

        assignStudentButton.setOnAction(e -> {
            String selectedStudent = studentGroupBox.getValue();
            String selectedGroup = groupBox.getValue();

            if (selectedStudent == null || selectedGroup == null) {
                studentAssignmentStatus.setText("Выберите студента и группу.");
                return;
            }

            try {
                // Получаем id студента
                int studentId = getStudentIdByName(selectedStudent, conn);

                // Парсим выбранную группу (формат: "Название направления №номер")
                int lastSpaceIndex = selectedGroup.lastIndexOf(" №");
                if (lastSpaceIndex == -1) {
                    studentAssignmentStatus.setText("Неверный формат группы");
                    return;
                }

                String facultyName = selectedGroup.substring(0, lastSpaceIndex);
                int groupNumber;
                try {
                    groupNumber = Integer.parseInt(selectedGroup.substring(lastSpaceIndex + 2));
                } catch (NumberFormatException ex) {
                    studentAssignmentStatus.setText("Неверный номер группы");
                    return;
                }

                // Назначаем студента в группу
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE student SET group_id = " +
                                "(SELECT g.group_id FROM \"group\" g " +
                                "INNER JOIN faculty f ON g.faculty = f.faculty_id " +
                                "WHERE g.group_number = ? AND f.faculty_name = ?) " +
                                "WHERE student_id = ?")) {
                    stmt.setInt(1, groupNumber);
                    stmt.setString(2, facultyName);
                    stmt.setInt(3, studentId);
                    int affectedRows = stmt.executeUpdate();

                    if (affectedRows > 0) {
                        studentAssignmentStatus.setText("Студент успешно назначен в группу " + facultyName + " №" + groupNumber);
                    } else {
                        studentAssignmentStatus.setText("Не удалось назначить студента в группу");
                    }
                }
            } catch (SQLException ex) {
                studentAssignmentStatus.setText("Ошибка назначения студента: " + ex.getMessage());
            }
        });

        root.getChildren().addAll(title, studentGroupBox, groupBox, assignStudentButton, studentAssignmentStatus);
        return root;
    }

    // Метод для создания контента вкладки "Назначение преподавателей"
    private static VBox createTeacherAssignmentTabContent(Connection conn) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label title = new Label("Назначение преподавателей на предметы");

        ComboBox<String> teacherBox = new ComboBox<>();
        // Загрузка преподавателей
        try (PreparedStatement stmt = conn.prepareStatement("SELECT teacher_id, first_name, last_name FROM teacher")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                teacherBox.getItems().add(rs.getString("first_name") + " " + rs.getString("last_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        ComboBox<String> subjectBox = new ComboBox<>();
        // Загрузка предметов
        try (PreparedStatement stmt = conn.prepareStatement("SELECT subject_name FROM subject")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                subjectBox.getItems().add(rs.getString("subject_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Button assignTeacherButton = new Button("Назначить преподавателя на предмет");
        Label teacherAssignmentStatus = new Label();

        assignTeacherButton.setOnAction(e -> {
            String selectedTeacher = teacherBox.getValue();
            String selectedSubject = subjectBox.getValue();

            if (selectedTeacher == null || selectedSubject == null) {
                teacherAssignmentStatus.setText("Выберите преподавателя и предмет.");
                return;
            }

            try {
                // Получаем id преподавателя
                int teacherId = getTeacherIdByName(selectedTeacher, conn);

                // Назначаем преподавателя на предмет
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO teacher_subject (teacher_id, subject_id) VALUES (?, (SELECT subject_id FROM subject WHERE subject_name = ?))")) {
                    stmt.setInt(1, teacherId);
                    stmt.setString(2, selectedSubject);
                    stmt.executeUpdate();
                    teacherAssignmentStatus.setText("Преподаватель успешно назначен на предмет: " + selectedSubject);
                }
            } catch (SQLException ex) {
                teacherAssignmentStatus.setText("Ошибка назначения преподавателя: " + ex.getMessage());
            }
        });

        root.getChildren().addAll(title, teacherBox, subjectBox, assignTeacherButton, teacherAssignmentStatus);
        return root;
    }

    private record FacultyItem(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}