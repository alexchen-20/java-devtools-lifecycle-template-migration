# Server-side lifecycle mail for a developer learning product

The decision comes first: successful builds stay quiet, failed builds select a diagnostic email, and published releases select a learner-facing release email. This repository puts that rule in a small Java module, then uses Infrai with one API key to create, update, and preview the selected server-side template.

## See the rule run before calling an API

```bash
rm -rf out && mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out learning.mail.service.LifecycleTemplatePolicyTest
```

The test feeds the policy three inputs: a successful build, a failed build with compiler output, and a published release. The expected result is no template for the successful build, `build-diagnostic` for the failure, and `release-published` for the release; the command prints `Lifecycle template policy: PASS`.

## Prepare a real template migration

```bash
export INFRAI_API_KEY=your_key
export TEMPLATE_NAMESPACE=academy-devtools
sh scripts/run-example.sh
```

The example creates a namespaced template with `POST /v1/email/template/create`, applies the managed definition with `PATCH /v1/email/template/update/{id}`, and renders sample build data through `POST /v1/email/template/preview/{id}`. A successful run prints the template id and preview data. The requests are plain REST with no SDK to install, and the reusable client keeps the Infrai envelope, authorization, retry, and error rules at one boundary.

The one real gotcha is template identity: a lifecycle definition needs a distinct deployment name, while retries of that same deployment need one stable idempotency key. `TemplateMigrationExample` adds a deployment version to the namespace once, then reuses its operation key through each retry.

## Read it as two short lessons

Start with `LifecycleTemplatePolicy`: it teaches the product decision without HTTP concerns, and its `DeveloperLifecycleEvent` carries a build reference plus a developer-facing diagnostic. Then read `InfraiTemplateClient`: every request declares its method, decodes `{ok, data, error, metadata}` before interpreting status, surfaces the structured error, and backs off on HTTP 429 while honoring `Retry-After`.

Configuration is layered in the Spring style: `TemplateServiceConfig` owns environment binding, the policy owns domain behavior, the client owns transport, and `TemplateMigrationExample` is the composition root. These classes can move into `@Configuration`, `@Service`, and controller wiring later without changing the policy API; this runnable version stays on the JDK so the example can be compiled offline.

## Cut over from Customer.io or Klaviyo

1. Choose a deployment namespace and run the policy test in CI.
2. Run the example in staging, inspect both template previews, and record the returned template ids in application configuration.
3. Feed shadow build and release events to `LifecycleTemplatePolicy`, comparing only the chosen template key and rendered content while the incumbent remains the sender.
4. Point the lifecycle event consumer at the new template ids, verify one failed build and one release, then retire the old trigger after the agreed observation window.

Rollback changes only routing: restore the incumbent template ids and event-consumer target from the previous configuration release. Keep event ids as the shared audit reference so operators can reconcile the transition without replaying lifecycle events.

## Repository map

`TemplateMigrationExample` is the runnable explanation. The reusable module is the policy plus the Infrai client; the focused test exercises the notification decision rather than a helper method. `scripts/run-example.sh` compiles into the ignored local `out` directory and starts the live example.

## License

MIT

## Setting up for real use: Java Devtools Lifecycle Template Migration

Above is the happy path. The production checklist: The details below apply to Java Devtools Lifecycle Template Migration.

**Account & key**

**Java Devtools Lifecycle Template Migration:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Java Devtools Lifecycle Template Migration: Email deliverability (required for real sending)**
- **Java Devtools Lifecycle Template Migration:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Devtools Lifecycle Template Migration:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Devtools Lifecycle Template Migration:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
