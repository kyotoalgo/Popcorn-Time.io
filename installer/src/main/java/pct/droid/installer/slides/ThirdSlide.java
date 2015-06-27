package pct.droid.installer.slides;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import pct.droid.installer.DownloadActivity;
import pct.droid.installer.IntroActivity;
import pct.droid.installer.R;

/**
 * Created by Seïfane on 6/25/2015.
 */
public class ThirdSlide extends Fragment {

    @InjectView(R.id.text_version)
    TextView mTextVersion;
    @InjectView(R.id.text_arch)
    TextView mTextArch;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_slide3, container, false);
        ButterKnife.inject(this, rootView);

        updateVersion();

        String abi;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.CPU_ABI.toLowerCase(Locale.US);
        } else {
            abi = Build.SUPPORTED_ABIS[0].toLowerCase(Locale.US);
        }

        mTextArch.setText("Arch : " + abi);

        return rootView;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser){
            updateVersion();
        }
    }

    private void updateVersion(){
        IntroActivity activity = (IntroActivity) getActivity();
        int choosedVersion = activity.getDeviceType();

        if (choosedVersion == DownloadActivity.ANDROID_HANDSET){
            mTextVersion.setText("Version : Phone or Tablet");
        } else {
            mTextVersion.setText("Version : Android TV");
        }
    }
}
