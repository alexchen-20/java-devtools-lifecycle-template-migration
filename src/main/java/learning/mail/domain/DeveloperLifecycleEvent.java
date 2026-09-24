package learning.mail.domain;

import java.util.Map;

public record DeveloperLifecycleEvent(
        String eventId,
        Kind kind,
        String project,
        String reference,
        String diagnostic,
        boolean successful) {

    public enum Kind { BUILD, RELEASE }

    public DeveloperLifecycleEvent {
        if (eventId == null || eventId.isBlank() || project == null || project.isBlank()) {
            throw new IllegalArgumentException("eventId and project are required");
        }
    }

    public Map<String, String> templateVars() {
        return Map.of(
                "project", project,
                "reference", reference == null ? "unavailable" : reference,
                "diagnostic", diagnostic == null ? "No diagnostic supplied" : diagnostic);
    }
}
