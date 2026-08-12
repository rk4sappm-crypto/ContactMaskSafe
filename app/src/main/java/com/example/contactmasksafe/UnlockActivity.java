package com.example.contactmasksafe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class UnlockActivity extends Activity {
    private EditText passwordInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_unlock);
        UiInsets.apply(this, findViewById(R.id.unlockRoot));
        passwordInput = findViewById(R.id.passwordInput);
        statusText = findViewById(R.id.unlockStatus);
        Button unlockButton = findViewById(R.id.btnUnlock);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                attemptUnlock();
                return true;
            }
            return false;
        });
        unlockButton.setOnClickListener(v -> attemptUnlock());
        updateLockoutMessage();
        passwordInput.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLockoutMessage();
    }

    private void attemptUnlock() {
        long remaining = AppLockManager.getLockoutRemainingMs(this);
        if (remaining > 0L) {
            showLockedMessage(remaining);
            return;
        }
        if (AppLockManager.verifyAndUnlock(this, passwordInput.getText().toString())) {
            passwordInput.setText("");
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
            finish();
            return;
        }
        passwordInput.setText("");
        long afterFailure = AppLockManager.getLockoutRemainingMs(this);
        if (afterFailure > 0L) showLockedMessage(afterFailure);
        else {
            statusText.setText("Incorrect password. Access denied.");
            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateLockoutMessage() {
        long remaining = AppLockManager.getLockoutRemainingMs(this);
        if (remaining > 0L) showLockedMessage(remaining);
        else statusText.setText("Enter the owner password to open ContactMask Safe.");
    }

    private void showLockedMessage(long remainingMs) {
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        statusText.setText("Too many incorrect attempts. Try again in about " + seconds + " seconds.");
    }
}
