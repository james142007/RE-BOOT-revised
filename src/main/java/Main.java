import controller.AudioController;
import controller.GameEngine;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        // main.java.Main entry part of the game
        Image icon = new Image(getClass().getResourceAsStream("/assets/icon.jpg"));

        stage.getIcons().add(icon);
        stage.setTitle("RE:BOOT");
        AudioController.getInstance().preload(); // load sound effects, report any missing audio files
        GameEngine gameEngine = new GameEngine(stage);
        gameEngine.startGame();

    }

    @Override
    public void stop() {
        AudioController.getInstance().shutdown();
    }
}