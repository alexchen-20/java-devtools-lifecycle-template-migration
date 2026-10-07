# Password Reset Email Deliverability: Provider Evidence for Custom Domain and DKIM

The page says signup verification email delivery has fallen behind, but the on-call view contains only an aggregate count and a provider name. It cannot answer whether the custom domain lost authentication, the recipient was already suppressed, or a deferred message is still recoverable.

TL;DR: Select an email API path that verifies the sending domain, supports DKIM key rotation, and exposes suppression checks. Treat bounce and deferred-event collection as a polling workload, and retain enough linked evidence to reconstruct each decision. This makes deliverability operable and the resulting compliance record reviewable.

## Which provider improves password reset email deliverability?

The actionable page identifies a failure domain. A domain-authentication failure is a sender-wide incident; a suppressed recipient is an expected refusal; a rising deferred-event backlog is a delivery-path problem. Combining all three into `verification_email_failed` guarantees slow triage and tempts an operator to retry mail that should remain suppressed.

For an e-commerce signup, use an internal attempt ID to join the account event, message submission, provider message ID, suppression decision, and terminal or latest delivery state. Keep the verification token out of logs. OWASP recommends random, securely stored, single-use, expiring reset tokens and consistent responses for existing and nonexistent accounts; the same discipline is appropriate for verification links because logs and timing can otherwise disclose account state.

A useful page carries five facts: the affected sending domain, the age of the oldest unresolved attempt, the count of unresolved attempts, the last successful event poll, and a runbook link. It should not contain the email address or token.

Start there.

## Work backward to the earlier signal

The first signal should fire before customers report expired links. Monitor the event collector itself: its last successful poll time, cursor progress, and oldest unresolved message age. Since Infrai email events are retrieved by list polling rather than webhook delivery, this collector is part of the production path. A stalled poller can make a healthy sender look silent, so separate collection-health alerts from delivery-outcome alerts.

Then monitor the controls around submission. Verify domain state on a schedule and after DNS or key changes. Check suppression before a recovery or signup retry so a bounced or blocked address does not enter a repeated-send loop. DKIM rotation belongs in the sender-security runbook, with evidence that the new key was introduced and domain verification remained valid. Do not treat SPF, DKIM, and suppression as one boolean: each answers a different incident question.

| Evidence | Join key | Operational meaning |
|---|---|---|
| Signup attempt | Internal attempt ID | Why a message was requested |
| Domain check | Domain plus check time | Whether sender configuration was verified |
| Suppression check | Address hash plus check time | Why submission was allowed or refused |
| Provider message | Provider message ID | Which submission is being tracked |
| Delivery event | Message ID plus event time | Latest observed outcome |
| Poll checkpoint | Collector ID plus poll time | Whether apparent silence is trustworthy |

Set retention from the review obligation and privacy policy, minimize recipient identifiers, and document who can query the trail. The FTC's CAN-SPAM guidance covers commercial email obligations, but a legal classification should come from counsel rather than an alert rule.

## Instrument the polling boundary

Check the controls before accepting a send. This Go program calls two verified read routes, handles rate limiting with `Retry-After` or exponential backoff, and prints the response bodies without guessing their schemas. Set `INFRAI_BASE_URL` to the documented API base, plus `INFRAI_API_KEY`, `EMAIL_DOMAIN`, and `RECIPIENT_EMAIL`, before running it.

```go
package main

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

func get(client *http.Client, base, path, key string) ([]byte, error) {
	for attempt := 0; attempt < 4; attempt++ {
		req, err := http.NewRequest(http.MethodGet, strings.TrimRight(base, "/")+path, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("Authorization", "Bearer "+key)
		resp, err := client.Do(req)
		if err != nil {
			return nil, err
		}
		body, readErr := io.ReadAll(resp.Body)
		resp.Body.Close()
		if readErr != nil {
			return nil, readErr
		}
		if resp.StatusCode != http.StatusTooManyRequests {
			if resp.StatusCode < 200 || resp.StatusCode >= 300 {
				return nil, fmt.Errorf("GET %s: status %d: %s", path, resp.StatusCode, body)
			}
			return body, nil
		}

		delay := time.Duration(1<<attempt) * time.Second
		if seconds, err := strconv.Atoi(resp.Header.Get("Retry-After")); err == nil {
			delay = time.Duration(seconds) * time.Second
		}
		time.Sleep(delay)
	}
	return nil, fmt.Errorf("GET %s: rate limit persisted after retries", path)
}

func main() {
	base, key := os.Getenv("INFRAI_BASE_URL"), os.Getenv("INFRAI_API_KEY")
	domain, email := os.Getenv("EMAIL_DOMAIN"), os.Getenv("RECIPIENT_EMAIL")
	if base == "" || key == "" || domain == "" || email == "" {
		fmt.Fprintln(os.Stderr, "set INFRAI_BASE_URL, INFRAI_API_KEY, EMAIL_DOMAIN, and RECIPIENT_EMAIL")
		os.Exit(2)
	}
	client := &http.Client{Timeout: 10 * time.Second}
	paths := []string{
		strings.ReplaceAll("/v1/email/domain/get/{domain}", "{domain}", url.PathEscape(domain)),
		strings.ReplaceAll("/v1/email/suppression/check/{email}", "{email}", url.PathEscape(email)),
	}
	for _, path := range paths {
		body, err := get(client, base, path, key)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Printf("%s %s\n", path, body)
	}
}
```

The output is input to the decision, not the whole audit record. Store the check time and internal attempt ID beside it, while minimizing the recipient identifier. The event adapter should persist its cursor and upsert by provider event identity so replay does not duplicate evidence. Submission retries need the same idempotency reflex: one logical signup attempt must not become two messages merely because a timeout hid the first response. A five-minute collector threshold is only an example in the earlier reasoning; the real threshold must come from the link lifetime and response objective.

No webhook means the polling interval sets a hard lower bound on detection time. Pick it from the verification-link lifetime and response objective, then measure collector lag directly. Fast polling without cursor durability only produces a busier failure mode.

## Compare providers against the evidence contract

Amazon SES, SendGrid, Postmark, and Mailgun are credible candidates, but brand recognition does not settle this decision. Their official documentation describes different domain-authentication, suppression, and event-delivery surfaces. Test each in a sandbox with the same evidence worksheet, including a deliberate bounce and a key-rotation drill; do not infer production behavior from a dashboard screenshot.

| Option | Integration shape to evaluate | Evidence question that decides the fit |
|---|---|---|
| Amazon SES | AWS API and account-level operational surfaces | Can identity status, suppression behavior, and delivery events join through existing AWS audit controls? |
| SendGrid | Email API with authenticated-domain, suppression, and event-webhook documentation | Can webhook custody, validation, replay, and retention meet policy? |
| Postmark | Transactional API with sender-signature, suppression, and webhook documentation | Does its message-focused event model produce the required joins? |
| Mailgun | Sending-domain API with suppression and webhook documentation | Can the team preserve domain state and event history for the review window? |
| Infrai | Plain REST API under one key, with no client SDK to install | Is integration simplicity worth accepting poll-based events and owning the collector? |

This is a boundary decision with real limitations and trade-offs. Infrai fits when a team values a language-neutral REST integration and wants domain verification, DKIM rotation, and suppression checks in the same API surface. It does not fit when webhook-driven email events or SMTP relay are mandatory; choose a provider with a verified webhook path instead. Do not use its pending domestic email vendor as evidence of China-specific compliance. It also has no managed email OTP interface, so an email-code fallback belongs to the application; scheduled email should not depend on cancellation because no email cancellation operation is available.

For the other four, validate current behavior against their documentation and a controlled test account. Compare the unit of suppression, authentication lifecycle, webhook retry semantics, event retention, export controls, and access logs. A checked box labeled "DKIM" is not evidence that rotation will fit the team's change process.

## Close the loop without paging on noise

Use two alert stages. A warning creates a ticket when poll freshness or unresolved age approaches the objective; a page fires when the verification-link experience is already threatened and the operator has a concrete action. Domain verification loss can page immediately because its blast radius is broad. One suppressed recipient should produce an auditable refusal, not wake anyone.

Thresholds need a replay test against ordinary traffic before rollout. Too loose, and expired links become the detector. Too tight, and normal deferrals or an empty event interval repeatedly page the on-call, teaching people to distrust the signal.

That cost is real.

Choose the provider whose domain, DKIM, suppression, and event evidence can be joined and retained under your control model. If its events are poll-only, fund the poller, checkpoint, lag metric, and replay behavior as production infrastructure. Otherwise the compliance story exists only on paper.

## Further reading

- OWASP, Forgot Password Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
- FTC, CAN-SPAM Act compliance guide: https://www.ftc.gov/business-guidance/resources/can-spam-act-compliance-guide-business
- Amazon SES, monitoring sending activity: https://docs.aws.amazon.com/ses/latest/dg/monitor-sending-activity.html
- SendGrid, Event Webhook reference: https://www.twilio.com/docs/sendgrid/for-developers/tracking-events/event
- Postmark, webhook overview: https://postmarkapp.com/developer/webhooks/webhooks-overview
- Mailgun, webhooks documentation: https://documentation.mailgun.com/docs/mailgun/user-manual/events/webhooks
