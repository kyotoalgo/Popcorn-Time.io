/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.fragments;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import pct.droid.R;
import pct.droid.activities.BeamPlayerActivity;
import pct.droid.activities.VideoPlayerActivity;
import pct.droid.base.beaming.BeamDeviceListener;
import pct.droid.base.beaming.BeamManager;
import pct.droid.base.beaming.BeamPlayerNotificationService;
import pct.droid.base.torrent.DownloadStatus;
import pct.droid.base.torrent.StreamInfo;
import pct.droid.base.torrent.TorrentService;
import pct.droid.base.utils.AnimUtils;
import pct.droid.base.utils.PixelUtils;
import pct.droid.base.utils.VersionUtils;
import pct.droid.dialogfragments.LoadingBeamingDialogFragment;
import pct.droid.dialogfragments.OptionDialogFragment;
import pct.droid.widget.SeekBar;
import timber.log.Timber;

public class BeamPlayerFragment extends Fragment implements TorrentService.Listener {

    public static final int REFRESH_INTERVAL_MS = (int) TimeUnit.SECONDS.toMillis(1);

    private StreamInfo mStreamInfo;
    private Long mResumePosition;
    private BeamPlayerActivity mActivity;
    private BeamManager mBeamManager = BeamManager.getInstance(getActivity());
    private MediaControl mMediaControl;
    private VolumeControl mVolumeControl;
    private boolean mHasVolumeControl = true, mHasSeekControl = true, mIsPlaying = false, mIsUserSeeking = false, mProcessingSeeking = false;
    private int mRetries = 0;
    private long mTotalTimeDuration = 0;
    private Float mDownloadProgress = 0f;
    private LoadingBeamingDialogFragment mLoadingDialog;
    private ScheduledThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(2);
    private ScheduledFuture mTask;

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.sliding_layout)
    SlidingUpPanelLayout mPanel;
    @InjectView(R.id.play_button)
    ImageButton mPlayButton;
    @InjectView(R.id.cover_image)
    ImageView mCoverImage;
    @InjectView(R.id.seekbar)
    SeekBar mSeekBar;
    @InjectView(R.id.volumebar)
    SeekBar mVolumeBar;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_beamplayer, container, false);
    }

    @Override
    public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        ButterKnife.inject(this, v);

        mToolbar.getBackground().setAlpha(0);
        mToolbar.setNavigationIcon(R.drawable.abc_ic_clear_mtrl_alpha);
        mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mVolumeBar.setOnSeekBarChangeListener(mVolumeBarChangeListener);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (BeamPlayerActivity) getActivity();
        mActivity.setSupportActionBar(mToolbar);

        mLoadingDialog = LoadingBeamingDialogFragment.newInstance();
        mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                closePlayer();
            }
        });
        mLoadingDialog.show(getChildFragmentManager(), "overlay_fragment");

        mResumePosition = mActivity.getResumePosition();

        mStreamInfo = mActivity.getInfo();
        int paletteColor = mStreamInfo.getPaletteColor();

        if (paletteColor == -1) {
            paletteColor = getResources().getColor(R.color.primary);
        }

        LayerDrawable progressDrawable;
        LayerDrawable volumeDrawable;
        if (!VersionUtils.isLollipop()) {
            progressDrawable = (LayerDrawable) getResources().getDrawable(R.drawable.scrubber_progress_horizontal_bigtrack);
            volumeDrawable = (LayerDrawable) getResources().getDrawable(R.drawable.scrubber_progress_horizontal);
        } else {
            if (mVolumeBar.getProgressDrawable() instanceof StateListDrawable) {
                StateListDrawable stateListDrawable = (StateListDrawable) mVolumeBar.getProgressDrawable();
                volumeDrawable = (LayerDrawable) stateListDrawable.getCurrent();
            } else {
                volumeDrawable = (LayerDrawable) mVolumeBar.getProgressDrawable();
            }

            progressDrawable = (LayerDrawable) getResources().getDrawable(R.drawable.progress_horizontal_material);
        }

        if(volumeDrawable == null) {
            volumeDrawable = (LayerDrawable) progressDrawable.mutate();
        }

        progressDrawable.findDrawableByLayerId(android.R.id.background).setColorFilter(getResources().getColor(R.color.beamplayer_seekbar_track), PorterDuff.Mode.SRC_IN);
        progressDrawable.findDrawableByLayerId(android.R.id.progress).setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);
        progressDrawable.findDrawableByLayerId(android.R.id.secondaryProgress).setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);

        volumeDrawable.findDrawableByLayerId(android.R.id.background).setColorFilter(getResources().getColor(R.color.beamplayer_seekbar_track), PorterDuff.Mode.SRC_IN);
        volumeDrawable.findDrawableByLayerId(android.R.id.progress).setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);
        volumeDrawable.findDrawableByLayerId(android.R.id.secondaryProgress).setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);

        mSeekBar.setProgressDrawable(progressDrawable);
        mSeekBar.getThumbDrawable().setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);

        mVolumeBar.setProgressDrawable(volumeDrawable);
        mVolumeBar.getThumbDrawable().setColorFilter(paletteColor, PorterDuff.Mode.SRC_IN);

        if (!VersionUtils.isJellyBean()) {
            mPlayButton.setBackgroundDrawable(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, paletteColor));
        } else {
            mPlayButton.setBackground(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, paletteColor));
        }

        if (mStreamInfo.getImageUrl() != null && !mStreamInfo.getImageUrl().equals("")) {
            Picasso.with(mCoverImage.getContext()).load(mStreamInfo.getImageUrl())
                .into(mCoverImage, new Callback() {
                    @Override
                    public void onSuccess() {
                        AnimUtils.fadeIn(mCoverImage);
                    }

                    @Override
                    public void onError() {
                    }
                });
        }

        mActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mActivity.getSupportActionBar().setTitle("");

        try {
            if (!mBeamManager.getConnectedDevice().hasCapability(MediaControl.Position) || !mBeamManager.getConnectedDevice().hasCapability(MediaControl.Seek) || !mBeamManager.getConnectedDevice().hasCapability(MediaControl.Duration)) {
                mHasSeekControl = false;
                mSeekBar.setVisibility(View.INVISIBLE);
            }

            if (!mBeamManager.getConnectedDevice().hasCapability(VolumeControl.Volume_Get) || !mBeamManager.getConnectedDevice().hasCapability(VolumeControl.Volume_Set) || !mBeamManager.getConnectedDevice().hasCapability(VolumeControl.Volume_Subscribe)) {
                mHasVolumeControl = false;
                mPanel.setEnabled(false);
                mPanel.setTouchEnabled(false);
            }

            if (!mBeamManager.getConnectedDevice().hasCapability(MediaControl.Pause)) {
                mPlayButton.setEnabled(false);
            }

            startVideo();
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.unknown_error, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        Intent intent = new Intent( getActivity(), BeamPlayerNotificationService.class );
        intent.setAction(mIsPlaying ? BeamPlayerNotificationService.ACTION_PLAY : BeamPlayerNotificationService.ACTION_PAUSE);
        getActivity().startService(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        BeamManager.getInstance(getActivity()).addDeviceListener(mDeviceListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        BeamManager manager = BeamManager.getInstance(getActivity());

        manager.removeDeviceListener(mDeviceListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Intent intent = new Intent( getActivity(), BeamPlayerNotificationService.class );
        getActivity().stopService(intent);
    }

    private void startVideo() {
        mBeamManager.playVideo(mStreamInfo, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                mMediaControl = object.mediaControl;

                mMediaControl.subscribePlayState(mPlayStateListener);
                mMediaControl.getPlayState(mPlayStateListener);

                if (mHasVolumeControl) {
                    mVolumeControl = BeamManager.getInstance(getActivity()).getVolumeControl();
                    mVolumeControl.subscribeVolume(mVolumeListener);
                    mVolumeControl.getVolume(mVolumeListener);
                }

                if (mHasSeekControl) {
                    startUpdating();
                    mMediaControl.getDuration(mDurationListener);
                }

                if(mResumePosition > 0) {
                    mMediaControl.seek(mResumePosition, null);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                Timber.e(error.getCause(), error.getMessage());
                if (mRetries > 2 && !isDetached()) {
                    if (mLoadingDialog.isVisible() && !getActivity().isFinishing()) {
                        mLoadingDialog.dismiss();
                    }

                    OptionDialogFragment.show(mActivity, getChildFragmentManager(), R.string.unknown_error, R.string.beaming_failed, android.R.string.yes, android.R.string.no, new OptionDialogFragment.Listener() {
                        @Override
                        public void onSelectionPositive() {
                            startVideo();
                        }

                        @Override
                        public void onSelectionNegative() {
                            closePlayer();
                        }
                    });

                    return;
                }

                startVideo();
                mRetries++;
            }
        });
    }

    @OnClick(R.id.play_button)
    public void playPauseClick(View v) {
        if(mMediaControl == null) return;

        ResponseListener<Object> responseListener = new ResponseListener<Object>() {
            @Override
            public void onSuccess(Object object) {
                mMediaControl.getPlayState(mPlayStateListener);
            }

            @Override
            public void onError(ServiceCommandError error) {
                mMediaControl.getPlayState(mPlayStateListener);
            }
        };

        if (mIsPlaying) {
            mIsPlaying = false;
            mMediaControl.pause(responseListener);
        } else {
            mIsPlaying = true;
            mMediaControl.play(responseListener);
        }

        mPlayButton.setImageResource(mIsPlaying ? R.drawable.ic_av_pause : R.drawable.ic_av_play);
    }

    @OnClick(R.id.forward_button)
    public void forwardClick(View v) {
        int newProgress = mSeekBar.getProgress() + 10000;
        if (newProgress > mTotalTimeDuration) newProgress = (int) mTotalTimeDuration;
        mMediaControl.seek(newProgress, null);
    }

    @OnClick(R.id.backward_button)
    public void backwardClick(View v) {
        int newProgress = mSeekBar.getProgress() - 10000;
        if (newProgress < 0) newProgress = 0;
        mMediaControl.seek(newProgress, null);
    }

    private void startUpdating() {
        mTask = mExecutor.scheduleAtFixedRate(mPositionRunnable, 0, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopUpdating() {
        if (mTask != null) {
            mTask.cancel(false);
        }
        if (mExecutor != null) {
            for (Runnable r : mExecutor.getQueue()) {
                mExecutor.remove(r);
            }
        }
    }

    private void closePlayer() {
        if (mActivity != null && mActivity.getService() != null) {
            mActivity.getService().stopStreaming();
        }
        mBeamManager.stopVideo();
        getActivity().finish();
    }

    private MediaControl.PlayStateListener mPlayStateListener = new MediaControl.PlayStateListener() {
        @Override
        public void onSuccess(MediaControl.PlayStateStatus state) {
            mIsPlaying = state.equals(MediaControl.PlayStateStatus.Playing);
            mPlayButton.setImageResource(mIsPlaying ? R.drawable.ic_av_pause : R.drawable.ic_av_play);

            if (mLoadingDialog.isVisible() && mIsPlaying && !getActivity().isFinishing()) {
                mLoadingDialog.dismiss();
            }

            if (mIsPlaying) {
                mMediaControl.getDuration(mDurationListener);
            }
        }

        @Override
        public void onError(ServiceCommandError error) {
            if (mLoadingDialog.isVisible() && error.getCode() == 500 && !getActivity().isFinishing()) {
                mLoadingDialog.dismiss();

                OptionDialogFragment.show(mActivity, getChildFragmentManager(), R.string.unknown_error, R.string.beaming_failed, android.R.string.yes, android.R.string.no, new OptionDialogFragment.Listener() {
                    @Override
                    public void onSelectionPositive() {
                        startVideo();
                    }

                    @Override
                    public void onSelectionNegative() {
                        closePlayer();
                    }
                });
            }
        }
    };

    private MediaControl.DurationListener mDurationListener = new MediaControl.DurationListener() {
        @Override
        public void onError(ServiceCommandError error) {
        }

        @Override
        public void onSuccess(Long duration) {
            if(mTotalTimeDuration != duration) {
                mTotalTimeDuration = duration;
                mSeekBar.setMax(duration.intValue());
            }
            //durationTextView.setText(formatTime(duration.intValue()));
        }
    };

    private VolumeControl.VolumeListener mVolumeListener = new VolumeControl.VolumeListener() {
        @Override
        public void onSuccess(Float volume) {
            mVolumeBar.setProgress((int) (volume * 100.0f));
        }

        @Override
        public void onError(ServiceCommandError error) {
        }
    };

    private Runnable mPositionRunnable = new Runnable() {
        @Override
        public void run() {
            mMediaControl.getPosition(new MediaControl.PositionListener() {
                @Override
                public void onSuccess(Long position) {
                    if (!mIsUserSeeking) {
                        mSeekBar.setProgress(position.intValue());
                        mSeekBar.setSecondaryProgress(0); // hack to make the secondary progress appear on Android 5.0
                        mSeekBar.setSecondaryProgress(mDownloadProgress.intValue());
                    }

                    if (mLoadingDialog.isVisible() && !getActivity().isFinishing() && position > 0) {
                        mLoadingDialog.dismiss();
                    }
                }

                @Override
                public void onError(ServiceCommandError error) {

                }
            });
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser && !mProcessingSeeking && mIsUserSeeking) {
                if (progress <= mDownloadProgress) {
                    mSeekBar.setProgress(progress);
                    mSeekBar.setSecondaryProgress(0); // hack to make the secondary progress appear on Android 5.0
                    mSeekBar.setSecondaryProgress(mDownloadProgress.intValue());

                    mProcessingSeeking = true;
                    mMediaControl.seek(mSeekBar.getProgress(), new ResponseListener<Object>() {
                        @Override
                        public void onSuccess(Object response) {
                            mProcessingSeeking = false;
                            startUpdating();
                        }

                        @Override
                        public void onError(ServiceCommandError error) {
                            mProcessingSeeking = false;
                            startUpdating();
                        }
                    });
                } else {
                    mSeekBar.setProgress(mDownloadProgress.intValue());
                    mSeekBar.setSecondaryProgress(0); // hack to make the secondary progress appear on Android 5.0
                    mSeekBar.setSecondaryProgress(mDownloadProgress.intValue());
                }
            }
        }

        @Override
        public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            mIsUserSeeking = true;
            stopUpdating();
        }

        @Override
        public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            mIsUserSeeking = false;
        }
    };

    public SeekBar.OnSeekBarChangeListener mVolumeBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(android.widget.SeekBar seekBar, int position, boolean fromUser) {
            if (fromUser)
                mVolumeControl.setVolume((float) mVolumeBar.getProgress() / 100.0f, null);
        }
    };

    @Override
    public void onStreamStarted() { }
    @Override
    public void onStreamError(Exception e) { }
    @Override
    public void onStreamReady(File videoLocation) { }

    @Override
    public void onStreamProgress(DownloadStatus status) {
        mDownloadProgress = mTotalTimeDuration / 100 * status.progress;
        mSeekBar.setSecondaryProgress(0); // hack to make the secondary progress appear on Android 5.0
        mSeekBar.setSecondaryProgress(mDownloadProgress.intValue());
    }

    BeamDeviceListener mDeviceListener = new BeamDeviceListener() {

        @Override
        public void onDeviceDisconnected(ConnectableDevice device) {
            super.onDeviceDisconnected(device);
            VideoPlayerActivity.startActivity(getActivity(), mStreamInfo, mSeekBar.getProgress());
            getActivity().finish();
        }

    };

}
