import config.DatabaseConnection;
import form.LoginForm;
import javafx.application.Application;
import javafx.stage.Stage;

import java.sql.*;

public class DatabaseProject extends Application {
    private final Connection connection = DatabaseConnection.getConnection();

    @Override
    public void start(Stage primaryStage) {
        new LoginForm(connection).start(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}