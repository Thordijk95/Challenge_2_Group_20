module org.example.challenge_2 {
    requires javafx.controls;
    requires javafx.fxml;
  requires java.management;

  opens org.example.challenge_2 to javafx.fxml;
    exports org.example.challenge_2;
}