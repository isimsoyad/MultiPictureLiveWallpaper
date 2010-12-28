package org.tamanegi.wallpaper.multipicture.picsource;

import org.tamanegi.wallpaper.multipicture.MultiPictureSetting;
import org.tamanegi.wallpaper.multipicture.plugin.LazyPickService;
import org.tamanegi.wallpaper.multipicture.plugin.PictureContentInfo;
import org.tamanegi.wallpaper.multipicture.plugin.ScreenInfo;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.FileObserver;
import android.os.Handler;
import android.preference.PreferenceManager;

public class SinglePickService extends LazyPickService
{
    private static final int RESCAN_DELAY = 5000; // msec

    private Handler handler;

    @Override
    public void onCreate()
    {
        super.onCreate();

        handler = new Handler();
    }

    @Override
    public LazyPicker onCreateLazyPicker()
    {
        return new SingleLazyPicker();
    }

    private class SingleLazyPicker extends LazyPicker
    {
        private Uri uri;

        private PictureObserver observer;
        private Runnable rescan_callback;

        @Override
        protected void onStart(String key, ScreenInfo hint)
        {
            // read preference
            SharedPreferences pref = PreferenceManager.
                getDefaultSharedPreferences(SinglePickService.this);
            String fname = pref.getString(
                MultiPictureSetting.getKey(
                    MultiPictureSetting.SCREEN_FILE_KEY, key), "");
            uri = Uri.parse(fname);

            // picture file observer
            observer = new PictureObserver(this);
            rescan_callback = new Runnable() {
                    @Override
                    public void run() {
                        notifyChanged();
                    }
                };

            observer.start();
        }

        @Override
        protected void onStop()
        {
            observer.stop();
            handler.removeCallbacks(rescan_callback);
        }

        @Override
        public PictureContentInfo getNext()
        {
            int orientation = PictureUtils.getContentOrientation(
                getContentResolver(), uri);
            return new PictureContentInfo(uri, orientation);
        }

        private void postRescanCallback()
        {
            handler.removeCallbacks(rescan_callback);
            handler.postDelayed(rescan_callback, RESCAN_DELAY);
        }
    }

    private class PictureObserver
    {
        private static final int EVENTS =
            FileObserver.DELETE_SELF | FileObserver.MODIFY |
            FileObserver.MOVE_SELF;

        private SingleLazyPicker picker;
        private boolean is_file;

        private FileObserver file_observer;
        private ContentObserver content_observer;

        private PictureObserver(SingleLazyPicker picker)
        {
            this.picker = picker;
        }

        private void start()
        {
            is_file =
                ContentResolver.SCHEME_FILE.equals(picker.uri.getScheme());

            if(is_file) {
                file_observer = new FileObserver(picker.uri.getPath()) {
                        @Override 
                        public void onEvent(int event, String path) {
                            if((event & EVENTS) != 0) {
                                picker.postRescanCallback();
                            }
                        }
                    };
                file_observer.startWatching();
            }
            else {
                content_observer = new ContentObserver(null) {
                        @Override
                        public boolean deliverSelfNotifications ()
                        {
                            return true;
                        }

                        @Override
                        public void onChange(boolean selfChange)
                        {
                            picker.postRescanCallback();
                        }
                    };
                getContentResolver().registerContentObserver(
                    picker.uri, false, content_observer);
            }
        }

        private void stop()
        {
            if(is_file) {
                file_observer.stopWatching();
            }
            else {
                getContentResolver().unregisterContentObserver(
                    content_observer);
            }
        }
    }
}