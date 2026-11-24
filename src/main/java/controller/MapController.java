package controller;

import java.util.HashMap;
import java.util.Map;

import data.AudioRegistry;
import data.Constants;
import data.GameSession;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import model.GameState;
import model.animator.MapAnimator;
import model.character.Component;
import model.character.Player;
import model.map.Era;

/**
 * Controller for the map. It has a label and imageView per era, clicking one
 * goes to that level.
 */
enum Level {
    LEVEL1(1), // intro to programming paradigms
    LEVEL2(2), // Procedural programming
    LEVEL3(3), // Functional programming
    LEVEL4(4), // Object Oriented Programming
    LEVEL5(5), // Imperative vs Declarative programming
    LEVEL6(6), // Event Driven programming
    LEVEL7(7); // Component Mapping

    private final int number;

    Level(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }
}

public class MapController {

    /**
     * What the map does once the player has MORE components than paradigms (after
     * beating the last one).
     * Change ALL_DONE_POLICY below to pick one.
     */
    enum AllDonePolicy {
        /** Every paradigm is greyed out (nothing left to do on the map). */
        ALL_LOCKED,
        /** Only the LAST paradigm stays clickable so the player can replay it. */
        REPLAY_LAST,
        /** Every paradigm becomes clickable (free replay of any paradigm). */
        ALL_UNLOCKED
    }

    /** >>> The one line to edit to change the "beat everything" behaviour. <<< */
    private static final AllDonePolicy ALL_DONE_POLICY = AllDonePolicy.ALL_LOCKED;

    /* For the imageview, sets the color black when not unlocked */
    private static final ColorAdjust BLACKOUT = new ColorAdjust();
    static {
        BLACKOUT.setBrightness(-1.0);
    }

    /** CSS class (see view/style/map.css) for paradigms the player can't click. */
    private static final String LOCKED_STYLE_CLASS = "paradigm-locked";
    /** CSS class put on the one paradigm the player should pick next. */
    private static final String ACTIVE_STYLE_CLASS = "paradigm-active";

    /** Clickable interface for the map */
    @FXML
    private Label era1Label;
    @FXML
    private ImageView era1ImageView;

    @FXML
    private Label era2Label;
    @FXML
    private ImageView era2ImageView;

    @FXML
    private Label era3Label;
    @FXML
    private ImageView era3ImageView;

    @FXML
    private Label era4Label;
    @FXML
    private ImageView era4ImageView;

    @FXML
    private Label era5Label;
    @FXML
    private ImageView era5ImageView;

    @FXML
    private Label era6Label;
    @FXML
    private ImageView era6ImageView;

    @FXML
    private Label era7Label;
    @FXML
    private ImageView era7ImageView;

    @FXML
    private Label componentCountLabel;

    private int lastShownComponentCount = -1;

    /* Status panel: health, lives and one component icon per era */
    @FXML
    private Label hpLabel;
    @FXML
    private Label livesLabel;
    @FXML
    private HBox statusComponentBox;

    /** Size (px) of the component icons in the status panel. */
    private static final double STATUS_ICON_SIZE = 60;
    /** Icon of each era's component in the status panel, by era number. */
    private final Map<Integer, ImageView> statusImageByEra = new HashMap<>();
    /* Last values drawn, so the labels and icons are only touched when something changes. */
    private int lastShownHealth = Integer.MIN_VALUE;
    private int lastShownMaxHealth = Integer.MIN_VALUE;
    private int lastShownLives = Integer.MIN_VALUE;
    private int lastShownCollectedMask = -1;

    @FXML
    private ImageView particleImageView;
    @FXML
    private ImageView mapImageView;

    private MapAnimator mapAnimator;

    @FXML
    private Button backButton;

    /** Map of the levels */
    private final Map<Node, Level> levelByNode = new HashMap<>();

    /* For the image view dark effect */
    private final Map<Level, ImageView> imageByLevel = new HashMap<>();
    private Level currentLevel;

    /* Adds the hover and description logic */
    private final Map<Level, Tooltip> tooltipByLevel = new HashMap<>();
    private final Map<Integer, Component> reward = new HashMap<>();

    private GameStateController stateController;
    private SceneNavigator navigator;

    public MapController(GameStateController stateController, SceneNavigator navigator) {
        this.stateController = stateController;
        this.navigator = navigator;
        debugShowGameLost();

    }

    // DEBUG
    private void debugShowGameLost() {
        if (!Constants.DEBUG_TOOLS) {
            return;
        }
        navigator.showGameLost();
    }

    /** Initialize all the imageView and Label Hashmap */
    @FXML
    private void initialize() {
        levelByNode.put(era1Label, Level.LEVEL1);
        levelByNode.put(era2Label, Level.LEVEL2);
        levelByNode.put(era3Label, Level.LEVEL3);
        levelByNode.put(era4Label, Level.LEVEL4);
        levelByNode.put(era5Label, Level.LEVEL5);

        levelByNode.put(era6Label, Level.LEVEL6);
        levelByNode.put(era7Label, Level.LEVEL7);

        imageByLevel.put(Level.LEVEL1, era1ImageView);
        imageByLevel.put(Level.LEVEL2, era2ImageView);
        imageByLevel.put(Level.LEVEL3, era3ImageView);
        imageByLevel.put(Level.LEVEL4, era4ImageView);
        imageByLevel.put(Level.LEVEL5, era5ImageView);
        imageByLevel.put(Level.LEVEL6, era6ImageView);
        imageByLevel.put(Level.LEVEL7, era7ImageView);

        loadEraImages(); // load the images for the imageview
        buildStatusComponentIcons(); // after loadEraImages(): reuses the images it loaded
        /* Tooltip */
        for (Map.Entry<Level, ImageView> entry : imageByLevel.entrySet()) {
            Tooltip tip = new Tooltip();
            tip.setWrapText(true);
            tip.setMaxWidth(320);
            tip.setShowDelay(Duration.millis(150));
            tip.setShowDuration(Duration.INDEFINITE); // stays while hovered
            tip.getStyleClass().add("component-tooltip");
            Tooltip.install(entry.getValue(), tip);
            tooltipByLevel.put(entry.getKey(), tip);
        }

        mapAnimator = new MapAnimator(mapImageView, particleImageView);
        AudioController.getInstance().bindClick(backButton, AudioRegistry.SFX_UI_CLICK);
        refreshComponentCount(); // show the right number the moment the map appears
        refreshPlayerStatus(); // same for health, lives and the component icons
    }

    /**
     * Reads the component count from the live session (same data as everywhere
     * else).
     */
    private int currentComponentCount() {
        GameSession session = GameSession.current();
        return (session == null) ? 0 : session.getPlayer().getCollectedComponents().size();
    }

    /**
     * Which paradigm may be clicked for a given component count.
     * Rule: paradigm number = components + 1.
     */
    private boolean isParadigmClickable(int paradigmNumber, int componentCount) {
        int next = componentCount + 1;
        if (next <= Era.TOTAL_ERAS) {
            return paradigmNumber == next; // the normal case: exactly one paradigm
        }
        // components >= number of paradigms: everything is beaten, use the policy
        switch (ALL_DONE_POLICY) {
            case REPLAY_LAST:
                return paradigmNumber == Era.TOTAL_ERAS;
            case ALL_UNLOCKED:
                return true;
            case ALL_LOCKED:
            default:
                return false;
        }
    }

    /** Disables / dims every paradigm node except the allowed one. */
    private void applyParadigmAvailability(int componentCount) {
        for (Map.Entry<Node, Level> entry : levelByNode.entrySet()) {
            Node node = entry.getKey();
            boolean clickable = isParadigmClickable(entry.getValue().getNumber(), componentCount);

            node.setDisable(!clickable); // disabled nodes ignore clicks and :hover
            node.getStyleClass().removeAll(LOCKED_STYLE_CLASS, ACTIVE_STYLE_CLASS);
            node.getStyleClass().add(clickable ? ACTIVE_STYLE_CLASS : LOCKED_STYLE_CLASS);
        }

        // dimms when the level is not done, and does the otther otherwise
        for (Map.Entry<Level, ImageView> entry : imageByLevel.entrySet()) {
            boolean finished = isParadigmFinished(entry.getKey().getNumber(), componentCount);
            setImageUnlocked(entry.getValue(), finished);
        }
        for (Map.Entry<Level, Tooltip> entry : tooltipByLevel.entrySet()) {
            int number = entry.getKey().getNumber();
            entry.getValue().setText(isParadigmFinished(number, componentCount)
                    ? componentText(number)
                    : "Era " + number + "\nComponent not recovered yet.");
        }
    }

    /* helper , gets the description of the component */
    private String componentText(int eraNumber) {
        GameSession session = GameSession.current();
        if (session != null) {
            for (Component c : session.getPlayer().getCollectedComponents()) {
                if (c.getOriginEraNumber() == eraNumber) {
                    return c.getName() + "\n\n" + c.getDescription();
                }
            }
        }
        return "Era " + eraNumber;
    }

    /* Determines if the paradigm era or level is finished */
    private boolean isParadigmFinished(int paradigmNumber, int componentCount) {
        return componentCount >= paradigmNumber;
    }

    /**
     * Re-reads the session and updates the count label and paradigm buttons if it
     * changed.
     */
    private void refreshComponentCount() {
        int count = currentComponentCount();
        if (count != lastShownComponentCount) {
            lastShownComponentCount = count;
            componentCountLabel.setText("Components: " + count + " / " + Era.TOTAL_ERAS);
            applyParadigmAvailability(count);
        }
    }

    /**
     * Updates "HP", "Lives" and the component icons of the status panel from the live session.
     * Only touches the UI when a value changed. With no session it shows placeholders and locked icons.
     */
    private void refreshPlayerStatus() {
        GameSession session = GameSession.current();
        int health = -1;
        int maxHealth = -1;
        int lives = -1;
        int collectedMask = 0; // bit n set = component of era n collected
        if (session != null) {
            Player player = session.getPlayer();
            health = player.getCurrentHealth();
            maxHealth = player.getMaxHealth();
            lives = player.getLives();
            for (Component c : player.getCollectedComponents()) {
                int era = c.getOriginEraNumber();
                if (era >= 1 && era <= Era.TOTAL_ERAS) {
                    collectedMask |= 1 << era;
                }
            }
        }

        if (health != lastShownHealth || maxHealth != lastShownMaxHealth) {
            lastShownHealth = health;
            lastShownMaxHealth = maxHealth;
            hpLabel.setText(session == null ? "HP: -- / --" : "HP: " + health + " / " + maxHealth);
        }
        if (lives != lastShownLives) {
            lastShownLives = lives;
            livesLabel.setText(session == null ? "Lives: -- / " + Player.MAX_LIVES
                    : "Lives: " + lives + " / " + Player.MAX_LIVES);
        }
        if (collectedMask != lastShownCollectedMask) {
            lastShownCollectedMask = collectedMask;
            for (Map.Entry<Integer, ImageView> entry : statusImageByEra.entrySet()) {
                boolean collected = (collectedMask & (1 << entry.getKey())) != 0;
                setImageUnlocked(entry.getValue(), collected); // locked = blacked out, same as the map
            }
        }
    }

    /* Navigates to gameplay sate and loads the scene depending on the level */
    public void navigateToLevel(int levelNumber) {
        GameSession session = GameSession.current();
        if (session == null) {
            System.err.println("[Map] No active session. Start a New Game or Continue first.");
            return;
        }
        if (levelNumber > session.getHighestUnlockedEra()) {
            return; // locked paradigm: ignore the click instead of throwing
        }
        if (!isParadigmClickable(levelNumber, currentComponentCount())) {
            return; // safety net: same rule as the buttons, in case a click slips through
        }
        session.enterEra(levelNumber); // keeps the same player: resumes the saved run for this era, else starts it
                                       // fresh (refills the toolbox)
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.GAMEPLAY);
    }

    /** Level pressed: mouse event that navigates to that level. */
    @FXML
    public void levelPressed(MouseEvent event) {
        Level level = levelByNode.get((Node) event.getSource());

        if (level != null) {
            currentLevel = level;
            navigateToLevel(level.getNumber());
        }
    }

    public void update(double deltaSeconds) {
        // amimations update
        refreshComponentCount(); // keeps the display in sync if the count changes while the map is open
        refreshPlayerStatus(); // same for health, lives and the status component icons
        mapAnimator.updateAnimation(deltaSeconds);
    }

    /* Goes back to the main menu */
    public void backPressed() {
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAIN_MENU);
    }

    /* Components */
    private void setImageUnlocked(ImageView imageView, boolean unlocked) {
        imageView.setEffect(unlocked ? null : BLACKOUT);
    }
    /** Fills the status panel's HBox with one icon per era, reusing the images the map already loaded. */
    private void buildStatusComponentIcons() {
        for (Level level : Level.values()) {
            ImageView icon = new ImageView(imageByLevel.get(level).getImage());
            icon.setFitWidth(STATUS_ICON_SIZE);
            icon.setFitHeight(STATUS_ICON_SIZE);
            icon.setPreserveRatio(true);
            icon.setSmooth(false); // keep the pixel art sharp
            setImageUnlocked(icon, false); // refreshPlayerStatus() unlocks the collected ones
            statusImageByEra.put(level.getNumber(), icon);
            statusComponentBox.getChildren().add(icon);
        }
    }

    /* Load the component assteds */
    private void loadEraImages() {
        for (Map.Entry<Level, ImageView> entry : imageByLevel.entrySet()) {
            String path = "/assets/components/era" + entry.getKey().getNumber() + ".png";
            var stream = getClass().getResourceAsStream(path);
            if (stream == null) {
                System.err.println("[Map] Missing image: " + path);
                continue;
            }
            ImageView view = entry.getValue();
            view.setImage(new Image(stream));
            view.setPreserveRatio(true);
            view.setSmooth(false); // keep the pixel art sharp
        }
    }
}