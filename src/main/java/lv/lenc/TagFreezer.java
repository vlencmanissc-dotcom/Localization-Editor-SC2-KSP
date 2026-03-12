package lv.lenc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagFreezer {

    // Placeholder format used during translation.
    private static final String PREFIX = "[[LTK-";
    private static final String SUFFIX = "-]]";

    // Loose recovery for slightly damaged placeholders after translation.
    private static final Pattern LOOSE_TOKEN = Pattern.compile(
            "(?<![\\p{Alnum}])\\[?\\[?\\s*LTK\\s*[-_\\s]*(\\d+)\\s*[-_\\s]*\\]?\\]?(?![\\p{Alnum}])",
            Pattern.CASE_INSENSITIVE
    );

    /*
     * Protect:
     * - [[...]]
     * - [text]
     * - repeated ASCII marker runs: << >> {{{ )))
     * - decorative ASCII runs: --- === ___ ...
     * - important unicode symbols and symbol sequences, including ■ □ → █ ░ etc.
     *
     * HTML tags are intentionally NOT frozen here.
     */
    private static final Pattern FREEZE_PATTERN = Pattern.compile(
            "(\\[\\[[^\\r\\n\\]]*\\]\\])" +                                  // [[...]]
                    "|(\\[[^\\r\\n\\]]+\\])" +                                       // [text]
                    "|(<{2,}|>{2,}|\\{{2,}|\\}{2,}|\\({2,}|\\){2,})" +               // << >> {{{ )))
                    "|([_\\-=\\.]{3,})" +                                            // --- === ___ ...
                    "|([\\u2500-\\u257F\\u2580-\\u259F\\u25A0-\\u25FF\\u2190-\\u21FF\\u2600-\\u26FF])" + // single symbol
                    "|((?:[\\p{S}\\p{Cf}\\p{Mn}\\p{Me}]|[\\u2500-\\u257F\\u2580-\\u259F\\u25A0-\\u25FF\\u2190-\\u21FF\\u2600-\\u26FF]){2,})" // symbol sequence
    );

    public static final class Frozen {
        public final String protectedText;
        public final List<String> tokens = new ArrayList<>();
        public final List<String> originals = new ArrayList<>();

        public Frozen(String protectedText) {
            this.protectedText = protectedText;
        }
    }

    public static Frozen freezeRich(String s) {
        if (s == null) s = "";

        Matcher m = FREEZE_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        List<String> toks = new ArrayList<>();
        List<String> orig = new ArrayList<>();
        int idx = 0;

        while (m.find()) {
            String token = PREFIX + idx + SUFFIX;
            idx++;

            toks.add(token);
            orig.add(m.group());

            m.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        m.appendTail(sb);

        Frozen f = new Frozen(sb.toString());
        f.tokens.addAll(toks);
        f.originals.addAll(orig);
        return f;
    }

    public static String unfreezeRich(String translated, Frozen f) {
        if (translated == null || f == null || f.tokens.isEmpty()) {
            return translated;
        }

        String out = translated;

        // Restore exact placeholders first.
        for (int i = 0; i < f.tokens.size(); i++) {
            out = out.replace(f.tokens.get(i), f.originals.get(i));
        }

        // Then try to recover slightly damaged placeholders.
        Matcher m = LOOSE_TOKEN.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            String replacement = (idx >= 0 && idx < f.originals.size())
                    ? f.originals.get(idx)
                    : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private TagFreezer() {}
}