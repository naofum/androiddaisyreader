package org.androiddaisyreader.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.CurrentInformation;
import java.util.UUID;

public class SQLiteCurrentInformationHelper extends SQLiteHandler {

    private static SQLiteCurrentInformationHelper sInstance;
    private Context mContext;

    public static synchronized SQLiteCurrentInformationHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SQLiteCurrentInformationHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * @deprecated getInstance(Context)を使用してください。
     */
    @Deprecated
    public SQLiteCurrentInformationHelper(Context context) {
        super(context);
        this.mContext = context;
    }

    /**
     * Add a record of current information table
     * 
     * @param current
     */
    public void addCurrentInformation(CurrentInformation current) {

        ContentValues mValue = new ContentValues();
        mValue.put(ID_KEY_CURRENT_INFORMATION, UUID.randomUUID().toString());
        putValue(mValue, current);
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.insert(TABLE_NAME_CURRENT_INFORMATION, null, mValue);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }

    }

    /**
     * Delete a of current information table
     * 
     * @param id
     */
    public void deleteCurrentInformation(String id) {
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.delete(TABLE_NAME_CURRENT_INFORMATION, ID_KEY_CURRENT_INFORMATION + "=?",
                    new String[] { id });
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }

    }

    /**
     * Update current information to sqlite
     * 
     * @param current
     */
    public void updateCurrentInformation(CurrentInformation current) {
        ContentValues mValue = new ContentValues();
        try {
            putValue(mValue, current);
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.update(TABLE_NAME_CURRENT_INFORMATION, mValue, ID_KEY_CURRENT_INFORMATION + "=?",
                    new String[] { current.getId() });
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
    }

    private void putValue(ContentValues value, CurrentInformation currentInfomation) {
        value.put(AUDIO_NAME_KEY_CURRENT_INFORMATION, currentInfomation.getAudioName());
        value.put(PATH_KEY_CURRENT_INFORMATION, currentInfomation.getPath());
        value.put(TIME_KEY_CURRENT_INFORMATION, currentInfomation.getTime());
        value.put(SECTION_KEY_CURRENT_INFORMATION, currentInfomation.getSection());
        value.put(PLAYING_KEY_CURRENT_INFORMATION, currentInfomation.getPlaying());
        value.put(SENTENCE_KEY_CURRENT_INFORMATION, currentInfomation.getSentence());
        value.put(ACTIVITY_KEY_CURRENT_INFORMATION, currentInfomation.getActivity());
        value.put(FIRST_NEXT_KEY_CURRENT_INFORMATION, currentInfomation.getFirstNext());
        value.put(FIRST_PREVIOUS_KEY_CURRENT_INFORMATION, currentInfomation.getFirstPrevious());
        value.put(AT_THE_END_KEY_CURRENT_INFORMATION, currentInfomation.getAtTheEnd());
    }

    /**
     * Get current information from sqlite
     * 
     * @return
     */
    public CurrentInformation getCurrentInformation() {
        String valueOfTrue = "1";
        CurrentInformation current = null;
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_CURRENT_INFORMATION, new String[] {
                    AUDIO_NAME_KEY_CURRENT_INFORMATION, PATH_KEY_CURRENT_INFORMATION,
                    SECTION_KEY_CURRENT_INFORMATION, TIME_KEY_CURRENT_INFORMATION,
                    PLAYING_KEY_CURRENT_INFORMATION, SENTENCE_KEY_CURRENT_INFORMATION,
                    ACTIVITY_KEY_CURRENT_INFORMATION, ID_KEY_CURRENT_INFORMATION,
                    FIRST_NEXT_KEY_CURRENT_INFORMATION, FIRST_PREVIOUS_KEY_CURRENT_INFORMATION,
                    AT_THE_END_KEY_CURRENT_INFORMATION }, null, null, null, null, null);
            if (mCursor != null && mCursor.moveToFirst()) {
                int idxAudioName = mCursor.getColumnIndex(AUDIO_NAME_KEY_CURRENT_INFORMATION);
                int idxPath = mCursor.getColumnIndex(PATH_KEY_CURRENT_INFORMATION);
                int idxSection = mCursor.getColumnIndex(SECTION_KEY_CURRENT_INFORMATION);
                int idxTime = mCursor.getColumnIndex(TIME_KEY_CURRENT_INFORMATION);
                int idxPlaying = mCursor.getColumnIndex(PLAYING_KEY_CURRENT_INFORMATION);
                int idxSentence = mCursor.getColumnIndex(SENTENCE_KEY_CURRENT_INFORMATION);
                int idxActivity = mCursor.getColumnIndex(ACTIVITY_KEY_CURRENT_INFORMATION);
                int idxId = mCursor.getColumnIndex(ID_KEY_CURRENT_INFORMATION);
                int idxFirstNext = mCursor.getColumnIndex(FIRST_NEXT_KEY_CURRENT_INFORMATION);
                int idxFirstPrevious = mCursor.getColumnIndex(FIRST_PREVIOUS_KEY_CURRENT_INFORMATION);
                int idxAtTheEnd = mCursor.getColumnIndex(AT_THE_END_KEY_CURRENT_INFORMATION);

                String audioName = mCursor.getString(idxAudioName);
                String path = mCursor.getString(idxPath);
                int section = Integer.valueOf(mCursor.getString(idxSection));
                int time = Integer.valueOf(mCursor.getString(idxTime));
                boolean playing = mCursor.getString(idxPlaying).contains(valueOfTrue);
                int sentence = Integer.valueOf(mCursor.getString(idxSentence));
                String activity = mCursor.getString(idxActivity);
                String id = mCursor.getString(idxId);
                boolean firstNext = mCursor.getString(idxFirstNext).contains(valueOfTrue);
                boolean firstPrevious = mCursor.getString(idxFirstPrevious).contains(valueOfTrue);
                boolean atTheEnd = mCursor.getString(idxAtTheEnd).contains(valueOfTrue);
                current = new CurrentInformation(audioName, path, section, time, playing, sentence,
                        activity, id, firstNext, firstPrevious, atTheEnd);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return current;
    }
}
