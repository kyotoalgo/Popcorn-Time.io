package pct.droid.installer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;

import com.google.gson.Gson;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import pct.droid.installer.utils.Progress.ProgressListener;


public class DownloadActivity extends ActionBarActivity {

    public final static int ANDROID_TV = 1;
    public final static int ANDROID_HANDSET = 0;
    private static OkHttpClient mHttpClient;

    private final String ANDROID_PACKAGE = "application/vnd.android.package-archive";
    private final String DATA_URL = "http://ci.popcorntime.io/android";

    private final Gson mGson = new Gson();

    private int deviceType = -1;

    @InjectView(R.id.progress_download)
    ProgressBar mProgressDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mHttpClient = getHttpClient();

        Bundle extras = getIntent().getExtras();
        if (extras != null){
            deviceType = extras.getInt("deviceType", 0);
        } else {
            finish();
        }

        mProgressDownload.setMax(100);
        mProgressDownload.setProgress(0);

        downloadInstallFile();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void downloadInstallFile() {

        final String abi;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.CPU_ABI.toLowerCase(Locale.US);
        } else {
            abi = Build.SUPPORTED_ABIS[0].toLowerCase(Locale.US);
        }

        final String variantStr;
        if (deviceType == ANDROID_TV) {
            variantStr = "tv";
        } else {
            variantStr = "mobile";
        }

        final String channelStr = "release";

        Request request = new Request.Builder()
                .url(DATA_URL + "/" + variantStr)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                //TODO: ERROR WHILE FETCHING UPDATE DATA
            }

            @Override
            public void onResponse(Response response) {
                try {
                    if (response.isSuccessful()) {
                        UpdaterData data = mGson.fromJson(response.body().string(), UpdaterData.class);
                        Map<String, Map<String, UpdaterData.Arch>> variant;

                        if (variantStr.equals("tv")) {
                            variant = data.tv;
                        } else {
                            variant = data.mobile;
                        }

                        UpdaterData.Arch channel = null;
                        if (variant.containsKey(channelStr) && variant.get(channelStr).containsKey(abi)) {
                            channel = variant.get(channelStr).get(abi);
                        }

                        downloadFile(channel.updateUrl);
                    } else {

                        //TODO: ERROR WHILE FETCHING UPDATE SERVER

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void downloadFile(final String location) {
        Request request = new Request.Builder()
                .url(location)
                .build();


        final ProgressListener progressListener = new ProgressListener() {
            @Override public void update(final long bytesRead, final long contentLength, boolean done) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        mProgressDownload.setProgress((int) ((100 * bytesRead) / contentLength));
                    }
                });
            }
        };

        mHttpClient.networkInterceptors().add(new Interceptor() {
            @Override public Response intercept(Chain chain) throws IOException {
                Response originalResponse = chain.proceed(chain.request());
                return originalResponse.newBuilder()
                        .body(new Progress.ProgressResponseBody(originalResponse.body(), progressListener))
                        .build();
            }
        });

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

                //TODO :: ERROR WHILE DOWNLOADING
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    String fileName = location.substring(location.lastIndexOf('/') + 1);
                    FileOutputStream fos = getApplicationContext().openFileOutput(fileName, Context.MODE_WORLD_READABLE);
                    fos.write(response.body().bytes());
                    fos.close();

                    String update_file_path = getApplicationContext().getFilesDir().getAbsolutePath() + "/" + fileName;

                    Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
                    notificationIntent.setDataAndType(Uri.parse("file://" + update_file_path), ANDROID_PACKAGE);
                    startActivity(notificationIntent);
                } else {
                    //TODO :: ERROR WHILE DOWNLOADING
                }
            }
        });

    }

    public static OkHttpClient getHttpClient() {
        if (mHttpClient == null) {
            mHttpClient = new OkHttpClient();
        }
        return mHttpClient;
    }
}
