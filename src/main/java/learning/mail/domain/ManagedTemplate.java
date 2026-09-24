package learning.mail.domain;

import java.util.Map;

public record ManagedTemplate(String key, String subject, String html, Map<String, String> templateVars) {
}
