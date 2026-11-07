package com.parsagames.groupsms;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MainActivity extends Activity {

    private static final String ACTION_SENT = "com.parsagames.groupsms.SENT";
    private static final int REQ_SMS = 101;
    private static final long DELAY_MS = 1500;

    private EditText etNumbers, etMessage;
    private Button btnSend;
    private TextView tvStatus;
    private ProgressBar progress;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    private volatile boolean sending = false;
    private volatile boolean queueFinished = false;
    private int total = 0, okCount = 0, failCount = 0;
    private final List<String> failedNumbers = new ArrayList<>();

    private List<String> pendingNumbers = null;
    private String pendingMessage = null;

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String num = intent.getStringExtra("num");
                if (getResultCode() == Activity.RESULT_OK) {
                    okCount++;
                } else {
                    failCount++;
                    failedNumbers.add(num == null ? "؟" : num);
                }
                updateStatus();
            } catch (Throwable ignored) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable t) {
            Toast.makeText(this, "خطا در نمایش صفحه", Toast.LENGTH_LONG).show();
        }
        try {
            IntentFilter f = new IntentFilter(ACTION_SENT);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(sentReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(sentReceiver, f);
            }
            receiverRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (receiverRegistered) unregisterReceiver(sentReceiver);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(10));
        return g;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F4F6F8"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("ارسال پیامک گروهی");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#1B2A41"));
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 0, 0, 16));

        TextView l1 = label("شماره‌ها (هر خط یک شماره)");
        root.addView(l1, lp(0, 0, 0, 6));

        etNumbers = new EditText(this);
        etNumbers.setHint("09121234567\n09351234567");
        etNumbers.setInputType(InputType.TYPE_CLASS_PHONE | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNumbers.setSingleLine(false);
        // کلید → (Next/Done) کیبورد را به «رفتن به خط بعد» تبدیل می‌کنیم
        etNumbers.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        etNumbers.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isEnterKey = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                boolean isAction = actionId == EditorInfo.IME_ACTION_NEXT
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEND
                        || actionId == EditorInfo.IME_ACTION_UNSPECIFIED;
                if (isAction || isEnterKey) {
                    try {
                        EditText e = (EditText) v;
                        int s = Math.max(e.getSelectionStart(), 0);
                        int en = Math.max(e.getSelectionEnd(), 0);
                        e.getText().replace(Math.min(s, en), Math.max(s, en), "\n");
                    } catch (Throwable ignored) {
                    }
                    return true; // فوکوس در همین باکس می‌ماند
                }
                return false;
            }
        });
        etNumbers.setMinLines(7);
        etNumbers.setMaxLines(12);
        etNumbers.setGravity(Gravity.TOP | Gravity.START);
        etNumbers.setPadding(dp(12), dp(12), dp(12), dp(12));
        etNumbers.setBackground(box(Color.WHITE, Color.parseColor("#C9D1D9")));
        root.addView(etNumbers, lp(0, 0, 0, 16));

        TextView l2 = label("متن پیام");
        root.addView(l2, lp(0, 0, 0, 6));

        etMessage = new EditText(this);
        etMessage.setHint("متن پیام را اینجا بنویسید...");
        etMessage.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etMessage.setSingleLine(false);
        etMessage.setMinLines(5);
        etMessage.setMaxLines(10);
        etMessage.setGravity(Gravity.TOP | Gravity.START);
        etMessage.setPadding(dp(12), dp(12), dp(12), dp(12));
        etMessage.setBackground(box(Color.WHITE, Color.parseColor("#C9D1D9")));
        root.addView(etMessage, lp(0, 0, 0, 16));

        btnSend = new Button(this);
        btnSend.setText("ارسال");
        btnSend.setTextSize(18);
        btnSend.setTextColor(Color.WHITE);
        btnSend.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E88E5"));
        bg.setCornerRadius(dp(12));
        btnSend.setBackground(bg);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSendClicked();
            }
        });
        root.addView(btnSend, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, lp(0, 14, 0, 0));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15);
        tvStatus.setTextColor(Color.parseColor("#1B2A41"));
        tvStatus.setLineSpacing(0, 1.3f);
        root.addView(tvStatus, lp(0, 12, 0, 0));

        setContentView(scroll);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#3A4756"));
        return t;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    // ------------------------------------------------------------- Logic

    private String toLatinDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else sb.append(c);
        }
        return sb.toString();
    }

    private List<String> parseNumbers(String raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String[] lines = toLatinDigits(raw).split("[\\r\\n,;،؛]+");
        for (String line : lines) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if ((c >= '0' && c <= '9') || (c == '+' && sb.length() == 0)) sb.append(c);
            }
            String n = sb.toString();
            if (n.startsWith("0098")) n = "+98" + n.substring(4);
            if (n.length() >= 5) set.add(n);
        }
        return new ArrayList<>(set);
    }

    private void onSendClicked() {
        try {
            if (sending) {
                Toast.makeText(this, "ارسال در حال انجام است...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Toast.makeText(this, "این دستگاه قابلیت ارسال پیامک ندارد", Toast.LENGTH_LONG).show();
                return;
            }
            List<String> nums = parseNumbers(etNumbers.getText().toString());
            String msg = etMessage.getText().toString().trim();
            if (nums.isEmpty()) {
                Toast.makeText(this, "حداقل یک شماره وارد کنید", Toast.LENGTH_LONG).show();
                return;
            }
            if (msg.isEmpty()) {
                Toast.makeText(this, "متن پیام را بنویسید", Toast.LENGTH_LONG).show();
                return;
            }
            pendingNumbers = nums;
            pendingMessage = msg;

            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQ_SMS);
                return;
            }
            confirmAndSend();
        } catch (Throwable t) {
            Toast.makeText(this, "خطای غیرمنتظره", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                confirmAndSend();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("دسترسی لازم است")
                        .setMessage("بدون اجازه‌ی ارسال پیامک، برنامه نمی‌تواند کار کند. "
                                + "از تنظیمات گوشی > برنامه‌ها > این برنامه > مجوزها، اجازه‌ی SMS را فعال کنید.")
                        .setPositiveButton("باشه", null)
                        .show();
            }
        }
    }

    private void confirmAndSend() {
        if (pendingNumbers == null || pendingMessage == null) return;
        final List<String> nums = pendingNumbers;
        final String msg = pendingMessage;
        new AlertDialog.Builder(this)
                .setTitle("تأیید ارسال")
                .setMessage("پیام برای " + nums.size() + " شماره ارسال شود؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        startSending(nums, msg);
                    }
                })
                .show();
    }

    private SmsManager getSms() {
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager m = getSystemService(SmsManager.class);
            if (m != null) return m;
        }
        return SmsManager.getDefault();
    }

    private void startSending(final List<String> nums, final String msg) {
        sending = true;
        queueFinished = false;
        total = nums.size();
        okCount = 0;
        failCount = 0;
        failedNumbers.clear();
        btnSend.setEnabled(false);
        btnSend.setText("در حال ارسال...");
        progress.setVisibility(View.VISIBLE);
        progress.setMax(total);
        progress.setProgress(0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateStatus();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < nums.size(); i++) {
                    String num = nums.get(i);
                    try {
                        SmsManager sm = getSms();
                        ArrayList<String> parts = sm.divideMessage(msg);

                        Intent in = new Intent(ACTION_SENT);
                        in.setPackage(getPackageName());
                        in.putExtra("num", num);
                        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                        PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, i, in, flags);

                        ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                        for (int p = 0; p < parts.size(); p++) {
                            sentIntents.add(p == parts.size() - 1 ? pi : null);
                        }
                        sm.sendMultipartTextMessage(num, null, parts, sentIntents, null);
                    } catch (Throwable t) {
                        failCount++;
                        failedNumbers.add(num);
                        postStatus();
                    }
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException ignored) {
                    }
                }
                queueFinished = true;
                postStatus();
                // اگر نتیجه‌ی بعضی پیام‌ها نرسید، بعد از ۲۰ ثانیه کار را پایان‌یافته اعلام کن
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finishSending();
                    }
                }, 20000);
            }
        }).start();
    }

    private void postStatus() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        });
    }

    private void updateStatus() {
        try {
            int done = okCount + failCount;
            progress.setProgress(Math.min(done, total));
            StringBuilder sb = new StringBuilder();
            sb.append("کل شماره‌ها: ").append(total)
                    .append("\nارسال‌شده: ").append(okCount)
                    .append("\nناموفق: ").append(failCount);
            if (queueFinished && done >= total) {
                finishSending();
                return;
            }
            tvStatus.setText(sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private void finishSending() {
        if (!sending) return;
        sending = false;
        try {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnSend.setEnabled(true);
            btnSend.setText("ارسال");
            StringBuilder sb = new StringBuilder();
            sb.append("پایان ارسال ✅")
                    .append("\nکل شماره‌ها: ").append(total)
                    .append("\nارسال‌شده: ").append(okCount)
                    .append("\nناموفق: ").append(failCount);
            int unknown = total - okCount - failCount;
            if (unknown > 0) sb.append("\nنتیجه‌ی نامشخص: ").append(unknown);
            if (!failedNumbers.isEmpty()) {
                sb.append("\n\nشماره‌های ناموفق:");
                for (String n : failedNumbers) sb.append("\n").append(n);
            }
            tvStatus.setText(sb.toString());
        } catch (Throwable ignored) {
        }
    }
}
