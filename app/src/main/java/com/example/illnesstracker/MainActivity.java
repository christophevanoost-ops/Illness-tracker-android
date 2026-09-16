package com.example.illnesstracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private WebView printWebView;
    private static final String CHANNEL_ID = "illness_reminders_v2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectPrintFeature();
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void injectPrintFeature() {
        String js =
                "(function(){" +
                "if(window.__episodePrintInstalled)return;window.__episodePrintInstalled=true;" +
                "function pe(v){return String(v==null?'':v).replace(/[&<>\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c];});}" +
                "function noteHtml(r){return r.note?'<div class=note>'+pe(r.note)+'</div>':'';}" +
                "window.printEpisodeOverview=function(){" +
                "var e=episodes.find(function(x){return x.id===activeEpisode;});if(!e){alert('Open an illness episode first.');return;}" +
                "var p=people.find(function(x){return x.id===e.personId;});" +
                "var list=records.filter(function(r){return r.episodeId===e.id;}).sort(function(a,b){return new Date(a.dateTime)-new Date(b.dateTime);});" +
                "var rows=list.length?list.map(function(r){" +
                "var temp='',med='',sym='';" +
                "if(r.type==='temperature')temp=pe(r.detail)+noteHtml(r);" +
                "else if(r.type==='medicine')med=pe(r.detail)+noteHtml(r);" +
                "else if(r.type==='symptoms'){var s=String(r.detail||'');var cut=s.lastIndexOf(' — ');if(cut>0)s=s.substring(0,cut);sym=pe(s)+noteHtml(r);}" +
                "return '<tr><td>'+pe(fmt(r.dateTime))+'</td><td>'+temp+'</td><td>'+med+'</td><td>'+sym+'</td></tr>';" +
                "}).join(''):'<tr><td colspan=4>No recordings for this episode.</td></tr>';" +
                "var html='<!doctype html><html><head><meta charset=UTF-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>body{font-family:Arial,sans-serif;color:#111;padding:28px;font-size:12px}h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:0 0 18px;font-weight:normal}.meta{margin-bottom:20px;line-height:1.6}.meta strong{display:inline-block;min-width:90px}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border:1px solid #bbb;padding:8px;vertical-align:top;text-align:left;word-wrap:break-word}th{background:#eee}.note{margin-top:5px;color:#555;font-style:italic}.footer{margin-top:18px;color:#666;font-size:10px}@media print{body{padding:0}}</style></head><body>'+" +
                "'<h1>Illness Tracker</h1><h2>Episode recordings overview</h2><div class=meta><div><strong>Person:</strong> '+pe(p?p.name:'Unknown')+'</div><div><strong>Episode:</strong> '+pe(e.name)+'</div><div><strong>Started:</strong> '+pe(fmt(e.startedAt))+'</div><div><strong>Records:</strong> '+list.length+'</div></div><table><thead><tr><th>Date & time</th><th>Temperature</th><th>Medication</th><th>Symptom</th></tr></thead><tbody>'+rows+'</tbody></table><div class=footer>Generated '+pe(new Date().toLocaleString())+'</div></body></html>';" +
                "if(window.Android&&Android.printEpisodeOverview){Android.printEpisodeOverview(html);}else{alert('Printing is only available in the Android app.');}" +
                "};" +
                "var panel=document.getElementById('episodePanel');if(panel){var top=panel.querySelector('.top');var actions=top&&top.querySelector('.actions');if(actions&&!document.getElementById('printEpisode')){var b=document.createElement('button');b.id='printEpisode';b.className='btn secondary';b.textContent='🖨️ Print overview';b.onclick=window.printEpisodeOverview;actions.insertBefore(b,actions.firstChild);}}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Illness reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Medication and illness tracker reminders");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            channel.setSound(soundUri, audioAttributes);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript(
                "(function(){var d=document.getElementById('detail'); if(d && !d.classList.contains('hidden')){showOverview(); return 'handled';} return 'finish';})()",
                value -> {
                    if (value != null && value.contains("finish")) MainActivity.super.onBackPressed();
                });
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                    if (am != null && !am.canScheduleExactAlarms()) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception ignored) { }
                    }
                }
            });
        }

        @JavascriptInterface
        public void scheduleNotification(String id, long triggerAtMillis, String title, String body) {
            AlarmScheduler.schedule(context, id, triggerAtMillis, title, body);
        }

        @JavascriptInterface
        public void cancelNotification(String id) {
            AlarmScheduler.cancel(context, id);
        }

        @JavascriptInterface
        public void printEpisodeOverview(String html) {
            runOnUiThread(() -> {
                printWebView = new WebView(MainActivity.this);
                printWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        if (printManager != null) {
                            PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("Illness Tracker episode overview");
                            printManager.print("Illness Tracker episode overview", adapter,
                                    new PrintAttributes.Builder().build());
                        }
                    }
                });
                printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            });
        }
    }
}
