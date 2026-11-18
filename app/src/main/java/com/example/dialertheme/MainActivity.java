package com.example.dialertheme;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.app.AlertDialog;
import java.util.Calendar;
import java.util.Collections;
import java.util.TreeMap;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.provider.ContactsContract;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String[] PERMS = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG};

    private AppTheme t;
    private FrameLayout content;
    private final IconView[] tabIcons = new IconView[4];
    private final TextView[] tabLabels = new TextView[4];
    private int tab = 0;
    private String dialNumber = "";
    private String pendingCall;
    private TextView dialDisplay;
    private TextView unblockBtn;   // «رفع مسدودی» داخل شماره‌گیر
    private TextView speedHint;    // نمایش خانه‌ی شماره‌گیر سریع (مثلاً *10)
    private TextView speedDelBtn;  // حذف خانه‌ی انتخاب‌شده از خود شماره‌گیر
    private TextView redialBtn;    // تکرار تماس بعد از اشغالی یا عدم پاسخ
    private TextView addSpeedBtn;  // افزودن شماره‌ی وارد‌شده به یک خانه‌ی سریع

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private boolean has(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = AppTheme.current(this);
        Ui.styleWindow(this, t);
        if (b != null) {
            tab = b.getInt("tab", 0);
            dialNumber = b.getString("dial", "");
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = Ui.text(this, "مخاطبین و تماس", 22, t.primary);
        title.setTypeface(null, Typeface.BOLD);
        Ui.glowText(title, t.primary, t);
        title.setPadding(dp(18), dp(16), dp(18), dp(8));
        root.addView(title);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // top glow line + tab bar
        View line = new View(this);
        line.setBackgroundColor(Ui.alpha(t.primary, t.neon ? 150 : 50));
        root.addView(line, new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(1))));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Ui.alpha(t.surface, 0xE6));
        String[] names = {"مخاطبین", "شماره‌گیر", "تماس‌ها", "تنظیمات"};
        int[] icons = {IconView.CONTACTS, IconView.KEYPAD, IconView.RECENTS, IconView.SETTINGS};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(10), 0, dp(8));
            IconView ic = new IconView(this, icons[i], t.text2, false);
            item.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView lb = Ui.text(this, names[i], 11, t.text2);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, dp(2), 0, 0);
            item.addView(lb);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(idx);
                }
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
            tabIcons[i] = ic;
            tabLabels[i] = lb;
        }
        root.addView(bar);
        setContentView(root);

        requestMissingPermissions();
        handleIntent(getIntent());
        showTab(tab);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("dial", dialNumber);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
        showTab(tab);
    }

    @Override
    protected void onResume() {
        refreshRedial();
        super.onResume();
        if (content != null && tab == 3) showTab(3);
    }

    @Override
    protected void onActivityResult(int rc, int result, Intent data) {
        super.onActivityResult(rc, result, data);
        if (rc == 77) {
            handlePickedContact(result, data);
            return;
        }
        if (rc == 79) {
            if (result == RESULT_OK && data != null) {
                Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                SharedPreferences.Editor e = kpPrefs().edit();
                if (picked == null) e.remove("keysound");
                else e.putString("keysound", picked.toString());
                e.apply();
                recreate();
            }
            return;
        }
        if (rc == 78) {
            if (result == RESULT_OK && data != null) {
                Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                SharedPreferences.Editor e = getSharedPreferences("ringer", MODE_PRIVATE).edit();
                if (picked == null) e.remove("uri");
                else e.putString("uri", picked.toString());
                e.apply();
                recreate();
            }
            return;
        }
        if (rc == 3 && result == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = Wallpaper.save(MainActivity.this, u);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (ok) recreate();
                            else Toast.makeText(MainActivity.this, "تصویر قابل استفاده نبود",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();
        }
    }

    private void handleIntent(Intent i) {
        if (i == null || i.getBooleanExtra("handled", false)) return;
        Uri d = i.getData();
        if (d != null && "tel".equals(d.getScheme())) {
            dialNumber = Uri.decode(d.getSchemeSpecificPart());
            tab = 1;
            i.putExtra("handled", true);
        }
    }

    private void requestMissingPermissions() {
        List<String> need = new ArrayList<>();
        for (String p : PERMS) if (!has(p)) need.add(p);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        showTab(tab);
        if (pendingCall != null && has(Manifest.permission.CALL_PHONE)) {
            String n = pendingCall;
            pendingCall = null;
            placeCall(n);
        }
    }

    private void showTab(int i) {
        tab = i;
        content.removeAllViews();
        View v;
        switch (i) {
            case 0: v = buildContacts(); break;
            case 1: v = buildKeypad(); break;
            case 2: v = buildRecents(); break;
            default: v = buildSettings(); break;
        }
        content.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        for (int k = 0; k < 4; k++) {
            boolean sel = k == i;
            tabIcons[k].setStyle(sel ? t.primary : t.text2, sel && t.neon);
            tabLabels[k].setTextColor(sel ? t.primary : t.text2);
            tabLabels[k].setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ---------------------------------------------------------------- actions

    private ToneGenerator toneGen;

    /** پخش صدای انتخاب‌شده از گوشی (یک بار، بدون نیاز به فایل جداگانه) */
    private void playCustomKeySound(String uri) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mp.setDataSource(this, Uri.parse(uri));
            mp.prepare();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    m.release();
                }
            });
            mp.start();
        } catch (Exception ignored) {
        }
    }

    private void launchKeySoundPicker() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        try {
            startActivityForResult(i, 79);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "انتخاب صدا در این گوشی ممکن نیست", Toast.LENGTH_SHORT).show();
        }
    }

    /** پخش صدای کوتاه برای هر کلید شماره‌گیری، طبق انتخاب کاربر در تنظیمات */
    private void playKeySound(String k) {
        String custom = kpPrefs().getString("keysound", null);
        if (custom != null) {
            playCustomKeySound(custom);
            return;
        }
        int mode = kpPrefs().getInt("sound", 0);
        if (mode == 4) return; // بی‌صدا
        try {
            if (toneGen == null) toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            int code;
            switch (mode) {
                case 1: code = ToneGenerator.TONE_PROP_BEEP; break;
                case 2: code = ToneGenerator.TONE_PROP_ACK; break;
                case 3: code = ToneGenerator.TONE_CDMA_PIP; break;
                default: code = dtmfCode(k); break;
            }
            toneGen.startTone(code, 120);
        } catch (RuntimeException ignored) {
            // بعضی دستگاه‌ها اجازه‌ی تولید صدا را نمی‌دهند
        }
    }

    private static int dtmfCode(String k) {
        switch (k) {
            case "0": return ToneGenerator.TONE_DTMF_0;
            case "1": return ToneGenerator.TONE_DTMF_1;
            case "2": return ToneGenerator.TONE_DTMF_2;
            case "3": return ToneGenerator.TONE_DTMF_3;
            case "4": return ToneGenerator.TONE_DTMF_4;
            case "5": return ToneGenerator.TONE_DTMF_5;
            case "6": return ToneGenerator.TONE_DTMF_6;
            case "7": return ToneGenerator.TONE_DTMF_7;
            case "8": return ToneGenerator.TONE_DTMF_8;
            case "9": return ToneGenerator.TONE_DTMF_9;
            case "*": return ToneGenerator.TONE_DTMF_S;
            default: return ToneGenerator.TONE_DTMF_P;
        }
    }

    private EditText pendingPick;      // فیلد تنظیمات که شماره‌اش پر می‌شود
    private int pendingSpeedDigit = 0; // کلید شماره‌گیر سریع که شماره‌اش ذخیره می‌شود

    /** انتخاب شماره از مخاطبین برای فیلد تنظیمات */
    private void pickContact(final EditText target) {
        pendingPick = target;
        pendingSpeedDigit = 0;
        launchContactPicker();
    }

    /** باز کردن صفحه‌ی مخاطبین گوشی (بدون نیاز به اجازه‌ی جداگانه) */
    /** انتخاب آهنگ زنگ تماس ورودی از فهرست خود گوشی */
    private void launchRingtonePicker(String existing) {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                existing == null ? null : Uri.parse(existing));
        try {
            startActivityForResult(i, 78);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "انتخاب آهنگ در این گوشی ممکن نیست", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchContactPicker() {
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(i, 77);
        } catch (ActivityNotFoundException e) {
            pendingPick = null;
            pendingSpeedDigit = 0;
            Toast.makeText(this, "برنامه‌ی مخاطبین پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePickedContact(int result, Intent data) {
        EditText target = pendingPick;
        int digit = pendingSpeedDigit;
        pendingPick = null;
        pendingSpeedDigit = 0;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        String number = null;
        String name = "";
        Cursor cur = null;
        try {
            cur = getContentResolver().query(data.getData(),
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                number = cur.getString(0);
                String n = cur.getString(1);
                name = n == null ? "" : n;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        if (number == null || number.trim().isEmpty()) {
            Toast.makeText(this, "شماره‌ای پیدا نشد", Toast.LENGTH_SHORT).show();
            return;
        }
        number = number.trim();
        if (digit > 0) {
            speedPrefs().edit().putString("d" + digit, number).putString("d" + digit + "n", name).apply();
            dialNumber = "";
            updateDial();
            Toast.makeText(this, "شماره روی کلید " + digit + " ذخیره شد", Toast.LENGTH_SHORT).show();
        } else if (target != null) {
            target.setText(number);
        }
    }

    private SharedPreferences speedPrefs() {
        return getSharedPreferences("speed", MODE_PRIVATE);
    }

    /** اگر شماره‌گیری به شکل *NN باشد (NN بین ۱ و ۱۰۰)، شماره‌ی خانه‌ی سریع را برمی‌گرداند. */
    private String speedSlotFromDial(String dial) {
        if (dial == null || !dial.matches("\\*[0-9]{1,3}#?")) return null;
        String digits = dial.substring(1).replace("#", "");
        int n = Integer.parseInt(digits);
        return (n >= 1 && n <= 100) ? String.valueOf(n) : null;
    }

    private static int parseSlot(String v) {
        try {
            int n = Integer.parseInt(v.trim());
            return (n >= 1 && n <= 100) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String speedNumber(int digit) {
        return speedPrefs().getString("d" + digit, "");
    }

    /** باز کردن چت واتساپ با شماره (شماره‌ی ایرانی 09… به 989… تبدیل می‌شود). */
    private void openWhatsApp(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 11) {
            digits = "98" + digits.substring(1);
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits)));
        } catch (Exception e) {
            Toast.makeText(this, "واتساپ پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    /** تاریخ شمسی همراه با نام روز و ساعت برای فهرست تماس‌ها */
    static String jalaliDateTime(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int[] j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return weekdayName(c.get(Calendar.DAY_OF_WEEK)) + " " + j[2] + " " + JALI_MONTHS[j[1] - 1]
                + " " + j[0] + "  " + String.format(Locale.US, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private void placeCall(String number) {
        if (TextUtils.isEmpty(number)) return;
        if (!has(Manifest.permission.CALL_PHONE)) {
            pendingCall = number;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 1);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number.trim(), null)));
        } catch (Exception e) {
            Toast.makeText(this, "برقراری تماس ممکن نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendSms(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))));
        } catch (Exception e) {
            Toast.makeText(this, "برنامه پیامک پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDefaultDialer() {
        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        return tm != null && getPackageName().equals(tm.getDefaultDialerPackage());
    }

    private void requestDefaultDialer() {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                i = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
            } else {
                i = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
            }
            startActivityForResult(i, 2);
        } catch (Exception e) {
            Toast.makeText(this, "این گوشی اجازه تغییر شماره‌گیر را از اینجا نمی‌دهد", Toast.LENGTH_LONG).show();
        }
    }

    private View message(String text, String btn, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView tv = Ui.text(this, text, 15, t.text2);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);
        if (btn != null) {
            TextView b = Ui.pill(this, btn, t.primary, t.onPrimary);
            b.setOnClickListener(l);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
            p.topMargin = dp(16);
            box.addView(b, p);
        }
        return box;
    }

    // --------------------------------------------------------------- contacts

    private View buildContacts() {
        if (!has(Manifest.permission.READ_CONTACTS)) {
            return message("برای نمایش مخاطبین، اجازه دسترسی لازم است.", "اجازه دادن",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestMissingPermissions();
                        }
                    });
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable sbg = Ui.rect(this, Ui.alpha(t.surface, 0xCC), 26);
        sbg.setStroke(dp(1), Ui.alpha(t.primary, t.neon ? 0xB0 : 0x40));
        searchBox.setBackground(sbg);
        searchBox.addView(new IconView(this, IconView.SEARCH, t.primary, t.neon),
                new LinearLayout.LayoutParams(dp(22), dp(22)));
        EditText search = new EditText(this);
        search.setHint("جستجو در مخاطبین");
        search.setHintTextColor(t.text2);
        search.setTextColor(t.text);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(dp(10), dp(12), dp(10), dp(12));
        searchBox.addView(search, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.setMargins(dp(14), dp(4), dp(14), dp(8));
        box.addView(searchBox, sp);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        box.addView(lv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final ContactAdapter ad = new ContactAdapter();
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                ContactUtil.Contact c = (ContactUtil.Contact) ad.getItem(pos);
                placeCall(c.number);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ContactUtil.Contact> l = ContactUtil.loadAll(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ad.setData(l);
                    }
                });
            }
        }).start();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                ad.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable e) {
            }
        });
        return box;
    }

    private static class ContactHolder {
        TextView avatar, name, number, wa, unblock;
        IconView sms;
    }

    private class ContactAdapter extends BaseAdapter {
        private List<ContactUtil.Contact> all = new ArrayList<>();
        private List<ContactUtil.Contact> shown = new ArrayList<>();
        private String q = "";

        void setData(List<ContactUtil.Contact> l) {
            all = l;
            filter(q);
        }

        void filter(String query) {
            q = query == null ? "" : query.trim().toLowerCase();
            String qd = q.replaceAll("[^0-9+]", "");
            List<ContactUtil.Contact> out = new ArrayList<>();
            for (ContactUtil.Contact c : all) {
                boolean ok = q.isEmpty()
                        || c.name.toLowerCase().contains(q)
                        || (!qd.isEmpty() && c.number.replaceAll("[^0-9+]", "").contains(qd));
                if (ok) out.add(c);
            }
            shown = out;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            ContactHolder h;
            if (cv == null) {
                h = new ContactHolder();
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));

                h.avatar = Ui.text(MainActivity.this, "", 18, t.primary);
                h.avatar.setTypeface(null, Typeface.BOLD);
                h.avatar.setGravity(Gravity.CENTER);
                h.avatar.setBackground(Ui.avatarBg(MainActivity.this, t));
                Ui.glowText(h.avatar, t.primary, t);
                row.addView(h.avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                h.name = Ui.text(MainActivity.this, "", 16, t.text);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.number = Ui.text(MainActivity.this, "", 13, t.text2);
                h.number.setSingleLine(true);
                h.number.setTextDirection(View.TEXT_DIRECTION_LTR);
                col.addView(h.name);
                col.addView(h.number);
                h.unblock = Ui.pill(MainActivity.this, "رفع مسدودی", t.primary, t.onPrimary);
                LinearLayout.LayoutParams ub = new LinearLayout.LayoutParams(WRAP, WRAP);
                ub.topMargin = dp(4);
                col.addView(h.unblock, ub);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.setMarginStart(dp(12));
                row.addView(col, cp);

                h.sms = new IconView(MainActivity.this, IconView.CHAT, t.accent, t.neon);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
                ip.setMarginStart(dp(8));
                row.addView(h.sms, ip);

                h.wa = Ui.pill(MainActivity.this, "واتساپ", t.primary, t.onPrimary);
                h.wa.setTextSize(10);
                h.wa.setPadding(dp(6), 0, dp(6), 0);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(WRAP, dp(22));
                wp.setMarginStart(dp(8));
                row.addView(h.wa, wp);

                row.setTag(h);
                cv = row;
            } else {
                h = (ContactHolder) cv.getTag();
            }
            final ContactUtil.Contact c = shown.get(pos);
            h.avatar.setText(c.name.isEmpty() ? "#" : c.name.substring(0, 1).toUpperCase());
            h.name.setText(c.name);
            String badge = (c.whatsapp ? "  · واتساپ" : "") + (c.telegram ? "  · تلگرام" : "");
            h.number.setText(c.number + badge);
            h.wa.setVisibility(c.whatsapp ? View.VISIBLE : View.GONE);
            final String conBlockKey = blockedKeyFor(c.number);
            h.unblock.setVisibility(conBlockKey != null ? View.VISIBLE : View.GONE);
            h.unblock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unblockNumber(conBlockKey);
                    Toast.makeText(MainActivity.this, "مسدودی برداشته شد", Toast.LENGTH_SHORT).show();
                    ContactAdapter.this.notifyDataSetChanged();
                }
            });
            h.wa.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openWhatsApp(c.number);
                }
            });
            h.sms.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendSms(c.number);
                }
            });
            return cv;
        }
    }

    // ----------------------------------------------------------------- keypad

    private int[] keyPalette() {
        switch (kpPrefs().getInt("palette", 0)) {
            case 1:
                return new int[]{0xFFFF5252, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0};
            case 2:
                return new int[]{0xFFF8BBD0, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFF9C4, 0xFFD1C4E9, 0xFFFFCCBC};
            case 3:
                return new int[]{t.primary, mix(t.primary, t.accent, 0.5f), t.accent};
            default:
                return new int[]{t.primary, t.accent, AppTheme.GREEN, 0xFFFFC107, 0xFFFF4FD8, 0xFF3D8BFF};
        }
    }

    /** اندازه‌ی کلید به دی‌پی: کوچک / متوسط / بزرگ */
    private int kpSizeDp() {
        int[] sizes = {56, 68, 80};
        int i = kpPrefs().getInt("size", 1);
        return sizes[(i < 0 || i > 2) ? 1 : i];
    }

    /** رنگ پس‌زمینه‌ی کلید */
    private int kpBgColor() {
        switch (kpPrefs().getInt("bg", 0)) {
            case 1:
                return 0xFFF4F4F7;
            case 2:
                return mix(t.bg, t.primary, 0.25f);
            case 3:
                return t.surface;
            default:
                return 0xFF1B1B22;
        }
    }

    /** شکل کلید: 0 سه‌بعدی دایره، 1 تخت دایره، 2 مربع گرد سه‌بعدی، 3 قرصی */
    private GradientDrawable keyShape(int rim, int bg) {
        int style = kpPrefs().getInt("style", 0);
        boolean threeD = style == 0 || style == 2;
        GradientDrawable g;
        if (threeD) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mix(bg, 0xFFFFFFFF, 0.25f), mix(bg, 0xFF000000, 0.35f)});
        } else {
            g = new GradientDrawable();
            g.setColor(bg);
        }
        if (style == 0 || style == 1) {
            g.setShape(GradientDrawable.OVAL);
        } else {
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(Ui.dp(this, style == 2 ? 18 : kpSizeDp() / 2f));
        }
        g.setStroke(Ui.dp(this, 2), rim);
        return g;
    }

    // ---- تنظیمات صفحه شماره‌گیر (شکل و رنگ) ----
    private SharedPreferences kpPrefs() {
        return getSharedPreferences("keypad", MODE_PRIVATE);
    }

    private boolean kpFlat() {
        return kpPrefs().getInt("style", 0) == 1;
    }

    private boolean kpMono() {
        return kpPrefs().getInt("color", 0) == 1;
    }

    private CharSequence colorize(String s) {
        if (kpMono()) return s;
        SpannableString sp = new SpannableString(s);
        int[] pal = keyPalette();
        for (int i = 0; i < s.length(); i++) {
            sp.setSpan(new ForegroundColorSpan(pal[i % pal.length]), i, i + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    private void updateDial() {
        if (dialDisplay != null) dialDisplay.setText(colorize(dialNumber));
        refreshUnblock();
        refreshSpeedHint();
    }

    /** شماره‌ی آخرین تماسی که بی‌پاسخ، اشغال یا رد شده (یا null) */
    private String lastUnansweredNumber() {
        if (!has(Manifest.permission.READ_CALL_LOG)) return null;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION},
                    null, null, CallLog.Calls.DATE + " DESC");
            if (cur != null && cur.moveToFirst()) {
                String num = cur.getString(0);
                int type = cur.getInt(1);
                long dur = cur.getLong(2);
                boolean failed = type == CallLog.Calls.MISSED_TYPE
                        || type == CallLog.Calls.REJECTED_TYPE
                        || (type == CallLog.Calls.OUTGOING_TYPE && dur == 0);
                if (failed && num != null && !num.isEmpty()) return num;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    private void refreshRedial() {
        if (redialBtn == null) return;
        redialBtn.setVisibility(lastUnansweredNumber() != null ? View.VISIBLE : View.GONE);
    }

    private void refreshSpeedHint() {
        if (speedHint == null) return;
        String slot = speedSlotFromDial(dialNumber);
        boolean plainNumber = dialNumber.matches("[0-9+]{2,}");
        if (addSpeedBtn != null) addSpeedBtn.setVisibility(plainNumber ? View.VISIBLE : View.GONE);
        if (slot == null) {
            speedHint.setText("");
            if (speedDelBtn != null) speedDelBtn.setVisibility(View.GONE);
            return;
        }
        String n = speedNumber(Integer.parseInt(slot));
        speedHint.setText("خانه‌ی " + slot + ": " + (n.isEmpty() ? "خالی است" : n));
        if (speedDelBtn != null) speedDelBtn.setVisibility(n.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** نام مخاطبی که این شماره را دارد (رشته‌ی خالی اگر پیدا نشد) */
    private String lookupDisplayName(String number) {
        if (!has(Manifest.permission.READ_CONTACTS)) return "";
        Cursor cur = null;
        try {
            cur = getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                return n == null ? "" : n;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return "";
    }

    /** باز کردن فرم ذخیره‌ی مخاطب با شماره‌ی تازه‌ذخیره‌شده تا نام آن هم ثبت شود */
    private void openSaveToContacts(String number) {
        Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
        i.putExtra(ContactsContract.Intents.Insert.PHONE, number);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "برنامه‌ی مخاطبین پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    /** دریافت کد خانه (۱ تا ۱۰۰) و ذخیره‌ی شماره‌ی روی صفحه در همان خانه (افزودن یا تغییر) */
    private void showAddSpeedDialog() {
        final String number = dialNumber;
        final EditText codeField = new EditText(this);
        codeField.setSingleLine(true);
        codeField.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeField.setHint("کد (۱ تا ۱۰۰)");
        codeField.setTextDirection(View.TEXT_DIRECTION_LTR);
        new AlertDialog.Builder(this)
                .setTitle("افزودن به شماره‌گیری سریع")
                .setMessage("شماره: " + number)
                .setView(codeField)
                .setPositiveButton("ذخیره", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int code = parseSlot(codeField.getText().toString());
                        if (code < 1) {
                            Toast.makeText(MainActivity.this, "کد باید بین ۱ تا ۱۰۰ باشد", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        speedPrefs().edit().putString("d" + code, number)
                                .putString("d" + code + "n", lookupDisplayName(number)).apply();
                        dialNumber = "";
                        updateDial();
                        openSaveToContacts(number);
                        Toast.makeText(MainActivity.this, "روی خانه‌ی " + code + " ذخیره شد", Toast.LENGTH_SHORT).show();
                        refreshSpeedHint();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }


    // ---- مسدودی‌ها (همان داده‌ای که TimedBlock در InCallActivity ذخیره می‌کند) ----
    private SharedPreferences blockPrefs() {
        return getSharedPreferences("timed_blocks", MODE_PRIVATE);
    }

    private java.util.List<String> activeBlocked() {
        java.util.List<String> out = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        SharedPreferences p = blockPrefs();
        for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
            long exp = p.getLong(e.getKey(), 0L);
            if (exp > now) {
                out.add(e.getKey());
            } else {
                p.edit().remove(e.getKey()).apply();
            }
        }
        return out;
    }

    private long expiryOfBlock(String number) {
        return blockPrefs().getLong(number, 0L);
    }

    private void unblockNumber(String number) {
        blockPrefs().edit().remove(number).apply();
    }

    private void unblockAllNumbers() {
        blockPrefs().edit().clear().apply();
    }

    /** اگر شماره‌ی وارد‌شده مسدود باشد، کلید «رفع مسدودی» نمایش داده می‌شود. */
    private String blockedKeyFor(String n) {
        String want = tail(n);
        if (want.isEmpty()) return null;
        for (String k : activeBlocked()) {
            if (want.equals(tail(k))) return k;
        }
        return null;
    }

    private void refreshUnblock() {
        if (unblockBtn == null) return;
        unblockBtn.setVisibility(blockedKeyFor(dialNumber) != null ? View.VISIBLE : View.GONE);
    }

    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** دکمه‌ی سه‌بعدی: گرادیان روشن به تیره + حاشیه‌ی رنگی */
    private GradientDrawable key3d(int color) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mix(color, 0xFFFFFFFF, 0.35f), mix(color, 0xFF000000, 0.45f)});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Ui.dp(this, 2), color);
        return g;
    }

    private String subFor(String k) {
        switch (k) {
            case "2": return "ABC";
            case "3": return "DEF";
            case "4": return "GHI";
            case "5": return "JKL";
            case "6": return "MNO";
            case "7": return "PQRS";
            case "8": return "TUV";
            case "9": return "WXYZ";
            case "0": return "+";
            default: return "";
        }
    }

    private View keyView(final String k, int idx) {
        int[] pal = keyPalette();
        int color = kpMono() ? t.primary : pal[idx % pal.length];
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setBackground(keyShape(color, kpBgColor()));
        TextView d = Ui.text(this, k, 26, color);
        d.setTypeface(null, Typeface.BOLD);
        d.setGravity(Gravity.CENTER);
        if (t.neon) d.setShadowLayer(6f, 0f, 0f, color);
        v.addView(d);
        String sub = subFor(k);
        if (!sub.isEmpty()) {
            TextView s = Ui.text(this, sub, 9, t.text2);
            s.setGravity(Gravity.CENTER);
            v.addView(s);
        }
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playKeySound(k);
                dialNumber += k;
                updateDial();
            }
        });
        if (k.equals("0")) {
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    dialNumber += "+";
                    updateDial();
                    return true;
                }
            });
        } else if (k.matches("[1-9]")) {
            // شماره‌گیری سریع: نگه‌داشتن کلید، تماس با شماره‌ی ذخیره‌شده‌ی آن کلید
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    String n = speedNumber(k.charAt(0) - '0');
                    if (n.isEmpty()) {
                        pendingSpeedDigit = k.charAt(0) - '0';
                        pendingPick = null;
                        launchContactPicker();
                    } else {
                        placeCall(n);
                    }
                    return true;
                }
            });
        }
        return v;
    }

    private LinearLayout.LayoutParams keyLp() {
        int size = dp(kpSizeDp());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(dp(8), dp(6), dp(8), dp(6));
        return p;
    }

    private View buildKeypad() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        box.setPadding(dp(16), dp(8), dp(16), dp(16));

        dialDisplay = Ui.text(this, dialNumber, 32, t.text);
        dialDisplay.setGravity(Gravity.CENTER);
        dialDisplay.setSingleLine(true);
        dialDisplay.setEllipsize(TextUtils.TruncateAt.START);
        dialDisplay.setMinHeight(dp(64));
        if (t.neon) dialDisplay.setShadowLayer(6f, 0f, 0f, t.primary);
        updateDial();
        box.addView(dialDisplay, new LinearLayout.LayoutParams(MATCH, WRAP));

        unblockBtn = Ui.pill(this, "رفع مسدودی این شماره", t.primary, t.onPrimary);
        unblockBtn.setVisibility(View.GONE);
        unblockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = blockedKeyFor(dialNumber);
                if (key != null) {
                    unblockNumber(key);
                    Toast.makeText(MainActivity.this, "مسدودی برداشته شد", Toast.LENGTH_SHORT).show();
                }
                refreshUnblock();
            }
        });
        box.addView(unblockBtn, fullTop(6));
        refreshUnblock();

        speedHint = Ui.text(this, "", 15, t.primary);
        speedHint.setGravity(Gravity.CENTER);
        box.addView(speedHint, new LinearLayout.LayoutParams(MATCH, WRAP));

        speedDelBtn = Ui.pill(this, "حذف این خانه", t.surface, t.text);
        speedDelBtn.setVisibility(View.GONE);
        speedDelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String slot = speedSlotFromDial(dialNumber);
                if (slot != null) {
                    speedPrefs().edit().remove("d" + slot).remove("d" + slot + "n").apply();
                    Toast.makeText(MainActivity.this, "خانه‌ی " + slot + " حذف شد", Toast.LENGTH_SHORT).show();
                }
                refreshSpeedHint();
            }
        });
        box.addView(speedDelBtn, fullTop(4));

        addSpeedBtn = Ui.pill(this, "ذخیره", t.primary, t.onPrimary);
        addSpeedBtn.setVisibility(View.GONE);
        addSpeedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddSpeedDialog();
            }
        });

        redialBtn = Ui.pill(this, "تکرار تماس", t.primary, t.onPrimary);
        redialBtn.setVisibility(View.GONE);
        redialBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = lastUnansweredNumber();
                if (n != null) placeCall(n);
                refreshRedial();
            }
        });
        box.addView(redialBtn, fullTop(4));
        refreshRedial();
        refreshSpeedHint();

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        int keyIdx = 0;
        for (String[] rowKeys : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            for (String k : rowKeys) r.addView(keyView(k, keyIdx++), keyLp());
            box.addView(r);
        }

        LinearLayout last = new LinearLayout(this);
        last.setGravity(Gravity.CENTER);
        addSpeedBtn.setTextSize(12);
        addSpeedBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout saveSlot = new LinearLayout(this);
        saveSlot.setGravity(Gravity.CENTER);
        saveSlot.addView(addSpeedBtn, new LinearLayout.LayoutParams(WRAP, WRAP));
        last.addView(saveSlot, keyLp());

        LinearLayout call = new LinearLayout(this);
        call.setGravity(Gravity.CENTER);
        call.setBackground(keyShape(AppTheme.GREEN, AppTheme.GREEN));
        call.addView(new IconView(this, IconView.PHONE, Color.WHITE, false),
                new LinearLayout.LayoutParams(dp(30), dp(30)));
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String slotNum = speedSlotFromDial(dialNumber);
                if (slotNum != null) {
                    String target = speedNumber(Integer.parseInt(slotNum));
                    if (target.isEmpty()) {
                        pendingSpeedDigit = Integer.parseInt(slotNum);
                        pendingPick = null;
                        launchContactPicker();
                    } else {
                        placeCall(target);
                    }
                } else {
                    placeCall(dialNumber);
                }
            }
        });
        last.addView(call, keyLp());

        LinearLayout back = new LinearLayout(this);
        back.setGravity(Gravity.CENTER);
        back.addView(new IconView(this, IconView.BACK, t.text2, false),
                new LinearLayout.LayoutParams(dp(30), dp(30)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!dialNumber.isEmpty()) {
                    dialNumber = dialNumber.substring(0, dialNumber.length() - 1);
                    updateDial();
                }
            }
        });
        back.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                dialNumber = "";
                updateDial();
                return true;
            }
        });
        last.addView(back, keyLp());
        box.addView(last);

        sv.addView(box, new ScrollView.LayoutParams(MATCH, WRAP));
        return sv;
    }

    // ---------------------------------------------------------------- recents

    private static class Recent {
        String number, name;
        int type;
        long date, duration;
    }

    private static class RecentHolder {
        IconView icon;
        TextView title, sub, unblock;
    }

    // ---------------------------------------------------------- گزارش تماس‌ها

    private static final String[] JALI_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};

    /** تبدیل تاریخ میلادی به شمسی: {سال، ماه، روز} */
    static int[] toJalali(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100)
                + ((gy2 + 399) / 400) + gd + gdm[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + (days % 31);
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + ((days - 186) % 30);
        }
        return new int[]{jy, jm, jd};
    }

    private static String weekdayName(int calDayOfWeek) {
        // Calendar: SUNDAY=1 ... SATURDAY=7
        String[] names = {"", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"};
        return names[calDayOfWeek];
    }

    private static class DayStat {
        int jy, jm, jd;
        String weekday = "";
        int incoming, outgoing, missed;
        long seconds;
    }

    private static String fmtDuration(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, x = sec % 60;
        return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, x);
    }

    private void showCallReport() {
        if (!has(Manifest.permission.READ_CALL_LOG)) {
            requestMissingPermissions();
            return;
        }
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(14));
        box.addView(Ui.text(this, "در حال بارگذاری…", 15, t.text2));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("گزارش تماس‌ها")
                .setView(sv)
                .setPositiveButton("بستن", null)
                .create();
        dlg.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final TreeMap<Long, DayStat> days = new TreeMap<>(Collections.reverseOrder());
                Cursor cur = null;
                try {
                    cur = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                            new String[]{CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                            null, null, CallLog.Calls.DATE + " DESC");
                    Calendar cal = Calendar.getInstance();
                    while (cur != null && cur.moveToNext()) {
                        int type = cur.getInt(0);
                        long date = cur.getLong(1);
                        long dur = cur.getLong(2);
                        cal.setTimeInMillis(date);
                        int[] j = toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH));
                        long key = j[0] * 10000L + j[1] * 100L + j[2];
                        DayStat ds = days.get(key);
                        if (ds == null) {
                            ds = new DayStat();
                            ds.jy = j[0];
                            ds.jm = j[1];
                            ds.jd = j[2];
                            ds.weekday = weekdayName(cal.get(Calendar.DAY_OF_WEEK));
                            days.put(key, ds);
                        }
                        // 1=ورودی، 2=خروجی، 3=بی‌پاسخ، 5=رد شده، 6=مسدود
                        if (type == CallLog.Calls.INCOMING_TYPE) ds.incoming++;
                        else if (type == CallLog.Calls.OUTGOING_TYPE) ds.outgoing++;
                        else ds.missed++;
                        ds.seconds += dur;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (cur != null) cur.close();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        box.removeAllViews();
                        if (days.isEmpty()) {
                            box.addView(Ui.text(MainActivity.this, "هیچ تماسی ثبت نشده است.", 15, t.text2));
                            return;
                        }
                        int tIn = 0, tOut = 0, tMiss = 0;
                        long tSec = 0;
                        for (DayStat ds : days.values()) {
                            tIn += ds.incoming;
                            tOut += ds.outgoing;
                            tMiss += ds.missed;
                            tSec += ds.seconds;
                            TextView row = Ui.text(MainActivity.this,
                                    ds.weekday + "  " + ds.jd + " " + JALI_MONTHS[ds.jm - 1] + " " + ds.jy
                                            + "\n"
                                            + "ورودی: " + ds.incoming
                                            + "   خروجی: " + ds.outgoing
                                            + "   بی‌پاسخ: " + ds.missed
                                            + "\nمدت مکالمه: " + fmtDuration(ds.seconds),
                                    14, t.text);
                            row.setPadding(dp(12), dp(10), dp(12), dp(10));
                            row.setBackground(Ui.rect(MainActivity.this, Ui.alpha(t.surface, 0xCC), 12));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.topMargin = dp(8);
                            box.addView(row, lp);
                        }
                        TextView total = Ui.text(MainActivity.this,
                                "جمع کل — ورودی: " + tIn + "   خروجی: " + tOut
                                        + "   بی‌پاسخ: " + tMiss + "\nمدت مکالمه: " + fmtDuration(tSec),
                                15, t.primary);
                        total.setPadding(0, dp(12), 0, 0);
                        box.addView(total);
                    }
                });
            }
        }).start();
    }

    private static String tail(String n) {
        String d = n.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    private View buildRecents() {
        if (!has(Manifest.permission.READ_CALL_LOG)) {
            return message("برای نمایش تاریخچه تماس‌ها، اجازه دسترسی لازم است.", "اجازه دادن",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestMissingPermissions();
                        }
                    });
        }
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        final RecentAdapter ad = new RecentAdapter();
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Recent r = (Recent) ad.getItem(pos);
                placeCall(r.number);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Recent> out = new ArrayList<>();
                Map<String, String> names = new HashMap<>();
                if (has(Manifest.permission.READ_CONTACTS)) {
                    for (ContactUtil.Contact c : ContactUtil.loadAll(MainActivity.this)) {
                        names.put(tail(c.number), c.name);
                    }
                }
                Cursor cur = null;
                try {
                    cur = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                            new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                            null, null, CallLog.Calls.DATE + " DESC");
                    while (cur != null && cur.moveToNext() && out.size() < 200) {
                        Recent r = new Recent();
                        r.number = cur.getString(0);
                        String cn = cur.getString(1);
                        r.type = cur.getInt(2);
                        r.date = cur.getLong(3);
                        r.duration = cur.getLong(4);
                        if (!TextUtils.isEmpty(cn)) r.name = cn;
                        else if (!TextUtils.isEmpty(r.number)) r.name = names.get(tail(r.number));
                        out.add(r);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (cur != null) cur.close();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ad.setData(out);
                    }
                });
            }
        }).start();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView rep = Ui.pill(this, "گزارش تماس‌ها به تفکیک روز", t.primary, t.onPrimary);
        rep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCallReport();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(WRAP, WRAP);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.setMargins(dp(14), dp(8), dp(14), dp(8));
        wrap.addView(rep, rp);
        wrap.addView(lv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return wrap;
    }

    private class RecentAdapter extends BaseAdapter {
        private List<Recent> data = new ArrayList<>();
        private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

        void setData(List<Recent> l) {
            data = l;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            RecentHolder h;
            if (cv == null) {
                h = new RecentHolder();
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));

                h.icon = new IconView(MainActivity.this, IconView.PHONE, t.primary, t.neon);
                row.addView(h.icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                h.title = Ui.text(MainActivity.this, "", 16, t.text);
                h.title.setSingleLine(true);
                h.title.setEllipsize(TextUtils.TruncateAt.END);
                h.sub = Ui.text(MainActivity.this, "", 13, t.text2);
                h.sub.setSingleLine(true);
                col.addView(h.title);
                col.addView(h.sub);
                h.unblock = Ui.pill(MainActivity.this, "رفع مسدودی", t.primary, t.onPrimary);
                LinearLayout.LayoutParams ub = new LinearLayout.LayoutParams(WRAP, WRAP);
                ub.topMargin = dp(4);
                col.addView(h.unblock, ub);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.setMarginStart(dp(12));
                row.addView(col, cp);

                row.setTag(h);
                cv = row;
            } else {
                h = (RecentHolder) cv.getTag();
            }
            Recent r = data.get(pos);
            boolean missed = r.type == 3 || r.type == 5;
            String title = !TextUtils.isEmpty(r.name) ? r.name
                    : (!TextUtils.isEmpty(r.number) ? r.number : "ناشناس");
            String typeLabel;
            switch (r.type) {
                case 1: typeLabel = "ورودی"; break;
                case 2: typeLabel = "خروجی"; break;
                case 3: typeLabel = "از دست رفته"; break;
                case 5: typeLabel = "رد شده"; break;
                default: typeLabel = "";
            }
            String sub = typeLabel + " • " + jalaliDateTime(r.date);
            if (r.duration > 0) {
                sub += String.format(Locale.US, " • %d:%02d", r.duration / 60, r.duration % 60);
            }
            int ic = missed ? AppTheme.RED : (r.type == 2 ? t.primary : AppTheme.GREEN);
            h.icon.setStyle(ic, t.neon);
            h.title.setText(title);
            h.title.setTextColor(missed ? AppTheme.RED : t.text);
            h.sub.setText(sub);
            final String recBlockKey = r.number == null ? null : blockedKeyFor(r.number);
            h.unblock.setVisibility(recBlockKey != null ? View.VISIBLE : View.GONE);
            h.unblock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unblockNumber(recBlockKey);
                    Toast.makeText(MainActivity.this, "مسدودی برداشته شد", Toast.LENGTH_SHORT).show();
                    RecentAdapter.this.notifyDataSetChanged();
                }
            });
            return cv;
        }
    }

    // --------------------------------------------------------------- settings

    private TextView header(String s) {
        TextView tv = Ui.text(this, s, 16, t.primary);
        tv.setTypeface(null, Typeface.BOLD);
        Ui.glowText(tv, t.primary, t);
        tv.setPadding(0, dp(20), 0, dp(8));
        return tv;
    }

    private LinearLayout.LayoutParams fullTop(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(topDp);
        return p;
    }

    private static final int[] SWATCHES = {
            0xFF000000, 0xFF0A0420, 0xFF121212, 0xFF202124, 0xFF1A237E, 0xFF14001F, 0xFF0D1B2A, 0xFF1B3324,
            0xFFFFFFFF, 0xFFF1F3F4, 0xFFFCE4EC, 0xFFFFF8E1, 0xFFE3F2FD, 0xFFEDE7F6, 0xFFE8F5E9, 0xFFEFEBE9,
            0xFF00F0FF, 0xFF1A73E8, 0xFF2E9E4F, 0xFF8AB4F8, 0xFFBB86FC, 0xFFFF2BD6, 0xFFFF4FD8, 0xFFFF9A3C,
            0xFFFFE600, 0xFFFF3CAC, 0xFF39FF14, 0xFF26C6DA, 0xFFFF7043, 0xFFD81B60, 0xFFD93025, 0xFF6A1B9A};

    /** ردیف‌های رنگ برای یک نقش (0 پس‌زمینه، 1 اصلی، 2 تأکید، 3 متن) */
    private LinearLayout swatchGrid(final int role, final int selected) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < SWATCHES.length / 8; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int i = 0; i < 8; i++) {
                final int color = SWATCHES[r * 8 + i];
                boolean sel = color == selected;
                View sw = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(color);
                g.setStroke(dp(sel ? 3 : 1), sel ? t.primary : Ui.alpha(t.text2, 0x80));
                sw.setBackground(g);
                sw.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppTheme.setRole(MainActivity.this, role, color);
                        recreate();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(sw, lp);
            }
            grid.addView(line);
        }
        return grid;
    }

    private interface Pick {
        void on(int index);
    }

    private Pick savePref(final String key) {
        return new Pick() {
            @Override
            public void on(int i) {
                kpPrefs().edit().putInt(key, i).apply();
            }
        };
    }

    private LinearLayout chipRow(String[] labels, int sel, final Pick pick) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = sel == i;
            TextView chip = Ui.pill(this, labels[i],
                    on ? t.primary : Ui.alpha(t.surface, 0xCC), on ? t.onPrimary : t.text);
            chip.setPadding(dp(4), dp(10), dp(4), dp(10));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.on(idx);
                    recreate();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(chip, cp);
        }
        return row;
    }

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(24));

        // default dialer
        box.addView(header("شماره‌گیر پیش‌فرض"));
        boolean def = isDefaultDialer();
        TextView st = Ui.text(this, def
                ? "این برنامه شماره‌گیر پیش‌فرض است ✓ صفحه تماس ورودی و خروجی با تم شما نمایش داده می‌شود."
                : "برای اینکه صفحه تماس ورودی و خروجی هم تم شما را داشته باشد، این برنامه باید شماره‌گیر پیش‌فرض باشد.",
                14, t.text2);
        box.addView(st);
        if (!def) {
            TextView b = Ui.pill(this, "تنظیم به‌عنوان شماره‌گیر پیش‌فرض", t.primary, t.onPrimary);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestDefaultDialer();
                }
            });
            box.addView(b, fullTop(10));
        }

        boolean missingPerm = false;
        for (String p : PERMS) if (!has(p)) missingPerm = true;
        if (missingPerm) {
            TextView b = Ui.pill(this, "درخواست اجازه‌های لازم", t.surface, t.text);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestMissingPermissions();
                }
            });
            box.addView(b, fullTop(10));
        }

        // background picture
        box.addView(header("تصویر پس‌زمینه"));
        box.addView(Ui.text(this,
                "یک عکس از گالری انتخاب کنید تا پشت همه صفحه‌ها (از جمله صفحه تماس) قرار بگیرد. "
                        + "بدون عکس، طرح نئونی یا گرادیان خود تم نمایش داده می‌شود.",
                14, t.text2));
        TextView pick = Ui.pill(this, "انتخاب تصویر از گالری", t.primary, t.onPrimary);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    startActivityForResult(i, 3);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "برنامه گالری پیدا نشد", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(pick, fullTop(10));
        if (Wallpaper.exists(this)) {
            TextView del = Ui.pill(this, "حذف تصویر پس‌زمینه", t.surface, t.text);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Wallpaper.delete(MainActivity.this);
                    recreate();
                }
            });
            box.addView(del, fullTop(8));
        }

        // custom colors (ready-made themes removed)
        box.addView(header("رنگ‌های شخصی"));
        box.addView(Ui.text(this, "پس‌زمینه", 14, t.text2));
        box.addView(swatchGrid(0, t.bg), fullTop(4));
        box.addView(Ui.text(this, "رنگ اصلی", 14, t.text2));
        box.addView(swatchGrid(1, t.primary), fullTop(4));
        box.addView(Ui.text(this, "رنگ تأکید", 14, t.text2));
        box.addView(swatchGrid(2, t.accent), fullTop(4));
        box.addView(Ui.text(this, "رنگ متن", 14, t.text2));
        box.addView(swatchGrid(3, t.text), fullTop(4));
        TextView reset = Ui.pill(this, "بازگردانی رنگ‌های پیش‌فرض", t.surface, t.text);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppTheme.resetCustom(MainActivity.this);
                recreate();
            }
        });
        box.addView(reset, fullTop(8));

        // text size
        box.addView(header("اندازه متن"));
        LinearLayout sizes = new LinearLayout(this);
        String[] labels = {"کوچک", "متوسط", "بزرگ", "خیلی بزرگ"};
        int curSize = AppTheme.sizeIndex(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean sel = curSize == i;
            TextView chip = Ui.pill(this, labels[i],
                    sel ? t.primary : Ui.alpha(t.surface, 0xCC), sel ? t.onPrimary : t.text);
            chip.setPadding(dp(4), dp(10), dp(4), dp(10));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppTheme.setSize(MainActivity.this, idx);
                    recreate();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            sizes.addView(chip, cp);
        }
        box.addView(sizes, new LinearLayout.LayoutParams(MATCH, WRAP));

        // block duration
        box.addView(header("مدت مسدودی تماس‌گیرنده"));
        box.addView(Ui.text(this,
                "بعد از این مدت، شماره از لیست مسدودها خارج می‌شود و تماس‌هایش دوباره زنگ می‌زنند.",
                14, t.text2));
        final int[] minutesValues = {5, 15, 60, 1440};
        int curMin = InCallActivity.TimedBlock.durationMinutes(this);
        int selMin = 0;
        for (int i = 0; i < minutesValues.length; i++) if (minutesValues[i] == curMin) selMin = i;
        box.addView(chipRow(new String[]{"۵ دقیقه", "۱۵ دقیقه", "۱ ساعت", "۲۴ ساعت"}, selMin,
                new Pick() {
                    @Override
                    public void on(int i) {
                        InCallActivity.TimedBlock.setDurationMinutes(MainActivity.this, minutesValues[i]);
                    }
                }), fullTop(8));

        // incoming ringtone
        box.addView(header("آهنگ زنگ تماس ورودی"));
        final String ringUri = getSharedPreferences("ringer", MODE_PRIVATE).getString("uri", null);
        String ringName = "پیش‌فرض گوشی";
        if (ringUri != null) {
            try {
                ringName = RingtoneManager.getRingtone(this, Uri.parse(ringUri)).getTitle(this);
            } catch (Exception e) {
                ringName = "آهنگ انتخابی";
            }
        }
        box.addView(Ui.text(this, "آهنگ فعلی: " + ringName, 14, t.text2));
        TextView pickRing = Ui.pill(this, "انتخاب آهنگ", t.primary, t.onPrimary);
        pickRing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchRingtonePicker(ringUri);
            }
        });
        box.addView(pickRing, fullTop(6));

        // speed dial note
        box.addView(header("شماره‌گیری سریع"));
        box.addView(Ui.text(this,
                "افزودن و حذف شماره‌های سریع (۱ تا ۱۰۰) در صفحه‌ی شماره‌گیر انجام می‌شود.",
                14, t.text2));

        // blocked numbers: list + unblock
        box.addView(header("شماره‌های مسدود"));
        final java.util.List<String> blocked = activeBlocked();
        if (blocked.isEmpty()) {
            box.addView(Ui.text(this, "در حال حاضر هیچ شماره‌ای مسدود نیست.", 14, t.text2));
        }
        for (final String num : blocked) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(Ui.rect(this, Ui.alpha(t.surface, 0xCC), 14));
            long leftMs = expiryOfBlock(num) - System.currentTimeMillis();
            long leftMin = Math.max(1, (leftMs + 59999L) / 60000L);
            TextView info = Ui.text(this, num + "  (" + leftMin + " دقیقه مانده)", 15, t.text);
            row.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));
            TextView un = Ui.pill(this, "رفع مسدودی", t.primary, t.onPrimary);
            un.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unblockNumber(num);
                    recreate();
                }
            });
            row.addView(un);
            box.addView(row, fullTop(8));
        }
        if (blocked.size() > 1) {
            TextView all = Ui.pill(this, "رفع مسدودی همه", t.surface, t.text);
            all.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unblockAllNumbers();
                    recreate();
                }
            });
            box.addView(all, fullTop(8));
        }

        // keypad appearance
        box.addView(header("صفحه شماره‌گیر"));
        box.addView(Ui.text(this, "شکل کلیدها", 14, t.text2));
        box.addView(chipRow(new String[]{"سه‌بعدی دایره", "تخت دایره", "مربع گرد", "قرصی"},
                kpPrefs().getInt("style", 0), savePref("style")), fullTop(6));
        box.addView(Ui.text(this, "اندازه کلید", 14, t.text2));
        box.addView(chipRow(new String[]{"کوچک", "متوسط", "بزرگ"},
                kpPrefs().getInt("size", 1), savePref("size")), fullTop(6));
        box.addView(Ui.text(this, "پس‌زمینه کلید", 14, t.text2));
        box.addView(chipRow(new String[]{"تیره", "روشن", "رنگ تم", "سطح تم"},
                kpPrefs().getInt("bg", 0), savePref("bg")), fullTop(6));
        box.addView(Ui.text(this, "رنگ‌بندی", 14, t.text2));
        box.addView(chipRow(new String[]{"رنگی", "تک‌رنگ (رنگ تم)"},
                kpPrefs().getInt("color", 0), savePref("color")), fullTop(6));
        box.addView(Ui.text(this, "پالت رنگ", 14, t.text2));
        box.addView(chipRow(new String[]{"پیش‌فرض", "رنگین‌کمان", "پاستلی", "هم‌خانواده تم"},
                kpPrefs().getInt("palette", 0), savePref("palette")), fullTop(6));
        box.addView(Ui.text(this, "صدای کلیدها", 14, t.text2));
        box.addView(chipRow(new String[]{"DTMF", "بیپ", "تأیید", "پیپ", "بی‌صدا"},
                kpPrefs().getInt("sound", 0), savePref("sound")), fullTop(6));
        String keyUri = kpPrefs().getString("keysound", null);
        String keyName = "پیش‌فرض (از حالت بالا)";
        if (keyUri != null) {
            try {
                keyName = RingtoneManager.getRingtone(this, Uri.parse(keyUri)).getTitle(this);
            } catch (Exception e) {
                keyName = "صدای انتخابی";
            }
        }
        box.addView(Ui.text(this, "صدای انتخابی از گوشی: " + keyName, 14, t.text2));
        TextView pickKey = Ui.pill(this, "انتخاب صدا از صداهای گوشی", t.primary, t.onPrimary);
        pickKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchKeySoundPicker();
            }
        });
        box.addView(pickKey, fullTop(6));

        TextView tip = Ui.text(this,
                "برای برگشتن به شماره‌گیر قبلی: تنظیمات اندروید ← برنامه‌ها ← برنامه‌های پیش‌فرض ← تلفن.",
                13, t.text2);
        tip.setPadding(0, dp(24), 0, 0);
        box.addView(tip);

        sv.addView(box, new ScrollView.LayoutParams(MATCH, WRAP));
        return sv;
    }
}
