package com.example.contactmasksafe;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.provider.ContactsContract;

import java.util.HashMap;
import java.util.Map;

public class NotificationMaskService extends NotificationListenerService {
    private static final String CHANNEL_ID = "contact_mask_safe_redacted_v3";
    private static final String NOTIFICATION_TAG = "ContactMaskSafe";

    private SavedNumberRepository repository;
    private NotificationManager notificationManager;
    private ContentObserver contactsObserver;
    private final Map<String, Integer> intentionallyCancelledOriginals = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new SavedNumberRepository(this);
        repository.refreshNow();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();

        contactsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                repository.invalidate();
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    true,
                    contactsObserver
            );
        } catch (RuntimeException ignored) {
            // Contacts permission can be revoked while the listener remains enabled.
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !AppPreferences.isPrivacyShieldEnabled(this)
                || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        if (!canPostReplacementNotification()) {
            return;
        }

        Notification original = sbn.getNotification();
        Bundle extras = original.extras;
        CharSequence title = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence subText = extras == null ? null : extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        String safeTitle = PhoneMasker.redactSavedNumbers(title, repository);
        String safeText = PhoneMasker.redactSavedNumbers(text, repository);
        String safeBigText = PhoneMasker.redactSavedNumbers(bigText, repository);
        String safeSubText = PhoneMasker.redactSavedNumbers(subText, repository);
        ExtraMaskResult extraMask = maskStructuredMessageExtras(extras);

        boolean changed = differs(title, safeTitle)
                || differs(text, safeText)
                || differs(bigText, safeBigText)
                || differs(subText, safeSubText)
                || extraMask.changed;
        if (!changed) {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_TAG, replacementId(sbn.getKey()));
            }
            return;
        }

        if (safeBigText.isEmpty() && !extraMask.safeText.isEmpty()) {
            safeBigText = extraMask.safeText;
        }
        if (safeText.isEmpty() && !extraMask.safeText.isEmpty()) {
            safeText = firstLine(extraMask.safeText);
        }

        String appLabel = applicationLabel(sbn.getPackageName());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(safeTitle.isEmpty() ? appLabel : safeTitle)
                .setContentText(safeText)
                .setSubText(safeSubText)
                .setWhen(original.when)
                .setShowWhen(true)
                .setAutoCancel((original.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                .setOngoing((original.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(original.category)
                .setNumber(original.number)
                .setColor(original.color)
                .setPriority(original.priority);

        if (original.getGroup() != null) {
            builder.setGroup(original.getGroup());
        }
        if (original.getSortKey() != null) {
            builder.setSortKey(original.getSortKey());
        }
        if (!safeBigText.isEmpty()) {
            builder.setStyle(new Notification.BigTextStyle().bigText(safeBigText));
        }
        if (original.contentIntent != null) {
            builder.setContentIntent(original.contentIntent);
        }
        if (original.deleteIntent != null) {
            builder.setDeleteIntent(original.deleteIntent);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                && original.actions != null) {
            for (Notification.Action action : original.actions) {
                if (action != null && !PhoneMasker.containsSavedNumber(action.title, repository)) {
                    builder.addAction(action);
                }
            }
        }
        builder.setGroupSummary((original.flags & Notification.FLAG_GROUP_SUMMARY) != 0);

        int replacementId = replacementId(sbn.getKey());
        String originalKey = sbn.getKey();
        try {
            if (originalKey != null) {
                Integer count = intentionallyCancelledOriginals.get(originalKey);
                intentionallyCancelledOriginals.put(originalKey, count == null ? 1 : count + 1);
            }
            cancelNotification(originalKey);
            notificationManager.notify(NOTIFICATION_TAG, replacementId, builder.build());
        } catch (SecurityException ignored) {
            if (originalKey != null) {
                decrementIntentionalCancel(originalKey);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || notificationManager == null
                || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        String key = sbn.getKey();
        if (key != null && hasIntentionalCancel(key)) {
            decrementIntentionalCancel(key);
            return;
        }
        notificationManager.cancel(NOTIFICATION_TAG, replacementId(key));
    }

    private boolean hasIntentionalCancel(String key) {
        Integer count = intentionallyCancelledOriginals.get(key);
        return count != null && count > 0;
    }

    private void decrementIntentionalCancel(String key) {
        Integer count = intentionallyCancelledOriginals.get(key);
        if (count == null || count <= 1) {
            intentionallyCancelledOriginals.remove(key);
        } else {
            intentionallyCancelledOriginals.put(key, count - 1);
        }
    }

    @Override
    public void onDestroy() {
        if (contactsObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(contactsObserver);
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }

    private ExtraMaskResult maskStructuredMessageExtras(Bundle extras) {
        if (extras == null) return ExtraMaskResult.EMPTY;
        StringBuilder safe = new StringBuilder();
        boolean changed = false;

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                String masked = PhoneMasker.redactSavedNumbers(line, repository);
                changed |= differs(line, masked);
                appendLine(safe, masked);
            }
        }

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable parcelable : messages) {
                if (!(parcelable instanceof Bundle)) continue;
                Bundle message = (Bundle) parcelable;
                CharSequence sender = message.getCharSequence("sender");
                CharSequence messageText = message.getCharSequence("text");
                String safeSender = PhoneMasker.redactSavedNumbers(sender, repository);
                String safeMessage = PhoneMasker.redactSavedNumbers(messageText, repository);
                changed |= differs(sender, safeSender) || differs(messageText, safeMessage);
                appendLine(safe, safeSender.isEmpty() ? safeMessage : safeSender + ": " + safeMessage);
            }
        }
        return new ExtraMaskResult(changed, safe.toString());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Masked notifications", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications re-posted after saved contact numbers are redacted.");
            channel.enableVibration(false);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private boolean canPostReplacementNotification() {
        if (notificationManager == null) return false;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N || notificationManager.areNotificationsEnabled();
    }

    private String applicationLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return "Protected notification";
        }
    }

    private static boolean differs(CharSequence original, String safe) {
        String originalString = original == null ? "" : original.toString();
        return !originalString.equals(safe);
    }

    private static int replacementId(String key) {
        return key == null ? 1 : (key.hashCode() & 0x7fffffff);
    }

    private static void appendLine(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(value.trim());
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private static final class ExtraMaskResult {
        private static final ExtraMaskResult EMPTY = new ExtraMaskResult(false, "");
        private final boolean changed;
        private final String safeText;
        private ExtraMaskResult(boolean changed, String safeText) {
            this.changed = changed;
            this.safeText = safeText;
        }
    }
}
