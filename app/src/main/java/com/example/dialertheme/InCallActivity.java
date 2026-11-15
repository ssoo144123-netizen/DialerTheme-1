diff --git a/app/src/main/java/com/example/dialertheme/InCallActivity.java b/app/src/main/java/com/example/dialertheme/InCallActivity.java
index 70481f1..05148bd 100644
--- a/app/src/main/java/com/example/dialertheme/InCallActivity.java
+++ b/app/src/main/java/com/example/dialertheme/InCallActivity.java
@@ -3,11 +3,13 @@ package com.example.dialertheme;
 import android.animation.ObjectAnimator;
 import android.animation.PropertyValuesHolder;
 import android.animation.ValueAnimator;
+import android.content.ContentValues;
 import android.app.Activity;
 import android.graphics.Color;
 import android.graphics.Typeface;
 import android.graphics.drawable.Drawable;
 import android.graphics.drawable.GradientDrawable;
+import android.provider.BlockedNumberContract;
 import android.os.Build;
 import android.os.Bundle;
 import android.os.Handler;
@@ -24,6 +26,7 @@ import android.view.animation.DecelerateInterpolator;
 import android.widget.FrameLayout;
 import android.widget.LinearLayout;
 import android.widget.TextView;
+import android.widget.Toast;
 
 import java.util.Locale;
 
@@ -297,6 +300,30 @@ public class InCallActivity extends Activity {
         return circleButton(icon, label, bg, ic, !active && t.neon, l);
     }
 
+    private void blockAndReject(Call c) {
+        String number = CallManager.number(c);
+        if (TextUtils.isEmpty(number)) {
+            Toast.makeText(this, "شماره ناشناس قابل مسدودسازی نیست", Toast.LENGTH_SHORT).show();
+            c.reject(false, null);
+            return;
+        }
+        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
+                && BlockedNumberContract.canCurrentUserBlockNumbers(this)) {
+            try {
+                ContentValues values = new ContentValues();
+                values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
+                getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values);
+                Toast.makeText(this, "شماره مسدود شد", Toast.LENGTH_SHORT).show();
+            } catch (Exception e) {
+                Toast.makeText(this, "مسدودسازی ممکن نشد", Toast.LENGTH_SHORT).show();
+            }
+        } else {
+            Toast.makeText(this, "مسدودسازی در این نسخه اندروید پشتیبانی نمی‌شود",
+                    Toast.LENGTH_SHORT).show();
+        }
+        c.reject(false, null);
+    }
+
     private void buildControls(final Call c) {
         controls.removeAllViews();
         int s = c.getState();
@@ -317,6 +344,13 @@ public class InCallActivity extends Activity {
                             c.answer(VideoProfile.STATE_AUDIO_ONLY);
                         }
                     }), weight());
+            r.addView(circleButton(IconView.END, "مسدود", Ui.keyBg(this, t),
+                    t.text, false, new View.OnClickListener() {
+                        @Override
+                        public void onClick(View v) {
+                            blockAndReject(c);
+                        }
+                    }), weight());
             controls.addView(r);
             return;
         }
