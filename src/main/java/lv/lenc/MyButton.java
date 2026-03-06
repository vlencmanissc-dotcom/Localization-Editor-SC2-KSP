package lv.lenc;

import javafx.scene.control.Button;

public class MyButton extends Button implements Disabable {
    public MyButton(String text) {
        super(text);
    }

    public void disable(Boolean bol)
    {
        this.disableProperty().set(bol); // Set disabled property to true
    }
}
