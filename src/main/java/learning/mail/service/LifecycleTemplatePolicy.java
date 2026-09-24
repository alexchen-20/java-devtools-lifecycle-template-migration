package learning.mail.service;

import learning.mail.domain.DeveloperLifecycleEvent;
import learning.mail.domain.ManagedTemplate;

import java.util.Map;
import java.util.Optional;

public final class LifecycleTemplatePolicy {
    public Optional<ManagedTemplate> choose(DeveloperLifecycleEvent event) {
        Map<String, String> vars = event.templateVars();
        if (event.kind() == DeveloperLifecycleEvent.Kind.BUILD && event.successful()) {
            return Optional.empty();
        }
        if (event.kind() == DeveloperLifecycleEvent.Kind.BUILD) {
            return Optional.of(new ManagedTemplate(
                    "build-diagnostic",
                    "Build needs attention: {{project}}",
                    "<h1>Build diagnostic</h1><p>Project: {{project}}</p><p>Ref: {{reference}}</p><pre>{{diagnostic}}</pre>",
                    vars));
        }
        return Optional.of(new ManagedTemplate(
                "release-published",
                "Release published: {{project}} {{reference}}",
                "<h1>Your release is published</h1><p>{{project}} {{reference}} is ready for learners.</p>",
                vars));
    }
}
