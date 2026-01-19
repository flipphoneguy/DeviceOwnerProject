package androidx.core.os;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;

import java.lang.reflect.Method;

/**
 * Shim class to provide AndroidX BundleCompat functionality.
 * The Dhizuku API uses this class internally.
 */
public final class BundleCompat {

    private BundleCompat() {}

    /**
     * Get an IBinder from a Bundle.
     * On API 18+, this directly uses Bundle.getBinder().
     */
    public static IBinder getBinder(Bundle bundle, String key) {
        if (bundle == null) {
            return null;
        }
        return bundle.getBinder(key);
    }

    /**
     * Put an IBinder into a Bundle.
     * On API 18+, this directly uses Bundle.putBinder().
     */
    public static void putBinder(Bundle bundle, String key, IBinder binder) {
        if (bundle != null) {
            bundle.putBinder(key, binder);
        }
    }

    /**
     * Type-safe getParcelable for API 33+, falls back to deprecated version.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T> T getParcelable(Bundle bundle, String key, Class<T> clazz) {
        if (bundle == null) {
            return null;
        }
        // On API 33+, use reflection to call the type-safe version; on older APIs, use the deprecated method
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                Method method = Bundle.class.getMethod("getParcelable", String.class, Class.class);
                return (T) method.invoke(bundle, key, clazz);
            } catch (Exception e) {
                // Fall back to deprecated method
            }
        }
        return (T) bundle.getParcelable(key);
    }

    /**
     * Type-safe getParcelableArrayList for API 33+, falls back to deprecated version.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T> java.util.ArrayList<T> getParcelableArrayList(Bundle bundle, String key, Class<? extends T> clazz) {
        if (bundle == null) {
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                Method method = Bundle.class.getMethod("getParcelableArrayList", String.class, Class.class);
                return (java.util.ArrayList<T>) method.invoke(bundle, key, clazz);
            } catch (Exception e) {
                // Fall back to deprecated method
            }
        }
        return (java.util.ArrayList<T>) bundle.getParcelableArrayList(key);
    }

    /**
     * Type-safe getSparseParcelableArray for API 33+, falls back to deprecated version.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T> android.util.SparseArray<T> getSparseParcelableArray(Bundle bundle, String key, Class<? extends T> clazz) {
        if (bundle == null) {
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                Method method = Bundle.class.getMethod("getSparseParcelableArray", String.class, Class.class);
                return (android.util.SparseArray<T>) method.invoke(bundle, key, clazz);
            } catch (Exception e) {
                // Fall back to deprecated method
            }
        }
        return (android.util.SparseArray<T>) bundle.getSparseParcelableArray(key);
    }
}
