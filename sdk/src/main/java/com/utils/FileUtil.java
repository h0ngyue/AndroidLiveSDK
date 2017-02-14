package com.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileUtil {

    public static final int MEDIA_TYPE_PCM = 0;

    public static final int MEDIA_TYPE_YUV = 1;

    public static final int MEDIA_TYPE_H264 = 2;

    public static final int MEDIA_TYPE_AAC = 3;

    public static final int MEDIA_TYPE_JPEG = 4;

    public static final int MEDIA_TYPE_TXT = 5;

    private static final String TAG = "FileUtil";

    private OutputStream mOutputStream;

    private String mPath;

    public static Uri getOutputMediaFileUri(int type, String appName) {
        return Uri.fromFile(getOutputMediaFile(type, appName));
    }

    @SuppressLint("SimpleDateFormat")
    public static File getOutputMediaFile(int type, String appName) {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), appName);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_PCM) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".pcm");
        } else if (type == MEDIA_TYPE_YUV) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".yuv");
        } else if (type == MEDIA_TYPE_H264) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".h264");
        } else if (type == MEDIA_TYPE_AAC) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".aac");
        } else if (type == MEDIA_TYPE_JPEG) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_TXT) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".txt");
        } else {
            return null;
        }

        return mediaFile;
    }

    public static String getApplicationName(Activity activity) {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = activity.getApplicationContext().getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        String applicationName = (String) packageManager.getApplicationLabel(applicationInfo);
        return applicationName;
    }

    public FileUtil() {
        mOutputStream = null;
        mPath = null;
    }

    public FileUtil(String path) {
        mOutputStream = null;
        mPath = path;
    }

    public void createFile() {
        if (mOutputStream == null) {
            try {
                mOutputStream = new FileOutputStream(mPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void createFile(int type, Activity activity) {
        if (mOutputStream == null) {
            try {
                mPath = getOutputMediaFile(type, getApplicationName(activity)).toString();
                mOutputStream = new FileOutputStream(mPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public String path() {
        return mPath;
    }

    public void saveToFile(byte[] data, int size) {
        if (mOutputStream != null) {
            try {
                mOutputStream.write(data, 0, size);
                mOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveToFile(Bitmap bitmap) {
        if (mOutputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, mOutputStream);
                mOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveToFile(byte[] data) {
        if (mOutputStream != null) {
            try {
                mOutputStream.write(data);
                mOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeFile() {
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mOutputStream = null;
        }
    }
}
