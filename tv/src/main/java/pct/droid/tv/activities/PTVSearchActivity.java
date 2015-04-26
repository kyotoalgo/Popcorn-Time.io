package pct.droid.tv.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import pct.droid.tv.R;


public class PTVSearchActivity extends Activity {

	public static Intent startActivity(Activity activity) {
		Intent intent = new Intent(activity, PTVSearchActivity.class);
		activity.startActivity(intent);
		return intent;
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);
	}
}