package com.example.contactmasksafe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MaskTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        int padding = (int) (22 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = new TextView(this);
        title.setText("ContactMask Safe self-test");
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(26f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView instructions = new TextView(this);
        instructions.setText("The saved phone number below should be covered by XXXXXXXXX within about one second. If it remains visible, the Accessibility service is not running or Contacts permission is unavailable.");
        instructions.setTextColor(Color.rgb(55, 65, 81));
        instructions.setTextSize(16f);
        instructions.setPadding(0, padding / 2, 0, padding);
        root.addView(instructions);

        SavedNumberRepository repository = new SavedNumberRepository(this);
        repository.refreshNow();
        String sampleNumber = repository.getSampleNumber();
        String sampleName = repository.getSampleName();

        TextView sample = new TextView(this);
        sample.setTextColor(Color.BLACK);
        sample.setTextSize(23f);
        sample.setPadding(padding / 2, padding, padding / 2, padding);
        sample.setBackgroundColor(Color.WHITE);
        if (sampleNumber == null) {
            sample.setText("No saved phone number was loaded. Grant Contacts permission and save at least one contact with a phone number.");
        } else {
            String name = sampleName == null || sampleName.trim().isEmpty()
                    ? "Saved contact" : sampleName.trim();
            sample.setText(name + "\n" + sampleNumber);
            sample.setContentDescription(name + " " + sampleNumber);
        }
        root.addView(sample, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("This test screen is screenshot-protected. Press Back after checking the mask.");
        note.setTextColor(Color.rgb(153, 27, 27));
        note.setTextSize(14f);
        note.setPadding(0, padding, 0, 0);
        root.addView(note);

        setContentView(root);
        UiInsets.apply(this, root);
    }
}
