module org.example.challenge_2 {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.challenge_2 to javafx.fxml;
    exports org.example.challenge_2;
}