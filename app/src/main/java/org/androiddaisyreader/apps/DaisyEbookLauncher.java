package org.androiddaisyreader.apps;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.SimpleBookContext;
import org.androiddaisyreader.player.IntentController;

import java.io.InputStream;

public class DaisyEbookLauncher extends AppCompatActivity {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String uri = getIntent().getDataString();

        InputStream contents = null;
        DaisyBookInfo bookInfo = null;
        try {
            SimpleBookContext context = new SimpleBookContext(uri);
            bookInfo = context.getBookInfo();
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext(), uri);
            ex.writeLogException();
        }

//        if (bookInfo != null) {
//            bookInfo.setPath(uri);
//            bookInfo.setId(Long.valueOf(id).toString());
//            mSql.addDaisyBook(bookInfo, Constants.TYPE_DOWNLOADED_BOOK);

//            DaisyBookUtil.addRecentBookToSQLite(daisyBook, mNumberOfRecentBooks, mSql);
//        }

        // push to reader activity
        IntentController intentController = new IntentController(
                DaisyEbookLauncher.this);
        intentController.pushToDaisyEbookReaderIntent(uri);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
