package pct.droid.installer.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import butterknife.ButterKnife;
import butterknife.InjectView;
import pct.droid.installer.DownloadActivity;
import pct.droid.installer.IntroActivity;
import pct.droid.installer.R;


/**
 * Created by Seïfane on 6/25/2015.
 */
public class SecondSlide extends Fragment {

    @InjectView(R.id.group_version_type)
    RadioGroup mGroupVersions;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_slide2, container, false);
        ButterKnife.inject(this, rootView);
        final IntroActivity activity = (IntroActivity) getActivity();

        mGroupVersions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.version_tv){
                    activity.setDeviceType(DownloadActivity.ANDROID_TV);
                } else if (i == R.id.version_handset){
                    activity.setDeviceType(DownloadActivity.ANDROID_HANDSET);
                }
            }
        });
        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.reset(this);
    }
}
