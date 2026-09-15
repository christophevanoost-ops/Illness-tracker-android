package com.example.illnesstracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

public class AlarmScheduler {
    private static final String PREFS = "scheduled_alarms";
    private static final String KEY = "items";

    public static void schedule(Context context, String id, long triggerAt, String title, String body) {
        if (triggerAt <= System.currentTimeMillis()) return;
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
        persist(context, id, triggerAt, title, body);
    }

    public static void cancel(Context context, String id) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi);
        removePersisted(context, id);
    }

    public static void restore(Context context) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i=0;i<a.length();i++) {
                JSONObject o = a.getJSONObject(i);
                long time = o.getLong("time");
                if (time > System.currentTimeMillis()) {
                    schedule(context, o.getString("id"), time, o.getString("title"), o.getString("body"));
                }
            }
        } catch (Exception ignored) { }
    }

    private static int requestCode(String id) { return id.hashCode(); }

    private static void persist(Context context, String id, long time, String title, String body) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray old = new JSONArray(raw), out = new JSONArray();
            for (int i=0;i<old.length();i++) if (!id.equals(old.getJSONObject(i).optString("id"))) out.put(old.getJSONObject(i));
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("time", time); o.put("title", title); o.put("body", body); out.put(o);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static void removePersisted(Context context, String id) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray old = new JSONArray(raw), out = new JSONArray();
            for (int i=0;i<old.length();i++) if (!id.equals(old.getJSONObject(i).optString("id"))) out.put(old.getJSONObject(i));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) { }
    }
}
