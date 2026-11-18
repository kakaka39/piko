package app.morphe.extension.newx.mute;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import app.morphe.extension.crimera.settings.CustomScreenFragment;
import app.morphe.extension.shared.StringRef;
public final class KeywordMuteFragment extends CustomScreenFragment {
 @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
  Button button = new Button(getActivity());
  button.setText(StringRef.str("piko_pref_strict_keyword_mute_keywords"));
  button.setOnClickListener(view -> new KeywordMutePreference(getActivity(), false).showManager());
  return button;
 }
}
