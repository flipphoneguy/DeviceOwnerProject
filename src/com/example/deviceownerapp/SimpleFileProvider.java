package com.example.deviceownerapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A simple FileProvider-like implementation to serve files from cache.
 * Avoids dependency on AndroidX/Support libraries.
 */
public class SimpleFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // Assume path corresponds to file in external cache dir
        File cacheDir = getContext().getExternalCacheDir();
        File file = new File(cacheDir, uri.getLastPathSegment());

        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        throw new FileNotFoundException(uri.getPath());
    }

    @Override
    public String getType(Uri uri) {
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
    }

    // Unused methods
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File cacheDir = getContext().getExternalCacheDir();
        File file = new File(cacheDir, uri.getLastPathSegment());

        if (projection == null) {
            projection = new String[] {
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
            };
        }

        android.database.MatrixCursor cursor = new android.database.MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            String col = projection[i];
            if (android.provider.OpenableColumns.DISPLAY_NAME.equals(col)) {
                row[i] = file.getName();
            } else if (android.provider.OpenableColumns.SIZE.equals(col)) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
