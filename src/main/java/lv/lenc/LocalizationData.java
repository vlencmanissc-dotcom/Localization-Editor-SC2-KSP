package lv.lenc;

public class LocalizationData {
    private String key;
    private String ruRu = "";
    private String deDE;   // <- новое поле
    private String enUs = "";
    private String esMx = "";
    private String esEs = "";
    private String frFr = "";
    private String itIt = "";
    private String plPl = "";
    private String ptBr = "";
    private String koKr = "";
    private String zhCn = "";
    private String zhTw = "";

    public LocalizationData(String key, String value, String language) {
        this.key = key;
        switch (language.toLowerCase()) {
            case "ruru":
                this.ruRu = value;
                break;
            case "dede":
                this.deDE = value;
                break;
            case "enus":
                this.enUs = value;
                break;
            case "esmx":
                this.esMx = value;
                break;
            case "eses":
                this.esEs = value;
                break;
            case "frfr":
                this.frFr = value;
                break;
            case "itit":
                this.itIt = value;
                break;
            case "plpl":
                this.plPl = value;
                break;
            case "ptbr":
                this.ptBr = value;
                break;
            case "kokr":
                this.koKr = value;
                break;
            case "zhcn":
                this.zhCn = value;
                break;
            case "zhtw":
                this.zhTw = value;
                break;
            default:
               // throw new IllegalArgumentException("Unsupported language: " + language);
        }
    }

    public String getRuRu() {
        return ruRu;
    }

    public String getZhTw() {
        return zhTw;
    }

    public String getZhCn() {
        return zhCn;
    }

    public String getKoKr() {
        return koKr;
    }

    public String getPtBr() {
        return ptBr;
    }

    public String getPlPl() {
        return plPl;
    }

    public String getItIt() {
        return itIt;
    }

    public String getFrFr() {
        return frFr;
    }

    public String getEsEs() {
        return esEs;
    }

    public String getEsMx() {
        return esMx;
    }

    public String getEnUs() {
        return enUs;
    }
    public String getDeDE() { return deDE; }
    public void setRuRu(String ruRu) {
        this.ruRu = ruRu;
    }

    public void setDeDe(String deDE) {
        this.deDE = deDE;
    }

    public void setEnUs(String enUs) {
        this.enUs = enUs;
    }

    public void setEsMx(String esMx) {
        this.esMx = esMx;
    }

    public void setEsEs(String esEs) {
        this.esEs = esEs;
    }

    public void setFrFr(String frFr) {
        this.frFr = frFr;
    }

    public void setItIt(String itIt) {
        this.itIt = itIt;
    }

    public void setPlPl(String plPl) {
        this.plPl = plPl;
    }

    public void setPtBr(String ptBr) {
        this.ptBr = ptBr;
    }

    public void setKoKr(String koKr) {
        this.koKr = koKr;
    }

    public void setZhCn(String zhCn) {
        this.zhCn = zhCn;
    }

    public void setZhTw(String zhTw) {
        this.zhTw = zhTw;
    }
    public String getByLang(String lang) {
        if (lang == null) return null;

        String v;
        switch (lang.toLowerCase()) {
            case "ruru": v = ruRu; break;
            case "dede": v = deDE; break;
            case "enus": v = enUs; break;
            case "esmx": v = esMx; break;
            case "eses": v = esEs; break;
            case "frfr": v = frFr; break;
            case "itit": v = itIt; break;
            case "plpl": v = plPl; break;
            case "ptbr": v = ptBr; break;
            case "kokr": v = koKr; break;
            case "zhcn": v = zhCn; break;
            case "zhtw": v = zhTw; break;
            default: return null;
        }

        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        if (v.equalsIgnoreCase("null")) return null; // если "null" как текст
        return v;
    }
    public String getKey() {
        return key;
    }

    public LocalizationData(String key) {
        this(key, "", "");
    }
}
