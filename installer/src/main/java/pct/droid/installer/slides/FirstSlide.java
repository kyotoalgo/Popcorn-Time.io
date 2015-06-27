package pct.droid.installer.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import pct.droid.installer.R;

/**
 * Created by Seïfane on 6/25/2015.
 */
public class FirstSlide extends Fragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_slide1, container, false);
        return rootView;
    }
}
