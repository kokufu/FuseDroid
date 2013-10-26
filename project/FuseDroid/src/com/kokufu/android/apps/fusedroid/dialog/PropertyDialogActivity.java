
package com.kokufu.android.apps.fusedroid.dialog;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.kokufu.android.apps.fusedroid.FuseDroidApplication;
import com.kokufu.android.apps.fusedroid.MainActivity;
import com.kokufu.android.apps.fusedroid.MainService;
import com.kokufu.android.apps.fusedroid.R;

public class PropertyDialogActivity extends Activity {
    private TextView mTextView;
    private TextView mTextViewWritable;
    private Button mCopyToClipboardButton;
    private Button mUmountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property);

        Intent intent = getIntent();

        final String targetDirPath = intent.getExtras().getString(
                FuseDroidApplication.EXTRA_TARGET_DIR);
        final String mountPointDirPath = intent.getExtras().getString(
                FuseDroidApplication.EXTRA_MOUNT_PINT_DIR);
        final boolean isWritable = intent.getExtras().getBoolean(
                FuseDroidApplication.EXTRA_WRITABLE);

        setTitle(mountPointDirPath);

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // expand the width of the dialog.
            getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }

        mTextView = (TextView) findViewById(R.id.textView1);
        mTextView.setText(
                getString(R.string.message_mounted2, targetDirPath, mountPointDirPath));

        mTextViewWritable = (TextView) findViewById(R.id.textView2);
        mTextViewWritable.setVisibility(isWritable ? View.VISIBLE : View.GONE);

        mCopyToClipboardButton = (Button) findViewById(R.id.buttonCopyToClipboard);
        mCopyToClipboardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    copyToClipboard11(mountPointDirPath);
                } else {
                    copyToClipboard(mountPointDirPath);
                }
                Toast.makeText(getApplicationContext(),
                        getString(R.string.copied_to_clipboard, mountPointDirPath),
                        Toast.LENGTH_LONG).show();
            }
        });

        mUmountButton = (Button) findViewById(R.id.buttonUmount);
        mUmountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                {
                    Intent intent = new Intent(getApplicationContext(),
                            MainService.class);
                    intent.setAction(MainService.ACTION_UMOUNT);
                    intent.putExtra(FuseDroidApplication.EXTRA_TARGET_DIR,
                            targetDirPath);
                    intent.putExtra(FuseDroidApplication.EXTRA_MOUNT_PINT_DIR,
                            mountPointDirPath);
                    startService(intent);
                }
                {
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    startActivity(intent);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void copyToClipboard(String text) {
        android.text.ClipboardManager clipboardManager =
                (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setText(text);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void copyToClipboard11(String text) {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData.Item item = new ClipData.Item(text);
        String[] mimeTypes = new String[] {
                ClipDescription.MIMETYPE_TEXT_PLAIN
        };
        ClipData clip = new ClipData("cell_data", mimeTypes, item);
        clipboardManager.setPrimaryClip(clip);
    }
}
