package model.novel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
/** Loads all the dialogues. */
public class DialogueLoader {

    public static VisualNovel load(String path) {

        try {
            InputStream input = DialogueLoader.class.getResourceAsStream(path);

            ObjectMapper mapper = new ObjectMapper();

            return mapper.readValue(input, VisualNovel.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load dialogue", e);
        }
    }
}