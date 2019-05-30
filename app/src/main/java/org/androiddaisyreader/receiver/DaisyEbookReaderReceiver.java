package org.androiddaisyreader.receiver;

import org.androiddaisyreader.service.DaisyEbookReaderService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class DaisyEbookReaderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_MEDIA_SCANNER_STARTED)
                || intent.getAction().equals(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)) {
            Intent serviceIntent = new Intent(context, DaisyEbookReaderService.class);
            if (Build.VERSION.SDK_INT >= 26) { // Build.VERSION_CODES.O
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
