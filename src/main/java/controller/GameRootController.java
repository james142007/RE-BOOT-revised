package controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
/**
 * Controller for the main game root, a stackpane with the overlays and the main content.
 * Lets scenes get swapped and overlays (pause, game over) added.
 * @author Felix
 */
public class GameRootController {

    // how dark the layer behind a dimmed overlay (e.g., pause) is: 0 = clear, 1 = solid black
    private static final double DIM_OPACITY = 0.65;
    
    //contains all the main scenes
    @FXML 
    private StackPane contentContainer;

    //contains overlays such as pause
    @FXML 
    private  StackPane overlayContainer;

    @FXML 
    public void initialize(){
        overlayContainer.setMouseTransparent(true);
    }
    // this methods sets the current content of the game e.g., Battle, Main menu etc
    public void setContent(Parent content){
        contentContainer.getChildren().setAll(content);
    }
    //shows overlays such as pause etc.
    public void showOverlay(Parent content){
        showOverlay(content, false);
    }
    //same as above, but can also darken everything behind the overlay (used by pause)
    public void showOverlay(Parent content, boolean dimBackground){
        if (dimBackground) {
            // dim layer goes first so it sits behind the overlay content
            overlayContainer.getChildren().setAll(createDimLayer(), content);
        } else {
            overlayContainer.getChildren().setAll(content);
        }
        StackPane.setAlignment(content, Pos.CENTER);
        overlayContainer.setMouseTransparent(false);

        overlayContainer.toFront();
    }
    public void hideOverlay(){
        overlayContainer.getChildren().clear();
        overlayContainer.setMouseTransparent(true);
    }
    // black layer that fills the whole overlay container and blocks clicks on whatever is behind it
    private Region createDimLayer(){
        Region dim = new Region();
        dim.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, DIM_OPACITY), null, null)));
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return dim;
    }
    public StackPane getContentContainer() {
        return contentContainer;
    }
    public StackPane getOverlayContainer() {
        return overlayContainer;
    }
    

}