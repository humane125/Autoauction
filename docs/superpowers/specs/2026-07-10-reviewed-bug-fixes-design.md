# Reviewed Multi-Repo Bug Fixes Design

## Scope

Fix the eleven validated defects that do not require new Hypixel menu dumps, chat messages, or a product decision. Leave Bazaar order acknowledgement, scheduled-stop interruption policy, and auction-listing acknowledgement unchanged.

## Test API

- Treat an auction refresh as publishable only when every advertised page succeeds. Keep the previous complete snapshot after a partial refresh.
- Reject or safely acknowledge mod statistics for an account deleted after socket authentication instead of allowing a database foreign-key exception to escape.
- Handle release-file stream errors without terminating Node.
- Reconcile collected auctions by normalized item name and closest compatible price, preferring exact/close price matches over name-only matches.
- Forward dashboard `banUntil` and `banId` into status persistence.

## Alt Manager

- Compact Stop disables runtime scheduling while preserving account order, modules, repeat, wait state, and time entries.
- Account switching returns success only when a Microsoft login attempt was accepted for execution; asynchronous failure must remain observable to the handoff readiness path.
- Start after Pause resumes the stored WAIT deadline and cycle identity.
- An exceptional proxy lookup clears the installed proxy before reporting not-ready.

## AutoAuction

- Discard text frames from obsolete WebSocket connection generations.
- A receiver claim completes only after purse progress is positive; a zero delta remains pending until timeout.

## Constraints

- Preserve existing commands and protocol payloads.
- Do not add new Hypixel chat parsing or menu assumptions.
- Add regression coverage before each production fix.
- Keep changes scoped to the files owning each defect.

