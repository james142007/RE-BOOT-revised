package controller;

import java.util.concurrent.Callable;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.Slider;

/**
 * Wires a Slider (and optional % label) to the one shared music volume in AudioController.
 * Used by the main menu and pause menu so both sliders show the same value.
 * @author Felix
 */
public final class MusicVolumeSlider {

    private MusicVolumeSlider() {
    }

    /** Binds the slider to the music volume both ways and shows it as a % in the label. Starts at the current volume. */
    public static void bind(Slider slider, Label percentLabel) {
        AudioController audio = AudioController.getInstance();

        slider.setMin(0.0);
        slider.setMax(1.0);
        slider.setBlockIncrement(0.05); // arrow keys move 5%

        // bidirectional: dragging changes the music live, and outside changes move the slider
        slider.valueProperty().bindBidirectional(audio.musicVolumeProperty());

        if (percentLabel != null) {
            percentLabel.textProperty().bind(Bindings.createStringBinding(new Callable<String>() {
                @Override
                public String call() {
                    return Math.round(slider.getValue() * 100) + "%";
                }
            }, slider.valueProperty()));
        }

        // the cyan "filled" part of the track (JavaFX has no built-in fill)
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number oldValue, Number newValue) {
                paintFill(slider);
            }
        });
        slider.skinProperty().addListener(new ChangeListener<Skin<?>>() {
            @Override
            public void changed(ObservableValue<? extends Skin<?>> obs, Skin<?> oldSkin, Skin<?> newSkin) {
                paintFill(slider);
            }
        });
        paintFill(slider);
    }

    // colors the left part of the track up to the thumb
    private static void paintFill(Slider slider) {
        Node track = slider.lookup(".track");
        if (track == null) {
            return; // skin not created yet: the skin listener calls this again
        }
        double range = slider.getMax() - slider.getMin();
        double percent = range <= 0 ? 0 : (slider.getValue() - slider.getMin()) / range * 100.0;
        track.setStyle(String.format(
                "-fx-background-color: linear-gradient(to right, #00F5FF 0%%, #00F5FF %1$.1f%%, #0A1A22 %1$.1f%%, #0A1A22 100%%);",
                percent));
    }
}
