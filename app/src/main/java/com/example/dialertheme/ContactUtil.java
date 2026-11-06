package com.example.dialertheme;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactUtil {

    public static class Contact {
        public String name;
        public String number;
    }

    public static List<Contact> loadAll(Context c) {
        List<Contact> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(0);
                String number = cur.getString(1);
                if (TextUtils.isEmpty(number)) continue;
                if (name == null) name = number;
                String key = name + "|" + number.replaceAll("[^0-9+]", "");
                if (!seen.add(key)) continue;
                Contact ct = new Contact();
                ct.name = name;
                ct.number = number;
                list.add(ct);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return list;
    }

    public static String lookupName(Context c, String number) {
        if (TextUtils.isEmpty(number)) return null;
        Cursor cur = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            cur = c.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }
}
