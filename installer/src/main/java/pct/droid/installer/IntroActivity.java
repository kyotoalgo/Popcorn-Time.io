package pct.droid.installer;

import android.content.Intent;
import android.os.Bundle;


import com.github.paolorotolo.appintro.AppIntro2;

import pct.droid.installer.slides.FirstSlide;
import pct.droid.installer.slides.SecondSlide;
import pct.droid.installer.slides.ThirdSlide;


public class IntroActivity extends AppIntro2 {

    private int deviceType = -1;

    @Override
    public void init(Bundle bundle) {
        addSlide(new FirstSlide(), getApplicationContext());
        addSlide(new SecondSlide(), getApplicationContext());
        addSlide(new ThirdSlide(), getApplicationContext());

        // OPTIONAL METHODS
        // Override bar/separator color
        //setBarColor(Color.parseColor("#3F51B5"));
        //setSeparatorColor(Color.parseColor("#2196F3"));

        // Hide Skip button
        //showSkipButton(false);

        // Turn vibration on and set intensity
        setVibrate(false);
        setVibrateIntensity(30);
        //setCustomTransformer(new PageTransformer());
        deviceType = DownloadActivity.ANDROID_HANDSET;
    }

    public int getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(int deviceType) {
        this.deviceType = deviceType;
    }

    @Override
    public void onDonePressed() {
        Intent i = new Intent(getApplicationContext(), DownloadActivity.class);
        i.putExtra("deviceType", deviceType);
        startActivity(i);
    }
}
