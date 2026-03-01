package lv.lenc;

public class TranslateRequest {
    private String q;
    private String source; // "en", "ru" или "auto"
    private String target; // язык результата, напр. "ru"
    private String format = "text";
    private String api_key = "";

    public TranslateRequest(String q, String source, String target) {
        this.setQ(q);
        this.setSource(source);
        this.setTarget(target);
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getApi_key() {
        return api_key;
    }

    public void setApi_key(String api_key) {
        this.api_key = api_key;
    }

    @Override
    public String toString() {
        return "TranslateRequest{" +
                "q='" + q + '\'' +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", format='" + format + '\'' +
                ", api_key='" + api_key + '\'' +
                '}';
    }
}