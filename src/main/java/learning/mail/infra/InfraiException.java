package learning.mail.infra;

import java.io.IOException;
import java.util.Map;

public final class InfraiException extends IOException {
    private final int status;
    private final Map<String, Object> details;

    public InfraiException(int status, Map<String, Object> details) {
        super(String.valueOf(details));
        this.status = status;
        this.details = Map.copyOf(details);
    }

    public int status() { return status; }
    public Map<String, Object> details() { return details; }
}
