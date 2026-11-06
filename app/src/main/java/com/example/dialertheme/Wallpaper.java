package com.example.dialertheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Custom background picture chosen by the user (stored cropped to screen size). */
public class Wallpaper {

    private static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    public static boolean exists(Context c) {
        return file(c).exists();
    }

    public static void delete(Context c) {
        file(c).delete();
    }

    public static Drawable load(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b == null) return null;
            return new BitmapDrawable(c.getResources(), b);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean save(Context c, Uri uri) {
        try {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int W = dm.widthPixels, H = dm.heightPixels;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();

            int sample = 1;
            while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap src = BitmapFactory.decodeStream(in, null, o2);
            if (in != null) in.close();
            if (src == null) return false;

            float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
            int sw = Math.round(src.getWidth() * scale);
            int sh = Math.round(src.getHeight() * scale);
            int left = (W - sw) / 2, top = (H - sh) / 2;

            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmap(src, null, new Rect(left, top, left + sw, top + sh), paint);

            FileOutputStream fo = new FileOutputStream(file(c));
            out.compress(Bitmap.CompressFormat.JPEG, 88, fo);
            fo.close();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
