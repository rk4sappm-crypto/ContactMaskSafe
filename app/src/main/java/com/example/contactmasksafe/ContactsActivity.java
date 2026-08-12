package com.example.contactmasksafe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Build;
import android.provider.ContactsContract;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;

public class ContactsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_list);
        UiInsets.apply(this, findViewById(R.id.rootList));

        TextView title = findViewById(R.id.titleText);
        ListView listView = findViewById(R.id.listView);
        title.setText("Masked Contacts");

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Contact permission required", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ArrayList<String> names = loadContactNamesOnly();
        if (names.isEmpty()) names.add("No saved contacts found");
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        listView.setLongClickable(false);
    }

    private ArrayList<String> loadContactNamesOnly() {
        ArrayList<String> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty() && !seen.contains(name)) {
                    seen.add(name);
                    result.add(name);
                }
            }
            cursor.close();
        }
        return result;
    }
}
