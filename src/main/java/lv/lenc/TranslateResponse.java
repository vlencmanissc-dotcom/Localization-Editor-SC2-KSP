package lv.lenc;

public class TranslateResponse {
    private String translatedText;

    public String getTranslatedText() {
        return translatedText;
    }

    public void setTranslatedText(String translatedText) {
        this.translatedText = translatedText;
    }

    @Override
    public String toString() {
        return "TranslateResponse{" +
                "translatedText='" + translatedText + '\'' +
                '}';
    }
}
