package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HowToPlayController {

    @FXML
    Label label;
    private SceneNavigator navigator;

    public HowToPlayController(SceneNavigator navigator) {
        this.navigator = navigator;
    }

   

    @FXML
    private void initialize() {
     
        label.setText(
            """
                ANSWER QUESTIONS
                Defeat enemies by answering their questions correctly.

                CLEAR EACH ERA
                Progress through 7 eras, each based on a programming paradigm.

                USE YOUR TOOLBOX
                Use lifelines when questions become difficult:
                  - DEBUG: Eliminate one incorrect answer from the choices.
                  - DATABASE: Peek at the database, but it may be incorrect.
                  - CONSULT A PROGRAMMER: Get advice, it might be useful.

                DEFEAT THE BOSS
                Recover the countermeasure component from each era.
                Gather all 7 components to save Humanity.
                """
        );
    }

    public void removeOverlay(){
        navigator.hideOverlay();
    }

}