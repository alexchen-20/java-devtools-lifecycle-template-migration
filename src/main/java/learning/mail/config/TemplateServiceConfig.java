package learning.mail.config;

public record TemplateServiceConfig(String apiKey, String namespace) {
    public TemplateServiceConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        if (namespace == null || !namespace.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("TEMPLATE_NAMESPACE must use lowercase letters, numbers, and hyphens");
        }
    }

    public static TemplateServiceConfig fromEnvironment() {
        String namespace = System.getenv().getOrDefault("TEMPLATE_NAMESPACE", "academy-devtools");
        return new TemplateServiceConfig(System.getenv("INFRAI_API_KEY"), namespace);
    }
}
