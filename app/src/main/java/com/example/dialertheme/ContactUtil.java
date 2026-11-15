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
        public boolean whatsapp;
        public boolean telegram;
    }

    private static final String MIME_WHATSAPP = "vnd.android.cursor.item/vnd.com.whatsapp.profile";
    private static final String MIME_TELEGRAM = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile";

    /** شناسه‌ی مخاطبینی که ردیف این برنامه‌ی پیام‌رسان را دارند. */
    private static Set<String> appContactIds(Context c, String mime) {
        Set<String> ids = new HashSet<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.CONTACT_ID},
                    ContactsContract.Data.MIMETYPE + "=?", new String[]{mime}, null);
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0);
                if (id != null) ids.add(id);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return ids;
    }

    public static List<Contact> loadAll(Context c) {
        List<Contact> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> waIds = appContactIds(c, MIME_WHATSAPP);
        Set<String> tgIds = appContactIds(c, MIME_TELEGRAM);
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID},
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(0);
                String number = cur.getString(1);
                String contactId = cur.getString(2);
                if (TextUtils.isEmpty(number)) continue;
                if (name == null) name = number;
                String key = name + "|" + number.replaceAll("[^0-9+]", "");
                if (!seen.add(key)) continue;
                Contact ct = new Contact();
                ct.name = name;
                ct.number = number;
                ct.whatsapp = contactId != null && waIds.contains(contactId);
                ct.telegram = contactId != null && tgIds.contains(contactId);
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
