package model.novel;
import java.util.List;

/** Represents the whole json file, with the dialogue lines and their speakers. */
public class VisualNovelScene {

    private String scene;
    private List<DialogueLine> dialogues;
    
    public List<DialogueLine> getDialogues() {
        return dialogues;
    }

    public String getScene() {
        return scene;
    }
}
