package com.kuna.bgmstoremobile;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class Info extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.infoview);

        TextView t = (TextView) findViewById(R.id.textView2);
        t.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
