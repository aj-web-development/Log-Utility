package com.in10s.logutility.parser;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogbackXmlParserImpl implements LogbackXmlParser {

    // Logback's %X{key} or %X{key:-default}; the default-value suffix is ignored.
    private static final Pattern MDC_TOKEN = Pattern.compile("%X\\{([^}:]+)(?::-[^}]*)?\\}");
    // A literal "word=" (or "word.sub-part=") immediately preceding an %X token, e.g. tid=%X{traceId}.
    private static final Pattern LINE_PREFIX_BEFORE = Pattern.compile("([A-Za-z0-9_.-]+=)$");
    private static final Pattern DATE_TOKEN = Pattern.compile("%d\\{([^}]*)\\}");
    private static final Pattern INDEX_TOKEN = Pattern.compile("%i\\b");
    private static final Pattern SPRING_VARIABLE = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public LogbackParseResult parse(String xmlContent) {
        if (!StringUtils.hasText(xmlContent)) {
            throw new LogbackParseException("The uploaded file is empty.");
        }

        Document doc = parseDocument(xmlContent);
        Map<String, String> springProperties = extractSpringProperties(doc);

        List<MdcFieldSuggestion> mdcFields = extractMdcFields(doc, springProperties);

        String backupPathPattern = null;
        String backupRootHint = null;
        String firstFileNamePattern = firstElementText(doc, "fileNamePattern");
        if (firstFileNamePattern != null) {
            String resolved = substitute(firstFileNamePattern, springProperties);
            ConvertedPattern converted = convertRollingPattern(resolved);
            if (converted != null) {
                backupPathPattern = converted.pattern();
                backupRootHint = converted.rootPrefix().isEmpty() ? null : converted.rootPrefix();
            }
        }

        String liveLogPathHint = null;
        String firstFile = firstElementText(doc, "file");
        if (firstFile != null) {
            liveLogPathHint = substitute(firstFile, springProperties);
        }

        return new LogbackParseResult(mdcFields, backupPathPattern, liveLogPathHint, backupRootHint);
    }

    // ------------------------------------------------------------------ XML parsing (XXE-safe)

    private Document parseDocument(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Untrusted upload: disallow DTDs entirely (the strongest, OWASP-recommended XXE
            // defense) — logback-spring.xml files never legitimately need one.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new LogbackParseException("Could not parse the uploaded file as XML: " + e.getMessage(), e);
        }
    }

    private static String firstElementText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    // ------------------------------------------------------------------ springProperty / substitution

    private static Map<String, String> extractSpringProperties(Document doc) {
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList nodes = doc.getElementsByTagName("springProperty");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = el.getAttribute("name");
            String defaultValue = el.getAttribute("defaultValue");
            if (StringUtils.hasText(name) && StringUtils.hasText(defaultValue)) {
                properties.put(name, defaultValue);
            }
        }
        return properties;
    }

    /** Best-effort {@code ${name}} substitution using springProperty defaults; leaves unresolved refs as-is. */
    private static String substitute(String raw, Map<String, String> properties) {
        Matcher matcher = SPRING_VARIABLE.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = properties.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ------------------------------------------------------------------ MDC field extraction

    private static List<MdcFieldSuggestion> extractMdcFields(Document doc, Map<String, String> springProperties) {
        Map<String, MdcFieldSuggestion> byKey = new LinkedHashMap<>(); // preserves first-seen order
        NodeList patterns = doc.getElementsByTagName("pattern");
        for (int i = 0; i < patterns.getLength(); i++) {
            String text = patterns.item(i).getTextContent();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String resolved = substitute(text, springProperties);
            Matcher matcher = MDC_TOKEN.matcher(resolved);
            while (matcher.find()) {
                String key = matcher.group(1);
                byKey.computeIfAbsent(key, k -> {
                    String linePrefix = detectLinePrefix(resolved, matcher.start());
                    return new MdcFieldSuggestion(k, k, humanizeLabel(k), linePrefix);
                });
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private static String detectLinePrefix(String pattern, int mdcTokenStart) {
        int from = Math.max(0, mdcTokenStart - 40);
        String before = pattern.substring(from, mdcTokenStart);
        Matcher prefixMatcher = LINE_PREFIX_BEFORE.matcher(before);
        return prefixMatcher.find() ? prefixMatcher.group(1) : null;
    }

    /** "traceId" -> "Trace Id", "sessionId" -> "Session Id". Purely a starting suggestion. */
    private static String humanizeLabel(String key) {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
        String[] words = spaced.trim().split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    // ------------------------------------------------------------------ rolling-pattern conversion

    private record ConvertedPattern(String pattern, String rootPrefix) {
    }

    /**
     * Converts a resolved {@code fileNamePattern} into this app's root-relative placeholder
     * style. Everything up to the last '/' before the first %d/%i token is treated as the
     * static root prefix (shown only as a hint — the admin supplies the real
     * {@code backupRootPath}); the remainder is converted token-by-token: {@code %d\{...\}}
     * becomes {@code {date}} (or {@code {HH}} if the inner format is hour-only, e.g. a hidden
     * hourly-rotation sub-token), and {@code %i} becomes {@code {i}}.
     */
    private static ConvertedPattern convertRollingPattern(String resolved) {
        Matcher dateMatcher = DATE_TOKEN.matcher(resolved);
        int dateIndex = dateMatcher.find() ? dateMatcher.start() : -1;
        Matcher indexMatcher = INDEX_TOKEN.matcher(resolved);
        int indexIndex = indexMatcher.find() ? indexMatcher.start() : -1;

        int firstToken = (dateIndex == -1) ? indexIndex
                : (indexIndex == -1) ? dateIndex
                : Math.min(dateIndex, indexIndex);
        if (firstToken == -1) {
            return null; // no rotation tokens found — nothing meaningful to suggest
        }

        int slashBefore = resolved.lastIndexOf('/', Math.max(0, firstToken - 1));
        String rootPrefix = slashBefore >= 0 ? resolved.substring(0, slashBefore + 1) : "";
        String patternPart = slashBefore >= 0 ? resolved.substring(slashBefore + 1) : resolved;

        Matcher dm = DATE_TOKEN.matcher(patternPart);
        StringBuilder converted = new StringBuilder();
        int last = 0;
        while (dm.find()) {
            converted.append(patternPart, last, dm.start());
            String inner = dm.group(1);
            if (inner.matches(".*[yMd].*")) {
                converted.append("{date}");
            } else if (inner.matches(".*H.*")) {
                converted.append("{HH}");
            } else {
                converted.append("{date}");
            }
            last = dm.end();
        }
        converted.append(patternPart.substring(last));

        String withIndex = INDEX_TOKEN.matcher(converted.toString()).replaceAll("{i}");
        return new ConvertedPattern(withIndex, rootPrefix);
    }
}
