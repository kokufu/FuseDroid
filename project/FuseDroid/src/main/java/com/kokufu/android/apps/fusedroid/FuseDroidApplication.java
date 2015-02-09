
package com.kokufu.android.apps.fusedroid;

import com.kokufu.android.apps.fusedroid.dialog.ErrorDialogActivity;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

public class FuseDroidApplication extends android.app.Application {
    public static final String EXTRA_TARGET_DIR = "com.kokufu.android.apps.fusedroid.extra.TARGT_DIR";
    public static final String EXTRA_MOUNT_PINT_DIR = "com.kokufu.android.apps.fusedroid.extra.MOUNT_POINT_DIR";
    public static final String EXTRA_WRITABLE = "com.kokufu.android.apps.fusedroid.extra.WRITABLE";

    private File mMountRootDir = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // Check whether su is installed and executable
        try {
            RootedShell shell = new RootedShell(null, null);
            shell.exit();
        } catch (IOException e) {
            String errorMessage = getString(R.string.error_no_su);
            Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
            intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
            intent.putExtra(ErrorDialogActivity.EXTRA_TEXT, errorMessage);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        // Check mount root file
        Environment.getExternalStorageDirectory();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mMountRootDir = calcMountRootDir8();
        } else {
            mMountRootDir = calcMountRootDir();
        }

        if (!mMountRootDir.exists()) {
            if (!mMountRootDir.mkdir()) {
                File mountRootDir2 = new File("/data/" + getString(R.string.app_name));
                try {
                    RootedShell shell = new RootedShell(null, null);
                    shell.stdinLine("mkdir " + mMountRootDir.getAbsolutePath());
                    shell.exit();
                } catch (IOException e) {
                    String errorMessage = getString(R.string.error_cannot_make_mount_point_root,
                            mMountRootDir,
                            mountRootDir2);
                    Intent intent = new Intent(getApplicationContext(), ErrorDialogActivity.class);
                    intent.putExtra(ErrorDialogActivity.EXTRA_TITLE, getString(R.string.error));
                    intent.putExtra(ErrorDialogActivity.EXTRA_TEXT, errorMessage);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }
                mMountRootDir = mountRootDir2;
            }
        }

        // Unmount all directories
        for (File f : mMountRootDir.listFiles()) {
            Intent intent = new Intent(this, MainService.class);
            intent.setAction(MainService.ACTION_UMOUNT);
            intent.putExtra(EXTRA_MOUNT_PINT_DIR, f.getAbsolutePath());
            startService(intent);
        }
    }

    public File getMountRootDir() {
        return mMountRootDir;
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private File calcMountRootDir8() {
        return Environment.getExternalStoragePublicDirectory(
                getString(R.string.app_name));
    }

    private File calcMountRootDir() {
        return new File(Environment.getExternalStorageDirectory(),
                getString(R.string.app_name));
    }
}
