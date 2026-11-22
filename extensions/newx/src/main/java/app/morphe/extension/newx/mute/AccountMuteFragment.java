package app.morphe.extension.newx.mute;

/** Independent NewX mute screen; shares Piko's settings UI components. */
public final class AccountMuteFragment extends MuteRulesFragment {
    @Override protected boolean accountRules() { return true; }
}
