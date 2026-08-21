package com.mkt.activity.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side HTML whitelist (R22.1). Drops script, iframe, event handlers, javascript URLs, and unknown tags.
 */
public final class ActivityHtmlSanitizer {

    public static final int MAX_LENGTH = 65535;

    private static final Set<String> VOID_TAGS = Set.of("br", "img", "hr");
    private static final Set<String> DROP_WITH_CONTENT =
            Set.of("script", "style", "iframe", "object", "embed", "form", "link", "meta", "base", "svg", "math");
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p",
            "div",
            "span",
            "br",
            "hr",
            "strong",
            "b",
            "em",
            "i",
            "u",
            "s",
            "ul",
            "ol",
            "li",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "a",
            "img",
            "blockquote",
            "pre",
            "code",
            "table",
            "thead",
            "tbody",
            "tr",
            "td",
            "th");
    private static final Map<String, Set<String>> ATTRS = Map.of(
            "a", Set.of("href", "title", "target"),
            "img", Set.of("src", "alt", "title", "width", "height"),
            "td", Set.of("colspan", "rowspan"),
            "th", Set.of("colspan", "rowspan"));
    private static final Set<String> GLOBAL_ATTRS = Set.of("class");
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:][\\w:.-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern SAFE_CLASS = Pattern.compile("^[a-zA-Z0-9 _-]{1,128}$");
    private static final Pattern SAFE_INT = Pattern.compile("^[0-9]{1,4}$");
    private static final Pattern ENTITY = Pattern.compile("&#x([0-9a-fA-F]{1,6});|&#([0-9]{1,7});", Pattern.CASE_INSENSITIVE);

    private ActivityHtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        int n = html.length();
        while (i < n) {
            char ch = html.charAt(i);
            if (ch != '<') {
                appendText(out, ch);
                i++;
                continue;
            }
            if (i + 1 < n) {
                char next = html.charAt(i + 1);
                if (next != '/' && next != '!' && next != '?' && !Character.isLetter(next)) {
                    appendText(out, '<');
                    i++;
                    continue;
                }
            }
            if (startsWith(html, i, "<!--")) {
                int end = html.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (startsWithIgnoreCase(html, i, "<!doctype") || startsWith(html, i, "<?")) {
                int end = html.indexOf('>', i + 2);
                i = end < 0 ? n : end + 1;
                continue;
            }
            int gt = html.indexOf('>', i + 1);
            if (gt < 0) {
                appendText(out, '<');
                i++;
                continue;
            }
            String rawTag = html.substring(i + 1, gt).trim();
            i = gt + 1;
            if (rawTag.isEmpty()) {
                continue;
            }
            boolean closing = rawTag.charAt(0) == '/';
            String body = closing ? rawTag.substring(1).trim() : rawTag;
            int sp = indexOfWs(body);
            String name = (sp < 0 ? body : body.substring(0, sp)).toLowerCase(Locale.ROOT);
            if (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            if (name.isEmpty() || !name.chars().allMatch(c -> c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                continue;
            }
            if (DROP_WITH_CONTENT.contains(name)) {
                if (!closing && !VOID_TAGS.contains(name)) {
                    i = skipUntilClose(html, i, name);
                }
                continue;
            }
            if (!ALLOWED_TAGS.contains(name)) {
                continue;
            }
            if (closing) {
                out.append("</").append(name).append('>');
                continue;
            }
            String attrSrc = sp < 0 ? "" : body.substring(sp);
            boolean selfClosing = attrSrc.endsWith("/");
            if (selfClosing) {
                attrSrc = attrSrc.substring(0, attrSrc.length() - 1);
            }
            out.append('<').append(name);
            appendAttrs(out, name, attrSrc);
            if (VOID_TAGS.contains(name) || selfClosing) {
                out.append(" />");
            } else {
                out.append('>');
            }
        }
        return out.toString().trim();
    }

    private static void appendAttrs(StringBuilder out, String tag, String attrSrc) {
        Matcher matcher = ATTR.matcher(attrSrc);
        boolean relAdded = false;
        while (matcher.find()) {
            String attrName = matcher.group(1).toLowerCase(Locale.ROOT);
            if (attrName.startsWith("on") || attrName.contains(":") || "style".equals(attrName) || "srcset".equals(attrName)) {
                continue;
            }
            Set<String> allowed = ATTRS.getOrDefault(tag, Set.of());
            if (!allowed.contains(attrName) && !GLOBAL_ATTRS.contains(attrName)) {
                continue;
            }
            String raw = stripQuotes(matcher.group(2));
            String decoded = decodeEntities(raw).trim();
            String safe = sanitizeAttr(tag, attrName, decoded);
            if (safe == null) {
                continue;
            }
            out.append(' ').append(attrName).append("=\"").append(escapeAttr(safe)).append('"');
            if ("a".equals(tag) && "target".equals(attrName) && "_blank".equals(safe)) {
                out.append(" rel=\"noopener noreferrer\"");
                relAdded = true;
            }
        }
        if ("a".equals(tag) && !relAdded && attrSrc.toLowerCase(Locale.ROOT).contains("target")) {
            // rel already handled when target=_blank
        }
    }

    private static String sanitizeAttr(String tag, String name, String value) {
        if (value == null) {
            return null;
        }
        if ("href".equals(name)) {
            return safeUrl(value, true);
        }
        if ("src".equals(name)) {
            return safeUrl(value, false);
        }
        if ("target".equals(name)) {
            return "_blank".equalsIgnoreCase(value) ? "_blank" : null;
        }
        if ("class".equals(name)) {
            return SAFE_CLASS.matcher(value).matches() ? value : null;
        }
        if ("width".equals(name) || "height".equals(name) || "colspan".equals(name) || "rowspan".equals(name)) {
            return SAFE_INT.matcher(value).matches() ? value : null;
        }
        if ("alt".equals(name) || "title".equals(name)) {
            return value.length() <= 256 ? value : value.substring(0, 256);
        }
        return "img".equals(tag) || "a".equals(tag) ? value : null;
    }

    private static String safeUrl(String value, boolean allowMailto) {
        String lower = decodeEntities(value).strip().toLowerCase(Locale.ROOT).replace("\u0000", "");
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return null;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value.strip();
        }
        if (allowMailto && lower.startsWith("mailto:")) {
            return value.strip();
        }
        if (lower.startsWith("#") || lower.startsWith("/")) {
            return value.strip();
        }
        return null;
    }

    private static int skipUntilClose(String html, int from, String name) {
        String open = "<" + name;
        String close = "</" + name;
        int depth = 1;
        int i = from;
        int n = html.length();
        while (i < n && depth > 0) {
            int nextOpen = indexOfIgnoreCase(html, open, i);
            int nextClose = indexOfIgnoreCase(html, close, i);
            if (nextClose < 0) {
                return n;
            }
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                int gt = html.indexOf('>', nextOpen + 1);
                i = gt < 0 ? n : gt + 1;
            } else {
                depth--;
                int gt = html.indexOf('>', nextClose + 1);
                i = gt < 0 ? n : gt + 1;
            }
        }
        return i;
    }

    private static void appendText(StringBuilder out, char ch) {
        switch (ch) {
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '&' -> out.append("&amp;");
            case '"' -> out.append("&quot;");
            default -> {
                if (ch == 0 || (ch < 32 && ch != '\n' && ch != '\r' && ch != '\t')) {
                    return;
                }
                out.append(ch);
            }
        }
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                        || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String decodeEntities(String value) {
        Matcher matcher = ENTITY.matcher(value);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String dec = matcher.group(2);
            int cp;
            try {
                cp = hex != null ? Integer.parseInt(hex, 16) : Integer.parseInt(dec);
            } catch (NumberFormatException ex) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(Character.toString(cp)));
        }
        matcher.appendTail(decoded);
        return decoded.toString().replace("&colon;", ":").replace("&COLON;", ":");
    }

    private static boolean startsWith(String html, int i, String token) {
        return html.regionMatches(i, token, 0, token.length());
    }

    private static boolean startsWithIgnoreCase(String html, int i, String token) {
        return html.regionMatches(true, i, token, 0, token.length());
    }

    private static int indexOfIgnoreCase(String html, String token, int from) {
        int n = html.length();
        int m = token.length();
        for (int i = from; i + m <= n; i++) {
            if (html.regionMatches(true, i, token, 0, m)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfWs(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
