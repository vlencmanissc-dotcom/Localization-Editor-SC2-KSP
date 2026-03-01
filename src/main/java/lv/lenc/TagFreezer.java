//package lv.lenc;
//
//import java.util.*;
//import java.util.regex.*;
//
//public final class TagFreezer {
//
//
//    private static final String PREFIX = "[[LTK-";
//    private static final String SUFFIX = "-]]";
//
// S
//    private static final Pattern MAIN_TOKEN = Pattern.compile(
//            Pattern.quote(PREFIX) + "(\\d+)" + Pattern.quote(SUFFIX),
//            Pattern.CASE_INSENSITIVE
//    );
//
//
//    private static final Pattern LOOSE_TOKEN = Pattern.compile(
//            "(?<![\\p{Alnum}])"               // слева не буква/цифра
//                    + "(?:\\[\\[)?\\s*L?TK\\s*[-_\\s]*?(\\d+)"
//                    + "\\s*(?:-\\]\\])?"               // штатный хвост -]]
//                    + "(?:\\s*\\]+)?"                  // съедаем лишние ]
//                    + "(?![\\p{Alnum}])",              // справа не буква/цифра
//            Pattern.CASE_INSENSITIVE
//    );
//
//
//    private static final Pattern ORPHAN_LEFT  = Pattern.compile("\\[+\\s*(<[^>]+>)");   // одна или больше '['
//    private static final Pattern ORPHAN_RIGHT = Pattern.compile("(<[^>]+>)\\s*-?\\]+"); // ']' или '-]' (и пачка ])
//    private static final Pattern ORPHAN_RBR   = Pattern.compile(">\\s*\\]+");           // как было, ок -> ">"
//
//    public static final class Frozen {
//        public final String protectedText;
//        public final List<String> tokens    = new ArrayList<>();
//        public final List<String> originals = new ArrayList<>();
//        public Frozen(String protectedText) { this.protectedText = protectedText; }
//    }
//
//
//    public static Frozen freezeRich(String s) {
//        if (s == null) s = "";
//        String regex =
//                "(<[^>]+>)|" +                                       // любые <...>
//                        "((?:\\p{S}|\\p{Cf}|\\p{Mn}|\\p{Me}" +
//                        "|[\\u2500-\\u259F\\u25A0-\\u25FF\\u2190-\\u21FF\\u2600-\\u26FF])+)|" +
//                        "([_\\-\\^=\\.]{3,})";                               // длинные «полоски»
//
//        Matcher m = Pattern.compile(regex).matcher(s);
//        StringBuffer sb = new StringBuffer();
//        List<String> toks = new ArrayList<>();
//        List<String> orig = new ArrayList<>();
//        int idx = 0;
//
//        while (m.find()) {
//            String token = PREFIX + (idx++) + SUFFIX;
//            toks.add(token);
//            orig.add(m.group());
//            m.appendReplacement(sb, Matcher.quoteReplacement(token));
//        }
//        m.appendTail(sb);
//
//        Frozen f = new Frozen(sb.toString());
//        f.tokens.addAll(toks);
//        f.originals.addAll(orig);
//        return f;
//    }
//
//
//    public static String unfreezeRich(String translated, Frozen f) {
//        if (translated == null || f.tokens.isEmpty()) return translated;
//        String out = translated;
//
//        // 1) точные маркеры
//        for (int i = 0; i < f.tokens.size(); i++) {
//            out = out.replace(f.tokens.get(i), f.originals.get(i));
//        }
//
//        // (LTK-5, LTK5, TK-5, TK5, и т. п.)
//        Matcher m = LOOSE_TOKEN.matcher(out);
//        StringBuffer sb = new StringBuffer();
//        while (m.find()) {
//            int idx = Integer.parseInt(m.group(1));
//            String replacement = (idx >= 0 && idx < f.originals.size())
//                    ? f.originals.get(idx)
//                    : m.group();
//            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
//        }
//        m.appendTail(sb);
//        out = sb.toString();
//
//        // 3) пост-чистка сиротских скобок вокруг HTML-тегов
//        out = ORPHAN_LEFT .matcher(out).replaceAll("$1");
//        out = ORPHAN_RIGHT.matcher(out).replaceAll("$1");
//        out = ORPHAN_RBR  .matcher(out).replaceAll(">");
//
//        return out;
//    }
//
//    private TagFreezer() {}
//}
