
package com.kokufu.android.apps.fusedroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;

import java.io.File;

public class MainActivity extends Activity {
    private static final String[] DIR_LIST = {
            "/data/data",
            "/system",
            "/"
    };

    private AutoCompleteTextView mAutoCompleteTextView;
    private CheckBox mCheckBox;
    private Button mMountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAutoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView1);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.select_dialog_item, DIR_LIST);
        mAutoCompleteTextView.setAdapter(adapter);
        mAutoCompleteTextView.setThreshold(1);
        mAutoCompleteTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAutoCompleteTextView.isPopupShowing()) {
                    mAutoCompleteTextView.dismissDropDown();
                } else {
                    mAutoCompleteTextView.showDropDown();
                }
            }
        });
        mAutoCompleteTextView.setText("/");

        mCheckBox = (CheckBox) findViewById(R.id.checkBox1);

        mMountButton = (Button) findViewById(R.id.button1);
        mMountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                File mountRootDir = ((FuseDroidApplication) getApplication()).getMountRootDir();
                File targetDir = new File(mAutoCompleteTextView.getText().toString());

                String mountPointName = targetDir.getName();
                mountPointName = (mountPointName.equals("")) ? "root" : mountPointName;
                File mountPointDir = new File(mountRootDir, mountPointName);

                Intent intent = new Intent(MainActivity.this, MainService.class);
                intent.setAction(MainService.ACTION_MOUNT);
                intent.putExtra(FuseDroidApplication.EXTRA_TARGET_DIR,
                        targetDir.getAbsolutePath());
                intent.putExtra(FuseDroidApplication.EXTRA_MOUNT_PINT_DIR,
                        mountPointDir.getAbsolutePath());
                intent.putExtra(FuseDroidApplication.EXTRA_WRITABLE, mCheckBox.isChecked());
                startService(intent);
            }
        });
    }
}
