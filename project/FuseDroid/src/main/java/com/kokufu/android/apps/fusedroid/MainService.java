
package com.kokufu.android.apps.fusedroid;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.kokufu.android.apps.fusedroid.RootedShell.OnOutputStreamLineListener;
import com.kokufu.android.apps.fusedroid.dialog.ErrorDialogActivity;
import com.kokufu.android.apps.fusedroid.dialog.PropertyDialogActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainService extends Service {
    public static final String ACTION_MOUNT = "com.kokufu.android.apps.fusedroid.intent.action.MOUNT";
    public static final String ACTION_UMOUNT = "com.kokufu.android.apps.fusedroid.intent.action.UMOUNT";

    private static final String TAG = "MainService";

    private static final String MOUNT_COMMAND = "mount.fusedroid";
    private static final String MOUNT_COMMAND_ASSETS = Build.CPU_ABI + "/" + MOUNT_COMMAND;

    private static final AtomicInteger sMountId = new AtomicInteger(1);

    private final Map<String, MountInfo> mMountInfoMap = new HashMap<String, MountInfo>();

    private boolean sIsInitialized = false;
    private File sMountCommandFile;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        String targetDirPath = intent.getExtras().getString(
                FuseDroidApplication.EXTRA_TARGET_DIR);
        String mountPointDirPath = intent.getExtras().getString(
                FuseDroidApplication.EXTRA_MOUNT_PINT_DIR);
        boolean isWritable = intent.getExtras().getBoolean(
                FuseDroidApplication.EXTRA_WRITABLE);

        if (ACTION_MOUNT.equals(action)) {
            if (!mMountInfoMap.containsKey(mountPointDirPath)) {
                MountInfo info = new MountInfo(
                        sMountId.getAndIncrement(),
                        targetDirPath,
                        mountPointDirPath,
                        isWritable);
                new MountTask(info).execute();
            } else {
                Toast.makeText(this, getString(R.string.error_already_mounted), Toast.LENGTH_SHORT)
                        .show();
            }
        } else if (ACTION_UMOUNT.equals(action)) {
            MountInfo info = mMountInfoMap.get(mountPointDirPath);
            if (info == null) {
                info = new MountInfo(0, targetDirPath, mountPointDirPath, isWritable);
            }
            new UmountTask(info).execute();
        } else {
            throw new IllegalArgumentException("Action " + action + " is not supported.");
        }
        return START_REDELIVER_INTENT;
    }

    private boolean initialize() {
        if (!sIsInitialized) {
            // Get version name
            String versionName;
            try {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.GET_META_DATA);
                versionName = packageInfo.versionName;
            } catch (NameNotFoundException e) {
                // Fatal
                throw new RuntimeException(e);
            }

            // check version
            sMountCommandFile = new File(getFilesDir(), MOUNT_COMMAND);
            boolean needUpdate = false;
            File versionFile = new File(getFilesDir(), "version");
            if (!versionFile.exists() || !sMountCommandFile.exists()) {
                needUpdate = true;
            } else {
                try {
                    String v = readFirstLine(versionFile);
                    if (v == null || !v.equals(versionName)) {
                        needUpdate = true;
                    }
                } catch (IOException e) {
                    needUpdate = true;

                    e.printStackTrace();
                }
            }

            if (needUpdate) {
                versionFile.delete();
                sMountCommandFile.delete();

                try {
                    copyFileFromAssets(MOUNT_COMMAND_ASSETS, sMountCommandFile);
                    execCommmandAsRoot("chmod 700 " + sMountCommandFile.getAbsolutePath());

                    writeFirstLine(versionFile, versionName);
                } catch (IOException e) {
                    Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
                    intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
                    intent.putExtra(ErrorDialogActivity.EXTRA_TEXT,
                            getString(R.string.error_cannot_copy_binary));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return false;
                }
            }

            sIsInitialized = true;
        }
        return true;
    }

    private static String readFirstLine(File targetFile) throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(targetFile)));
            return br.readLine();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    private static void writeFirstLine(File targetFile, String line) throws IOException {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile)));
            bw.write(line);
            bw.flush();
        } finally {
            if (bw != null) {
                bw.close();
            }
        }
    }

    private void copyFileFromAssets(String path, File targetFile)
            throws IOException {

        OutputStream os = null;
        InputStream is = null;
        try {
            os = new FileOutputStream(targetFile);
            is = getAssets().open(path);
            byte[] buf = new byte[1024];
            int len = 0;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Do nothing
                }

            }
        }
    }

    /**
     * @param dir
     * @return {@code true} if the directory was created or the directory
     *         already existed, {@code false} on failure.<br />
     *         This behavior is NOT the same as {@link File#mkdir()}'s.
     */
    private static boolean mkdir(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                try {
                    if (execCommmandAsRoot("test -e " + dir.getAbsolutePath()) == 0) {
                        return true;
                    } else {
                        return (execCommmandAsRoot("mkdir " + dir.getAbsolutePath()) == 0);
                    }
                } catch (IOException e) {
                    // Do nothing
                    e.printStackTrace();
                }
                return false;
            }
        }
        return true;
    }

    private static boolean rmdir(File dir) {
        try {
            return (execCommmandAsRoot("rmdir " + dir.getAbsolutePath()) == 0);
        } catch (IOException e) {
            // Do nothing
            e.printStackTrace();
        }
        return false;
    }

    private static int execCommmandAsRoot(String command) throws IOException {
        final int result[] = {
                -1
        };

        final Object lock = new Object();
        final RootedShell shell = new RootedShell(
                new OnOutputStreamLineListener() {
                    @Override
                    public void onOut(String line) {
                        try {
                            result[0] = Integer.valueOf(line);
                        } catch (NumberFormatException e) {
                            // Do nothing
                            e.printStackTrace();
                        }
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                },
                null);
        synchronized (lock) {
            shell.stdinLine(command);
            shell.stdinLine("echo $?");

            try {
                lock.wait(30000); // 30 seconds TODO temporary
            } catch (InterruptedException e) {
                // Do nothing
                e.printStackTrace();
            }
        }

        shell.exit();

        return result[0];
    }

    private Notification makeNotification(String ticker, String title, String text,
            PendingIntent intent) {
        return new NotificationCompat.Builder(
                getApplicationContext())
                .setSmallIcon(R.drawable.ic_notification)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(intent)
                .build();

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification makeNotification16(String ticker, String title, String text,
            PendingIntent intent) {
        return new Notification.Builder(
                getApplicationContext())
                .setSmallIcon(R.drawable.ic_notification)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(intent)
                .build();
    }

    private static class MountInfo {
        public final int mId;
        public final String mTargetDirPath;
        public final String mMountPintDirPath;
        public final boolean mIsWritable;

        public MountInfo(int id, String targetDirPath, String mountPointDirPath, boolean isWritable) {
            mId = id;
            mTargetDirPath = targetDirPath;
            mMountPintDirPath = mountPointDirPath;
            mIsWritable = isWritable;
        }
    }

    private class MountTask extends AsyncTask<Void, Void, Boolean> {
        private final MountInfo mInfo;

        public MountTask(MountInfo info) {
            mInfo = info;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (!initialize()) {
                return false;
            }

            // Check dir exist
            boolean result = false;
            try {
                result = (execCommmandAsRoot("test -d " + mInfo.mTargetDirPath) == 0);
            } catch (IOException e) {
                // Do nothing
            }
            if (!result) {
                Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
                intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
                intent.putExtra(ErrorDialogActivity.EXTRA_TEXT,
                        getString(R.string.error_dir_does_not_exist, mInfo.mTargetDirPath));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return false;
            }

            // Mount
            if (mkdir(new File(mInfo.mMountPintDirPath))) {
                Log.d(TAG, "mounting " + mInfo.mTargetDirPath + " on " + mInfo.mMountPintDirPath);
                try {
                    StringBuilder sb = new StringBuilder()
                            .append(sMountCommandFile.getAbsolutePath());
                    if (mInfo.mIsWritable) {
                        sb.append(" --writable");
                    }
                    sb
                            .append(" --readable -o allow_other ")
                            .append(mInfo.mTargetDirPath)
                            .append(" ")
                            .append(mInfo.mMountPintDirPath)
                            .append(" 1>/dev/null");
                    String command = sb.toString();
                    result = (execCommmandAsRoot(command) == 0);
                } catch (IOException e) {
                    Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
                    intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
                    intent.putExtra(ErrorDialogActivity.EXTRA_TEXT,
                            getString(R.string.error_mount_fail));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return false;
                }

                if (result) {
                    Log.d(TAG, "Mounted. " + mInfo.mTargetDirPath + " on "
                            + mInfo.mMountPintDirPath);
                } else {
                    Log.e(TAG, "Mount failed. " + mInfo.mTargetDirPath + " on "
                            + mInfo.mMountPintDirPath);
                }
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                mMountInfoMap.put(mInfo.mMountPintDirPath, mInfo);

                // Show notification
                String ticker = getString(R.string.message_mounted1, mInfo.mMountPintDirPath);
                String title = getString(R.string.app_name);
                String text = getString(R.string.message_mounted2, mInfo.mTargetDirPath,
                        mInfo.mMountPintDirPath);

                Intent notificationIntent = new Intent(getApplicationContext(),
                        PropertyDialogActivity.class);
                notificationIntent.putExtra(FuseDroidApplication.EXTRA_TARGET_DIR,
                        mInfo.mTargetDirPath);
                notificationIntent.putExtra(FuseDroidApplication.EXTRA_MOUNT_PINT_DIR,
                        mInfo.mMountPintDirPath);
                notificationIntent.putExtra(FuseDroidApplication.EXTRA_WRITABLE,
                        mInfo.mIsWritable);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent intent = PendingIntent.getActivity(getApplicationContext(),
                        mInfo.mId, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Notification notification;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    notification = makeNotification16(ticker, title, text, intent);
                } else {
                    notification = makeNotification(ticker, title, text, intent);
                }
                notification.flags |= Notification.FLAG_NO_CLEAR;

                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(mInfo.mId);
                manager.notify(mInfo.mId, notification);
            }
        }
    }

    private class UmountTask extends AsyncTask<Void, Void, Boolean> {
        private final MountInfo mInfo;

        public UmountTask(MountInfo info) {
            mInfo = info;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (!initialize()) {
                return false;
            }

            Log.d(TAG, "Unmounting " + mInfo.mMountPintDirPath);

            boolean result = false;
            try {
                String command = new StringBuilder()
                        .append("umount ")
                        .append(mInfo.mMountPintDirPath)
                        .append(" 1>/dev/null")
                        .toString();

                result = (execCommmandAsRoot(command) == 0);
            } catch (IOException e) {
                Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
                intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
                intent.putExtra(ErrorDialogActivity.EXTRA_TEXT,
                        getString(R.string.error_umount_fail));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return false;
            }

            if (result) {
                Log.d(TAG, "Unmounted " + mInfo.mMountPintDirPath);
            } else {
                Log.e(TAG, "Unmount failed." + mInfo.mMountPintDirPath);
            }

            rmdir(new File(mInfo.mMountPintDirPath));

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(mInfo.mId);
                mMountInfoMap.remove(mInfo.mMountPintDirPath);
            }
        }
    }
}
