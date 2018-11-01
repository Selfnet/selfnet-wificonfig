package de.selfnet.wifisetup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class ResultScreen extends Activity {

    public static String TITLE = "title";
    public static String DESC = "desc";

    /**
     * Called when the activity is created.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result_screen);
        Intent intent = getIntent();
        String title = intent.getStringExtra(TITLE);
        String desc = intent.getStringExtra(DESC);
        ((TextView) findViewById(R.id.resulttitle)).setText(title);
        ((TextView) findViewById(R.id.result)).setText(desc);
    }
}
