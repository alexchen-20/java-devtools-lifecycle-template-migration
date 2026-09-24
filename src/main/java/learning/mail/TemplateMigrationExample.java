package learning.mail;

import learning.mail.config.TemplateServiceConfig;
import learning.mail.domain.DeveloperLifecycleEvent;
import learning.mail.domain.ManagedTemplate;
import learning.mail.infra.InfraiTemplateClient;
import learning.mail.service.LifecycleTemplatePolicy;

import java.time.Instant;
import java.util.Map;

public final class TemplateMigrationExample {
    private TemplateMigrationExample() {}

    public static void main(String[] args) throws Exception {
        TemplateServiceConfig config = TemplateServiceConfig.fromEnvironment();
        DeveloperLifecycleEvent event = new DeveloperLifecycleEvent(
                "build-1842", DeveloperLifecycleEvent.Kind.BUILD, "Java Foundations",
                "main@8f21c7a", "Lesson compiler failed at module 3", false);
        ManagedTemplate template = new LifecycleTemplatePolicy().choose(event).orElseThrow();

        String version = Long.toString(Instant.now().toEpochMilli());
        String name = config.namespace() + "-" + template.key() + "-" + version;
        String operationKey = event.eventId() + "-" + version;
        InfraiTemplateClient infrai = new InfraiTemplateClient(config.apiKey());
        Map<String, Object> created = infrai.create(name, template, operationKey + "-create");
        String templateId = String.valueOf(created.get("template_id"));
        infrai.update(templateId, template, operationKey + "-update");
        Map<String, Object> preview = infrai.preview(templateId, event.templateVars());

        System.out.println("Managed template " + templateId + " for " + event.eventId());
        System.out.println("Preview: " + preview);
    }
}
