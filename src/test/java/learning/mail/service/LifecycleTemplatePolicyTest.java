package learning.mail.service;

import learning.mail.domain.DeveloperLifecycleEvent;
import learning.mail.domain.ManagedTemplate;

public final class LifecycleTemplatePolicyTest {
    public static void main(String[] args) {
        LifecycleTemplatePolicy policy = new LifecycleTemplatePolicy();

        var greenBuild = new DeveloperLifecycleEvent("b-1", DeveloperLifecycleEvent.Kind.BUILD,
                "Java Foundations", "main", null, true);
        check(policy.choose(greenBuild).isEmpty(), "successful builds should stay quiet");

        var failedBuild = new DeveloperLifecycleEvent("b-2", DeveloperLifecycleEvent.Kind.BUILD,
                "Java Foundations", "main", "compiler output", false);
        ManagedTemplate diagnostic = policy.choose(failedBuild).orElseThrow();
        check(diagnostic.key().equals("build-diagnostic"), "failed builds need the diagnostic template");
        check(diagnostic.templateVars().get("diagnostic").equals("compiler output"), "diagnostic must remain visible");

        var release = new DeveloperLifecycleEvent("r-1", DeveloperLifecycleEvent.Kind.RELEASE,
                "Java Foundations", "v2.4.0", null, true);
        check(policy.choose(release).orElseThrow().key().equals("release-published"),
                "a release needs the release template");
        System.out.println("Lifecycle template policy: PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
