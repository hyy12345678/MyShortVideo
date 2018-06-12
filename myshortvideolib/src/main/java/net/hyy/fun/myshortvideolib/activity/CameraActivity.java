package net.hyy.fun.myshortvideolib.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.hyy.fun.myshortvideolib.R;
import net.hyy.fun.myshortvideolib.camera.CameraManager;
import net.hyy.fun.myshortvideolib.camera.CameraProgressBar;
import net.hyy.fun.myshortvideolib.camera.CameraView;
import net.hyy.fun.myshortvideolib.camera.MediaPlayerManager;
import net.hyy.fun.myshortvideolib.utils.FileUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by huangyy
 * 拍摄界面
 */
public class CameraActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * 获取相册
     */
    public static final int REQUEST_PHOTO = 1;
    /**
     * 获取视频
     */
    public static final int REQUEST_VIDEO = 2;
    /**
     * 最小录制时间
     */
    private static final int MIN_RECORD_TIME = 1 * 1000;
    /**
     * 最长录制时间
     */
    private static final int MAX_RECORD_TIME = 10 * 1000;
    /**
     * 刷新进度的间隔时间
     */
    private static final int PLUSH_PROGRESS = 100;

    private Context mContext;
    /**
     * TextureView
     */
    private TextureView mTextureView;
    /**
     * 带手势识别
     */
    private CameraView mCameraView;
    /**
     * 录制按钮
     */
    private CameraProgressBar mProgressbar;

    /**
     * 关闭,选择,前后置
     */
    private ImageView iv_close, iv_choice;

    /**
     * camera manager
     */
    private CameraManager cameraManager;
    /**
     * player manager
     */
    private MediaPlayerManager playerManager;
    /**
     * true代表视频录制,否则拍照
     */
    private boolean isSupportRecord;
    /**
     * 视频录制地址
     */
    private String recorderPath, photoPath;
    /**
     * 获取照片订阅, 进度订阅
     */
    private Subscription takePhotoSubscription, progressSubscription;
    /**
     * 是否正在录制
     */
    private boolean isRecording, isResume;

    public static void lanuchForPhoto(Activity context) {
        Intent intent = new Intent(context, CameraActivity.class);
        context.startActivityForResult(intent, REQUEST_PHOTO);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_camera);
        initView();
        initDatas();
    }

    private void initView() {
        mTextureView = (TextureView) findViewById(R.id.mTextureView);
        mCameraView = (CameraView) findViewById(R.id.mCameraView);
        mProgressbar = (CameraProgressBar) findViewById(R.id.mProgressbar);

        iv_close = (ImageView) findViewById(R.id.iv_close);
        iv_close.setOnClickListener(this);
        iv_choice = (ImageView) findViewById(R.id.iv_choice);
        iv_choice.setOnClickListener(this);

    }

    protected void initDatas() {
        cameraManager = CameraManager.getInstance(getApplication());
        playerManager = MediaPlayerManager.getInstance(getApplication());
        cameraManager.setCameraType(isSupportRecord ? 1 : 0);



        final int max = MAX_RECORD_TIME / PLUSH_PROGRESS;
        mProgressbar.setMaxProgress(max);

        mProgressbar.setOnProgressTouchListener(new CameraProgressBar.OnProgressTouchListener() {
            @Override
            public void onClick(CameraProgressBar progressBar) {
                cameraManager.takePhoto(callback);
            }

            @Override
            public void onLongClick(CameraProgressBar progressBar) {
                startRecorder(max);
            }

            @Override
            public void onZoom(boolean zoom) {
                cameraManager.handleZoom(zoom);
            }

            @Override
            public void onLongClickUp(CameraProgressBar progressBar) {
                stopRecorder();
                if (progressSubscription != null) {
                    progressSubscription.unsubscribe();
                }
                int recordSecond = mProgressbar.getProgress() * PLUSH_PROGRESS;//录制多少毫秒
                mProgressbar.reset();
                if (recordSecond < MIN_RECORD_TIME) {//小于最小录制时间作废
                    Toast.makeText(mContext, "录制时间不可小1秒", Toast.LENGTH_SHORT).show();
                    if (recorderPath != null) {
                        FileUtils.delteFiles(new File(recorderPath));
                        recorderPath = null;
                    }
                    setTakeButtonShow(true);
                } else if (isResume && mTextureView != null && mTextureView.isAvailable()){
                    setTakeButtonShow(false);
                    cameraManager.closeCamera();
                    playerManager.playMedia(new Surface(mTextureView.getSurfaceTexture()), recorderPath);
                }
            }

            @Override
            public void onPointerDown(float rawX, float rawY) {
                if (mTextureView != null) {
                    mCameraView.setFoucsPoint(new PointF(rawX, rawY));
                }
            }
        });

        mCameraView.setOnViewTouchListener(new CameraView.OnViewTouchListener() {
            @Override
            public void handleFocus(float x, float y) {
                cameraManager.handleFocusMetering(x, y);
            }

            @Override
            public void handleZoom(boolean zoom) {
                cameraManager.handleZoom(zoom);
            }
        });
    }



    /**
     * 是否显示录制按钮
     * @param isShow
     */
    private void setTakeButtonShow(boolean isShow) {
        if (isShow) {
            mProgressbar.setVisibility(View.VISIBLE);
            iv_choice.setVisibility(View.INVISIBLE);

        } else {
            mProgressbar.setVisibility(View.GONE);
            iv_choice.setVisibility(View.VISIBLE);

        }
    }

    /**
     * 停止录制
     */
    private void stopRecorder() {
        cameraManager.stopMediaRecord();
        isRecording = false;
    }

    /**
     * 开始录制
     */
    private void startRecorder(int max) {
        try {
            recorderPath = FileUtils.getUploadVideoFile(mContext);
            cameraManager.startMediaRecord(recorderPath);
            isRecording = true;
            progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .take(max).subscribe(new Subscriber<Long>() {
                        @Override
                        public void onCompleted() {
                            stopRecorder();
                            mProgressbar.reset();
                            if (isResume && mTextureView != null && mTextureView.isAvailable()){
                                setTakeButtonShow(false);
                                cameraManager.closeCamera();
                                playerManager.playMedia(new Surface(mTextureView.getSurfaceTexture()), recorderPath);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onNext(Long aLong) {
                            mProgressbar.setProgress(mProgressbar.getProgress() + 1);
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(mContext, "没有权限...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResume = true;
        isRecording = false;
        if (mTextureView.isAvailable()) {
            if (recorderPath != null) {
                setTakeButtonShow(false);
                playerManager.playMedia(new Surface(mTextureView.getSurfaceTexture()), recorderPath);
            } else {
                openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
            }
        } else {
            mTextureView.setSurfaceTextureListener(listener);
        }
    }

    @Override
    protected void onPause() {
        isResume = false;
        if (isRecording) {
            stopRecorder();
            if (progressSubscription != null) {
                progressSubscription.unsubscribe();
            }
            mProgressbar.reset();
            FileUtils.delteFiles(new File(recorderPath));
            recorderPath = null;
        }
        if (takePhotoSubscription != null) {
            takePhotoSubscription.unsubscribe();
        }
        photoPath = null;
        cameraManager.closeCamera();
        playerManager.stopMedia();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mCameraView.removeOnZoomListener();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.iv_close) {
            if (backClose()) {
                return;
            }
            finish();

        } else if (i == R.id.iv_choice) {
//            Toast.makeText(CameraActivity.this, "choice", Toast.LENGTH_SHORT).show();

            backResult();


        }
    }

    /**
     * 返回结果通过Intent
     */
    private void backResult() {
        if(recorderPath != null){

            scanFileAsync(this,recorderPath);

            Intent i = new Intent();
            i.putExtra("result",recorderPath);
            setResult(RESULT_OK,i);
            finish();
        }

        if (photoPath != null) {//有拍照

            scanFileAsync(this,photoPath);

            Intent i = new Intent();
            i.putExtra("result",photoPath);
            setResult(RESULT_OK,i);
            finish();
        }


    }

    /**
     * 返回关闭界面
     */
    private boolean backClose() {
        if (recorderPath != null) {//正在录制或正在播放
            if (isRecording) {//正在录制
                stopRecorder();
                if (progressSubscription != null) {
                    progressSubscription.unsubscribe();
                }
                mProgressbar.reset();
                FileUtils.delteFiles(new File(recorderPath));
                recorderPath = null;
                if (mTextureView != null && mTextureView.isAvailable()) {
                    openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
                }
                return true;
            }
            playerManager.stopMedia();
            FileUtils.delteFiles(new File(recorderPath));
            recorderPath = null;
            if (mTextureView != null && mTextureView.isAvailable()) {
                openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
            }
            return true;
        }
        if (photoPath != null) {//有拍照
            photoPath = null;//有需求也可以删除
            cameraManager.restartPreview();
            setTakeButtonShow(true);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (backClose()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * 开启照相机
     * @param texture
     * @param width
     * @param height
     */
    private void openCamera(SurfaceTexture texture, int width, int height) {
        setTakeButtonShow(true);
        try {
            cameraManager.openCamera(texture, width, height);
        } catch (RuntimeException e) {
            Toast.makeText(mContext, "没有权限...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * camera回调监听
     */
    private TextureView.SurfaceTextureListener listener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            if (recorderPath != null) {
                setTakeButtonShow(false);
                playerManager.playMedia(new Surface(texture), recorderPath);
            } else {
                openCamera(texture, width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private Camera.PictureCallback callback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            setTakeButtonShow(false);
            takePhotoSubscription = Observable.create(new Observable.OnSubscribe<Boolean>() {
                @Override
                public void call(Subscriber<? super Boolean> subscriber) {
                    if (!subscriber.isUnsubscribed()) {
                        photoPath = FileUtils.getUploadPhotoFile(mContext);
                        subscriber.onNext(FileUtils.savePhoto(photoPath, data, cameraManager.isCameraFrontFacing()));
                    }
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<Boolean>() {
                @Override
                public void onCompleted() {

                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onNext(Boolean aBoolean) {
                    if (aBoolean != null && aBoolean) {
                        iv_choice.setVisibility(View.VISIBLE);
                    } else {
                        setTakeButtonShow(true);
                    }
                }
            });
        }
    };


    /**
     * 启动MediaScanner服务，扫描媒体文件
     * @param ctx
     * @param filePath
     */
    public void scanFileAsync(Context ctx, String filePath) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(new File(filePath)));
        ctx.sendBroadcast(scanIntent);
    }

}
