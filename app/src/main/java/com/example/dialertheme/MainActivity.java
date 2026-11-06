package com.example.dialertheme;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
        super.onResume();
        if (content != null && tab == 3) showTab(3);
    }

    @Override
    protected void onActivityResult(int rc, int result, Intent data) {
        super.onActivityResult(rc, result, data);
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
        TextView avatar, name, number;
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
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.setMarginStart(dp(12));
                row.addView(col, cp);

                h.sms = new IconView(MainActivity.this, IconView.CHAT, t.accent, t.neon);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(26), dp(26));
                ip.setMarginStart(dp(8));
                row.addView(h.sms, ip);

                row.setTag(h);
                cv = row;
            } else {
                h = (ContactHolder) cv.getTag();
            }
            final ContactUtil.Contact c = shown.get(pos);
            h.avatar.setText(c.name.isEmpty() ? "#" : c.name.substring(0, 1).toUpperCase());
            h.name.setText(c.name);
            h.number.setText(c.number);
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

    private void updateDial() {
        if (dialDisplay != null) dialDisplay.setText(dialNumber);
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

    private View keyView(final String k) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.keyBg(this, t));
        TextView d = Ui.text(this, k, 26, t.text);
        d.setGravity(Gravity.CENTER);
        Ui.glowText(d, t.primary, t);
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
        }
        return v;
    }

    private LinearLayout.LayoutParams keyLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(68), dp(68));
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
        Ui.glowText(dialDisplay, t.primary, t);
        box.addView(dialDisplay, new LinearLayout.LayoutParams(MATCH, WRAP));

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] rowKeys : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            for (String k : rowKeys) r.addView(keyView(k), keyLp());
            box.addView(r);
        }

        LinearLayout last = new LinearLayout(this);
        last.setGravity(Gravity.CENTER);
        last.addView(new View(this), keyLp());

        LinearLayout call = new LinearLayout(this);
        call.setGravity(Gravity.CENTER);
        call.setBackground(Ui.glowButton(this, AppTheme.GREEN));
        call.addView(new IconView(this, IconView.PHONE, Color.WHITE, false),
                new LinearLayout.LayoutParams(dp(30), dp(30)));
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                placeCall(dialNumber);
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
        TextView title, sub;
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
        return lv;
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
            String sub = typeLabel + " • " + fmt.format(new Date(r.date));
            if (r.duration > 0) {
                sub += String.format(Locale.US, " • %d:%02d", r.duration / 60, r.duration % 60);
            }
            int ic = missed ? AppTheme.RED : (r.type == 2 ? t.primary : AppTheme.GREEN);
            h.icon.setStyle(ic, t.neon);
            h.title.setText(title);
            h.title.setTextColor(missed ? AppTheme.RED : t.text);
            h.sub.setText(sub);
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

        // themes
        box.addView(header("تم رنگی"));
        int cur = AppTheme.index(this);
        for (int i = 0; i < AppTheme.ALL.length; i++) {
            final int idx = i;
            AppTheme th = AppTheme.ALL[i];
            boolean sel = cur == i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{th.bg, th.bg2});
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(sel ? 3 : 1), sel ? th.primary : Ui.alpha(th.text2, 0x80));
            row.setBackground(bg);

            TextView nm = Ui.text(this, th.name + (th.neon ? "  ✦" : "") + (sel ? "  ✓" : ""), 16, th.text);
            Ui.glowText(nm, th.primary, th);
            row.addView(nm, new LinearLayout.LayoutParams(0, WRAP, 1f));
            int[] sw = {th.primary, th.accent, th.surface};
            for (int col : sw) {
                View d = new View(this);
                d.setBackground(Ui.circle(col));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(22), dp(22));
                dl.setMarginStart(dp(6));
                row.addView(d, dl);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppTheme.setCurrent(MainActivity.this, idx);
                    recreate();
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
            rp.bottomMargin = dp(8);
            box.addView(row, rp);
        }

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

        TextView tip = Ui.text(this,
                "برای برگشتن به شماره‌گیر قبلی: تنظیمات اندروید ← برنامه‌ها ← برنامه‌های پیش‌فرض ← تلفن.",
                13, t.text2);
        tip.setPadding(0, dp(24), 0, 0);
        box.addView(tip);

        sv.addView(box, new ScrollView.LayoutParams(MATCH, WRAP));
        return sv;
    }
}
