import config.DatabaseConnection;
import javafx.application.Application;
import javafx.stage.Stage;

import java.sql.*;

import static scene.AuthScene.showLoginScene;

public class DatabaseProject extends Application {

    private final Connection connection = DatabaseConnection.getConnection();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Вход в систему");
        showLoginScene(primaryStage, connection);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
