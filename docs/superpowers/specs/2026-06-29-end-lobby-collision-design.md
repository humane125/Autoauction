# End Lobby Collision Design

## Goal
When a macro account lands in The End and sees another registered account in the same tablist, AutoAuction should move only the newly active account to a different lobby.

## Behavior
- The API sends every registered Minecraft username to each authenticated mod socket.
- The mod stores the account names locally, case-insensitive, including offline accounts.
- While Nebula combat macro is desired on and observed on, the mod checks the current tablist.
- If the tablist area is `The End` and another registered username is visible, the current account starts a lobby switch.
- A switch runs `ensureOff()`, sends `/is`, waits for the player to remain available briefly, then runs `ensureOn()`.
- After Nebula returns the account to The End, detection runs again. If another registered account is still present, it switches again.

## Guards
- The current account is ignored when matching tablist names.
- Detection only runs in The End.
- Detection only runs when macro state is active enough to avoid fighting manual disables.
- A cooldown prevents duplicate switch starts while a prior `/is` or Nebula state change is still settling.
- Older accounts already farming in the lobby should not leave merely because another account arrived; the account that just re-enabled macro from this flow is eligible for the next scan.

## Testing
- Unit test username list parsing and socket dispatch.
- Unit test tablist collision matching, current-account exclusion, and The End gating.
- Unit test the lobby switch state machine command order: toggle macro off, `/is`, toggle macro on.
- Run full mod tests and API tests before build/copy.
