package model.question;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
//loads the question data
public class TutorialQuestionLoader {

    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static TutorialQuestionData load(String path){
        
        try (InputStream input = TutorialQuestionLoader.class.getResourceAsStream(path)) {
                
                if (input == null) {              
                    throw new IllegalArgumentException("Resource not found: " + path);
                }

            return mapper.readValue(input, TutorialQuestionData.class);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load tutorial questions", e);
        }
    }

}


    

