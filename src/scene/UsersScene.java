package scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UsersScene {
    public static void showAdminScene(Stage primaryStage) {
        VBox adminBox = new VBox(10);
        adminBox.setPadding(new Insets(20));
        adminBox.setAlignment(Pos.CENTER);
        Label label = new Label("Вы вошли как Администратор");
        adminBox.getChildren().add(label);
        primaryStage.setScene(new Scene(adminBox, 400, 300));
    }

    public static void showTeacherScene(Stage primaryStage) {
        VBox teacherBox = new VBox(10);
        teacherBox.setPadding(new Insets(20));
        teacherBox.setAlignment(Pos.CENTER);
        Label label = new Label("Вы вошли как Преподаватель");
        teacherBox.getChildren().add(label);
        primaryStage.setScene(new Scene(teacherBox, 400, 300));
    }

    public static void showStudentScene(Stage primaryStage) {
        VBox studentBox = new VBox(10);
        studentBox.setPadding(new Insets(20));
        studentBox.setAlignment(Pos.CENTER);
        Label label = new Label("Вы вошли как Студент");
        studentBox.getChildren().add(label);
        primaryStage.setScene(new Scene(studentBox, 400, 300));
    }
}
