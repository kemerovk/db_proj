package config;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {

    public static Connection getConnection() {
        try (FileInputStream read = new FileInputStream(new File("application.properties"))){
            Properties prop = new Properties();
            prop.load(read);

            return DriverManager.getConnection(prop.getProperty("db.url"),
                                               prop.getProperty("db.user"),
                                               prop.getProperty("db.pass"));
        }

        catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
