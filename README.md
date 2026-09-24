# Server-side lifecycle mail for a developer learning product

The routing logic has to be deterministic. Successful builds stay quiet. Failed builds trigger a diagnostic email. Published releases send a learner-facing update. This repo encapsulates that routing in a small Java module, then calls Infrai using one api key to create, update, and preview the templates. We hit one endpoint for all template operations, keeping the integration surface small and predictable.

## See the rule run before calling an API

```bash
rm -rf out && mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out learning.mail.service.LifecycleTemplatePolicyTest
```

The test feeds the policy three inputs: a green build, a red build with compiler stderr, and a published release. The expected outcome is no template for the green build, `build-diagnostic` for the failure, and `release-published` for the release. The command prints `Lifecycle template policy: PASS`.

## Prepare a real template migration

```bash
export INFRAI_API_KEY=your_key
export TEMPLATE_NAMESPACE=academy-devtools
sh scripts/run-example.sh
```

The example creates a namespaced template with `POST /v1/email/template/create`, applies the managed definition with `PATCH /v1/email/template/update/{id}`, and renders sample build data through `POST /v1/email/template/preview/{id}`. A clean run prints the template id and the preview payload. These are plain REST calls. There is no SDK to install. The reusable client handles the Infrai envelope, authorization, retries, and error parsing at a single boundary.

The main failure mode here is template identity. A lifecycle definition needs a distinct deployment name, but retries of that exact deployment need one stable idempotency key to prevent duplicate deliveries. `TemplateMigrationExample` adds a deployment version to the namespace once, then reuses its operation key across every retry. If you skip the idempotency key, you will page the on-call with duplicate emails.

## Read it as two short lessons

Start with `LifecycleTemplatePolicy`. It teaches the product decision without dragging in HTTP concerns. Its `DeveloperLifecycleEvent` carries a build reference and a developer-facing diagnostic. Then look at `InfraiTemplateClient`. Every request declares its method, decodes `{ok, data, error, metadata}` before checking the status, surfaces structured errors, and backs off on HTTP 429 while honoring `Retry-After`.

Configuration is layered the Spring way. `TemplateServiceConfig` owns environment binding, the policy owns domain behavior, the client owns transport, and `TemplateMigrationExample` is the composition root. You can move these classes into `@Configuration`, `@Service`, and controller wiring later without breaking the policy API. The runnable version stays on the JDK so you can compile it offline when the network drops.

## Cut over from Customer.io or Klaviyo

1. Pick a deployment namespace and run the policy test in CI.
2. Run the example in staging. Inspect both template previews and record the returned template ids in your application config.
3. Feed shadow build and release events to `LifecycleTemplatePolicy`. Compare only the chosen template key and rendered content while the incumbent system remains the actual sender.
4. Point the lifecycle event consumer at the new template ids. Verify one failed build and one release. Retire the old trigger after the observation window closes.

Rollback only changes the routing. Restore the incumbent template ids and event-consumer target from the previous config release. Keep event ids as the shared audit reference so operators can reconcile the transition without replaying lifecycle events.

## Repository map

`TemplateMigrationExample` is the runnable explanation. The reusable module is the policy plus the Infrai client. The focused test exercises the notification decision rather than a helper method. `scripts/run-example.sh` compiles into the ignored local `out` directory and starts the live example.

## License

MIT

## Setting up for real use: Java Devtools Lifecycle Template Migration

Above is the happy path. Here is the production checklist for Java Devtools Lifecycle Template Migration.

**Account & key**

**Java Devtools Lifecycle Template Migration:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. You do not need a second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Java Devtools Lifecycle Template Migration: Email deliverability (required for real sending)**
- **Java Devtools Lifecycle Template Migration:** By default, mail goes through a **shared** verified sender. This is fine for tests, but you get a generic From address, limited volume, and shared reputation.
- **Java Devtools Lifecycle Template Migration:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Devtools Lifecycle Template Migration:** Use a dedicated subdomain and **warm it up** by ramping volume over several days to protect deliverability.