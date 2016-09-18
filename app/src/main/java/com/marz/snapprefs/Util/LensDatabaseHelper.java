package com.marz.snapprefs.Util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.marz.snapprefs.Lens;
import com.marz.snapprefs.Lens.LensEntry;
import com.marz.snapprefs.Logger;
import com.marz.snapprefs.Preferences;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by Andre on 16/09/2016.
 */
public class LensDatabaseHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = Preferences.mSavePath + "/Lenses.db";
    public static final String[] fullProjection = {
            LensEntry.COLUMN_NAME_MCODE,
            LensEntry.COLUMN_NAME_GPLAYID,
            LensEntry.COLUMN_NAME_MHINTID,
            LensEntry.COLUMN_NAME_MICONLINK,
            LensEntry.COLUMN_NAME_MID,
            LensEntry.COLUMN_NAME_MLENSLINK,
            LensEntry.COLUMN_NAME_MSIGNATURE,
    };
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + Lens.LensEntry.TABLE_NAME;
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + LensEntry.TABLE_NAME + " (" +
                    LensEntry.COLUMN_NAME_MCODE + TEXT_TYPE + " PRIMARY KEY," +
                    LensEntry.COLUMN_NAME_GPLAYID + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_MHINTID + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_MICONLINK + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_MID + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_MLENSLINK + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_MSIGNATURE + TEXT_TYPE + COMMA_SEP +
                    LensEntry.COLUMN_NAME_ACTIVE + " INTEGER DEFAULT 0 )";

    private boolean requiresUpdate = true;
    private ArrayList<LensData> lensCache = new ArrayList<>();
    private ArrayList<LensData> excludedLensCache = new ArrayList<>();

    public LensDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Logger.log("Database name: " + DATABASE_NAME);
    }

    public static String formatExclusionList(ArrayList<String> list) {
        StringBuilder builder = new StringBuilder();

        Iterator<String> iterator = list.iterator();

        while (iterator.hasNext()) {
            String str = iterator.next();
            builder.append("'");
            builder.append(str);
            builder.append("'");

            if (iterator.hasNext())
                builder.append(",");
        }
        return builder.toString();
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + LensEntry.TABLE_NAME +
                    " ADD COLUMN " + LensEntry.COLUMN_NAME_ACTIVE + " INTEGER DEFAULT 0");
        }
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public long getRowCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        long count = DatabaseUtils.queryNumEntries(db, LensEntry.TABLE_NAME);
        db.close();
        return count;
    }

    public void insertLens(LensData lensData) {
        Logger.log("Inserting new lens: " + lensData.mCode);

        SQLiteDatabase db = this.getWritableDatabase();

        long newRowId = db.insert(LensEntry.TABLE_NAME, null, lensData.getContent());
        Logger.log("New Lens Row ID: " + newRowId);
        requiresUpdate = true;
    }

    public boolean containsLens(String mCode) {
        Logger.log("Getting lens from database");

        SQLiteDatabase db = this.getReadableDatabase();

        String selection = LensEntry.COLUMN_NAME_MCODE + " = ?";
        String[] selectionArgs = {mCode};
        String sortOrder =
                LensEntry.COLUMN_NAME_MCODE + " DESC";

        Logger.log("Performing query: " + selection + mCode);

        Cursor cursor = db.query(
                LensEntry.TABLE_NAME,                     // The table to query
                fullProjection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );

        Logger.log("Query count: " + cursor.getCount());

        if (cursor.getCount() == 0) {
            cursor.close();
            return false;
        }

        cursor.close();
        return true;
    }

    public LensData getLens(String mCode) {
        Logger.log("Getting lens from database");

        SQLiteDatabase db = this.getReadableDatabase();

        String selection = LensEntry.COLUMN_NAME_MCODE + " = ?";
        String[] selectionArgs = {mCode};
        String sortOrder =
                LensEntry.COLUMN_NAME_MCODE + " DESC";

        Logger.log("Performing query: " + selection + mCode);

        Cursor cursor = db.query(
                LensEntry.TABLE_NAME,                     // The table to query
                fullProjection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );

        Logger.log("Query count: " + cursor.getCount());

        if (cursor.getCount() == 0)
            return null;

        cursor.moveToFirst();

        LensData lensData = getLensFromCursor(cursor);

        if( lensData == null )
            return null;

        cursor.close();
        Logger.log("Queried database to get lens: " + lensData.mCode);
        return lensData;
    }

    public ArrayList<LensData> getAllExcept(ArrayList<String> blacklist) {
        Logger.log("Getting all lenses from database");

        if( !requiresUpdate) {
            Logger.log("Using lens cache");
            return excludedLensCache;
        }

        String strBlacklist = formatExclusionList(blacklist);
        String query = "select * from " + LensEntry.TABLE_NAME +
                " where " + LensEntry.COLUMN_NAME_MCODE + " not in (" + strBlacklist + ")";

        Logger.log("Performing query: " + query);

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(query, null);

        Logger.log("Query size: " + cursor.getCount());
        ArrayList<LensData> lensDataList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                Logger.log("Looping cursor result");
                LensData lensData = getLensFromCursor(cursor);

                if( lensData == null )
                    continue;

                lensDataList.add(lensData);
                cursor.moveToNext();
            }

            Logger.log("Completed getting lenses");
        }

        cursor.close();

        excludedLensCache = new ArrayList<>(lensDataList);
        requiresUpdate = false;
        return lensDataList;
    }

    public ArrayList<LensData> getAllLenses() {
        if (!requiresUpdate) {
            Logger.log("Using lens cache");
            return lensCache;
        }

        Logger.log("Getting all lenses from database");
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("select * from " + LensEntry.TABLE_NAME, null);

        Logger.log("Query size: " + cursor.getCount());
        ArrayList<LensData> lensList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                Logger.log("Looping cursor result");
                LensData lensData = getLensFromCursor(cursor);

                if( lensData == null )
                    continue;

                lensList.add(lensData);
                cursor.moveToNext();
            }
            db.close();
            Logger.log("Completed getting lenses");
        }

        cursor.close();

        lensCache = new ArrayList<>(lensList);
        requiresUpdate = false;
        return lensList;
    }

    public void deleteLens(String mCode) {
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = LensEntry.COLUMN_NAME_MCODE + " LIKE ?";
        String[] selectionArgs = {mCode};

        int rowsDeleted = db.delete(LensEntry.TABLE_NAME, selection, selectionArgs);

        if (rowsDeleted > 0)
            requiresUpdate = true;
    }

    public void replaceLens(LensData lensData) {
        deleteLens(lensData.mCode);
        insertLens(lensData);
    }

    public void updateLens(String strTitle, String strValue) {
        SQLiteDatabase db = this.getReadableDatabase();

// New value for one column
        ContentValues values = new ContentValues();
        values.put(strTitle, strValue);

// Which row to update, based on the title
        String selection = strTitle + " = ?";
        String[] selectionArgs = {strValue};

        int rowsUpdated = db.update(
                LensEntry.TABLE_NAME,
                values,
                selection,
                selectionArgs);

        if (rowsUpdated > 0)
            requiresUpdate = true;
    }

    public LensData getLensFromCursor(Cursor cursor) {
        LensData lensData = new LensData();

        try {
            lensData.mCode = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MCODE));
            lensData.mGplayIapId = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_GPLAYID));
            lensData.mHintId = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MHINTID));
            lensData.mIconLink = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MICONLINK));
            lensData.mId = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MID));
            lensData.mLensLink = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MLENSLINK));
            lensData.mSignature = cursor.getString(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_MSIGNATURE));
            short activeState = cursor.getShort(cursor.getColumnIndexOrThrow(LensEntry.COLUMN_NAME_ACTIVE));
            lensData.mActive = activeState != 0;

            Logger.log("Queried database for lens: " + lensData.mCode + " Active: " + lensData.mActive);
        } catch (IllegalArgumentException e) {
            Logger.log("Issue querying database", e);
            return null;
        }

        return lensData;
    }
}
