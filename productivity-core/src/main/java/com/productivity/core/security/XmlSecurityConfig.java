package com.productivity.core.security;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads and manages security rules parsed from security-config.xml.
 * Implements XXE prevention aligned with Anthropic Cybersecurity Skill:
 * testing-for-xxe-injection-vulnerabilities (CWE-611 / OWASP Top 10).
 */
public final class XmlSecurityConfig {

    public record RateLimitRule(String path, String method, int limit, int windowSeconds, String description) {}

    private static volatile XmlSecurityConfig INSTANCE;

    private final Map<String, Pattern> regexPatterns = new HashMap<>();
    private final Map<String, String> regexStrings = new HashMap<>();
    private final Map<String, String> securityHeaders = new LinkedHashMap<>();
    private final Map<String, String> cacheControlPaths = new LinkedHashMap<>();

    private boolean securityFilterEnabled = true;
    private boolean csrfEnabled = true;
    private String csrfHeaderName = "X-CSRF-TOKEN";
    private String csrfParamName = "_csrf";
    private String csrfCookieName = "XSRF-TOKEN";
    private Set<String> csrfSafeMethods = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private boolean rateLimitingEnabled = true;
    private int defaultRateLimit = 120;
    private int defaultRateWindowSeconds = 60;
    private final List<RateLimitRule> rateLimitRules = new ArrayList<>();

    public static XmlSecurityConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (XmlSecurityConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = new XmlSecurityConfig();
                    INSTANCE.loadFromClasspath("security-config.xml");
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Package-private constructor for testing or custom configuration instances.
     */
    XmlSecurityConfig() {}

    public void loadFromClasspath(String resourceName) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                loadFromStream(in);
            } else {
                initFallbacks();
            }
        } catch (Exception e) {
            initFallbacks();
        }
    }

    public void loadFromStream(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Strict XXE hardening (Anthropic Skill: testing-for-xxe-injection-vulnerabilities)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document doc = factory.newDocumentBuilder().parse(in);
        doc.getDocumentElement().normalize();

        parseRegexRules(doc);
        parseSecurityFilter(doc);
        parseRateLimiting(doc);
    }

    private void parseRegexRules(Document doc) {
        NodeList rules = doc.getElementsByTagName("rule");
        for (int i = 0; i < rules.getLength(); i++) {
            Element el = (Element) rules.item(i);
            if (el.getParentNode() != null && "regex-rules".equals(el.getParentNode().getNodeName())) {
                String id = el.getAttribute("id");
                String pattern = el.getAttribute("pattern");
                if (!id.isBlank() && !pattern.isBlank()) {
                    regexStrings.put(id, pattern);
                    regexPatterns.put(id, Pattern.compile(pattern));
                }
            }
        }
    }

    private void parseSecurityFilter(Document doc) {
        NodeList filterNodes = doc.getElementsByTagName("security-filter");
        if (filterNodes.getLength() > 0) {
            Element filterEl = (Element) filterNodes.item(0);
            if (filterEl.hasAttribute("enabled")) {
                this.securityFilterEnabled = Boolean.parseBoolean(filterEl.getAttribute("enabled"));
            }

            NodeList headers = filterEl.getElementsByTagName("header");
            for (int i = 0; i < headers.getLength(); i++) {
                Element h = (Element) headers.item(i);
                securityHeaders.put(h.getAttribute("name"), h.getAttribute("value"));
            }

            NodeList paths = filterEl.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Element p = (Element) paths.item(i);
                cacheControlPaths.put(p.getAttribute("pattern"), p.getAttribute("value"));
            }

            NodeList csrfNodes = filterEl.getElementsByTagName("csrf");
            if (csrfNodes.getLength() > 0) {
                Element csrfEl = (Element) csrfNodes.item(0);
                if (csrfEl.hasAttribute("enabled")) {
                    this.csrfEnabled = Boolean.parseBoolean(csrfEl.getAttribute("enabled"));
                }
                setIfPresent(csrfEl, "header-name", val -> this.csrfHeaderName = val);
                setIfPresent(csrfEl, "param-name", val -> this.csrfParamName = val);
                setIfPresent(csrfEl, "cookie-name", val -> this.csrfCookieName = val);
                setIfPresent(csrfEl, "safe-methods", val -> {
                    this.csrfSafeMethods = Set.of(val.split(","));
                });
            }
        }
    }

    private void parseRateLimiting(Document doc) {
        NodeList rlNodes = doc.getElementsByTagName("rate-limiting");
        if (rlNodes.getLength() > 0) {
            Element rlEl = (Element) rlNodes.item(0);
            if (rlEl.hasAttribute("enabled")) {
                this.rateLimitingEnabled = Boolean.parseBoolean(rlEl.getAttribute("enabled"));
            }

            NodeList defNodes = rlEl.getElementsByTagName("defaults");
            if (defNodes.getLength() > 0) {
                Element defEl = (Element) defNodes.item(0);
                if (defEl.hasAttribute("limit")) this.defaultRateLimit = Integer.parseInt(defEl.getAttribute("limit"));
                if (defEl.hasAttribute("window-seconds")) this.defaultRateWindowSeconds = Integer.parseInt(defEl.getAttribute("window-seconds"));
            }

            NodeList rules = rlEl.getElementsByTagName("rule");
            for (int i = 0; i < rules.getLength(); i++) {
                Element r = (Element) rules.item(i);
                if (r.getParentNode() != null && "rules".equals(r.getParentNode().getNodeName())) {
                    String path = r.getAttribute("path");
                    String method = r.hasAttribute("method") ? r.getAttribute("method").toUpperCase() : "*";
                    int limit = r.hasAttribute("limit") ? Integer.parseInt(r.getAttribute("limit")) : this.defaultRateLimit;
                    int window = r.hasAttribute("window-seconds") ? Integer.parseInt(r.getAttribute("window-seconds")) : this.defaultRateWindowSeconds;
                    String desc = r.getAttribute("description");
                    rateLimitRules.add(new RateLimitRule(path, method, limit, window, desc));
                }
            }
        }
    }

    private void setIfPresent(Element parent, String tagName, java.util.function.Consumer<String> consumer) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            String text = list.item(0).getTextContent();
            if (text != null && !text.isBlank()) {
                consumer.accept(text.trim());
            }
        }
    }

    private void initFallbacks() {
        regexPatterns.put("category-color", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
        regexPatterns.put("safe-redirect", Pattern.compile("^/(?!/|\\\\)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]*$"));
        regexPatterns.put("csv-formula-injection", Pattern.compile("^[=+\\-@\\t\\r].*"));
        regexPatterns.put("safe-filename", Pattern.compile("^[a-zA-Z0-9_\\-. ]+$"));
        regexPatterns.put("backup-entry", Pattern.compile("^(focus-backup\\.json|attachments/\\d+\\.bin)$"));
    }

    public Pattern getRegexPattern(String ruleId) {
        return regexPatterns.computeIfAbsent(ruleId, k -> Pattern.compile(".*"));
    }

    public String getRegexString(String ruleId) {
        return regexStrings.getOrDefault(ruleId, ".*");
    }

    public boolean matchesRegex(String ruleId, CharSequence input) {
        if (input == null) return false;
        Pattern p = regexPatterns.get(ruleId);
        return p != null && p.matcher(input).matches();
    }

    public Map<String, String> getSecurityHeaders() {
        return Collections.unmodifiableMap(securityHeaders);
    }

    public Map<String, String> getCacheControlPaths() {
        return Collections.unmodifiableMap(cacheControlPaths);
    }

    public boolean isSecurityFilterEnabled() { return securityFilterEnabled; }
    public boolean isCsrfEnabled() { return csrfEnabled; }
    public String getCsrfHeaderName() { return csrfHeaderName; }
    public String getCsrfParamName() { return csrfParamName; }
    public String getCsrfCookieName() { return csrfCookieName; }
    public Set<String> getCsrfSafeMethods() { return csrfSafeMethods; }

    public boolean isRateLimitingEnabled() { return rateLimitingEnabled; }
    public int getDefaultRateLimit() { return defaultRateLimit; }
    public int getDefaultRateWindowSeconds() { return defaultRateWindowSeconds; }
    public List<RateLimitRule> getRateLimitRules() { return Collections.unmodifiableList(rateLimitRules); }

    /**
     * Finds the most specific matching rate limit rule for a given URI and HTTP method.
     */
    public RateLimitRule findMatchingRateLimitRule(String uri, String method) {
        if (uri == null) uri = "/";
        String reqMethod = method != null ? method.toUpperCase() : "GET";
        for (RateLimitRule rule : rateLimitRules) {
            boolean methodMatches = "*".equals(rule.method()) || rule.method().equalsIgnoreCase(reqMethod);
            boolean pathMatches = uri.startsWith(rule.path()) || (rule.path().contains("*") && matchesPathPattern(rule.path(), uri));
            if (methodMatches && pathMatches) {
                return rule;
            }
        }
        return new RateLimitRule("*", "*", defaultRateLimit, defaultRateWindowSeconds, "Global Default");
    }

    private boolean matchesPathPattern(String pattern, String path) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return path.matches(regex);
    }
}
