
package com.kokufu.android.apps.fusedroid.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import com.kokufu.android.apps.fusedroid.R;

public class ErrorDialogActivity extends Activity {
    public static final String EXTRA_TITLE = "com.kokufu.android.apps.fusedroid.extra.TITLE";
    public static final String EXTRA_TEXT = "com.kokufu.android.apps.fusedroid.extra.TEXT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_dialog);

        Intent intent = getIntent();

        final String title = intent.getExtras().getString(EXTRA_TITLE);
        final String text = intent.getExtras().getString(EXTRA_TEXT);

        setTitle(title);

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // expand the width of the dialog.
            getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }

        TextView textView = (TextView) findViewById(R.id.textView1);
        textView.setText(text);

        Button button = (Button) findViewById(R.id.button1);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
