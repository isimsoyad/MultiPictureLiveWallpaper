package org.tamanegi.wallpaper.multipicture;

import java.lang.reflect.Method;
import java.util.IllegalFormatException;

import org.tamanegi.wallpaper.multipicture.picsource.AlbumSource;
import org.tamanegi.wallpaper.multipicture.picsource.FolderSource;
import org.tamanegi.wallpaper.multipicture.picsource.PictureUtils;
import org.tamanegi.wallpaper.multipicture.picsource.SingleSource;
import org.tamanegi.wallpaper.multipicture.plugin.PictureSourceContract;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class MultiPictureSetting extends PreferenceActivity
{
    public static final String SCREEN_DEFAULT = "default";
    public static final String SCREEN_KEYGUARD = "keyguard";

    public static final String SCREEN_ENABLE_KEY = "screen.%s.enable";
    public static final String SCREEN_PICSOURCE_KEY = "screen.%s.picsource";
    public static final String SCREEN_PICSOURCE_DESC_KEY =
        "screen.%s.picsource.desc";
    public static final String SCREEN_PICSOURCE_SERVICE_KEY =
        "screen.%s.picsource.service";
    public static final String SCREEN_PICSOURCE_SERIAL_KEY =
        "screen.picsource.serial";
    public static final String SCREEN_BGCOLOR_KEY = "screen.%s.bgcolor";
    public static final String SCREEN_BGCOLOR_CUSTOM_KEY =
        "screen.%s.bgcolor.custom";
    public static final String SCREEN_CLIP_KEY = "screen.%s.clipratio";
    public static final String SCREEN_SATURATION_KEY = "screen.%s.saturation";
    public static final String SCREEN_OPACITY_KEY = "screen.%s.opacity";
    public static final String SCREEN_RECURSIVE_KEY = "screen.%s.recursive";
    public static final String SCREEN_ORDER_KEY = "screen.%s.order";
    public static final String SCREEN_RESCAN_KEY = "screen.%s.rescan";
    public static final String SCREEN_MEDIAPROVIDER_KEY =
        "screen.%s.mediaprovider";

    public static final String SCREEN_TYPE_KEY = "screen.%s.type";
    public static final String SCREEN_FILE_KEY = "screen.%s.file";
    public static final String SCREEN_FOLDER_KEY = "screen.%s.folder";
    public static final String SCREEN_BUCKET_KEY = "screen.%s.bucket";

    private static final String DEFAULT_CLIP_KEY = "draw.clipratio";
    private static final String DEFALUT_RECURSIVE_KEY = "folder.recursive";
    private static final String DEFAULT_ORDER_KEY = "folder.order";

    private static final int REQUEST_CODE_PICSOURCE = 1;

    private static final int MIN_MEMORY_CLASS = 16;
    private static final int MAX_MEMORY_CLASS = 24;
    private static final int MAX_LARGE_MEMORY_CLASS = 48;

    private static final String TAG = "MultiPictureSetting";

    private SharedPreferences pref;
    private PreferenceGroup pref_group;
    private ContentResolver resolver;
    private Handler handler;

    private ScreenPickerPreference screen_picker;
    private int pickable_screen = 0;
    private int all_screen_mask = 0;

    private PictureSourcePreference cur_item = null;
    private ComponentName cur_comp = null;
    private String cur_key = null;
    private boolean cur_is_default = false;

    public static String getIndexString(int idx)
    {
        return (idx >= 0 ? String.valueOf(idx) : SCREEN_DEFAULT);
    }

    public static String getKey(String base, int idx)
    {
        return getKey(base, getIndexString(idx));
    }

    public static String getKey(String base, String key)
    {
        if(SCREEN_DEFAULT.equals(key)) {
            if(SCREEN_CLIP_KEY.equals(base)) {
                return DEFAULT_CLIP_KEY;
            }
            if(SCREEN_RECURSIVE_KEY.equals(base)) {
                return DEFALUT_RECURSIVE_KEY;
            }
            if(SCREEN_ORDER_KEY.equals(base)) {
                return DEFAULT_ORDER_KEY;
            }
        }
        return String.format(base, key);
    }

    public static int getAutoMemoryClass(Context context)
    {
        ActivityManager amgr = (ActivityManager)
            context.getSystemService(Context.ACTIVITY_SERVICE);
        int mclass = amgr.getMemoryClass();
        int large_mclass = getLargeMemoryClass(amgr);

        if(large_mclass > 0) {
            return Math.min(Math.max(large_mclass, MIN_MEMORY_CLASS),
                            MAX_LARGE_MEMORY_CLASS);
        }
        else {
            return Math.min(Math.max(mclass, MIN_MEMORY_CLASS),
                            MAX_MEMORY_CLASS);
        }
    }

    private static int getLargeMemoryClass(ActivityManager amgr)
    {
        try {
            Method m = ActivityManager.class.getMethod(
                "getLargeMemoryClass", (Class[])null);
            return (Integer)m.invoke(amgr);
        }
        catch(Exception e) {
            // ignore
            return -1;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        resolver = getContentResolver();
        handler = new Handler();

        if(! "org.tamanegi.wallpaper.multipicture".equals(getPackageName())) {
            getPreferenceScreen().removePreference(
                    getPreferenceManager().findPreference("other.cat"));
        }
        else {
            getPreferenceManager().findPreference("other.dnt")
                .setOnPreferenceClickListener(new OnDntClickListener());
        }

        // setup screen-N setting item
        pref_group = (PreferenceGroup)
            getPreferenceManager().findPreference("screen.cat");

        for(int i = 0; i < ScreenPickerPreference.SCREEN_COUNT; i++) {
            addScreenPreferences(i, true);
            all_screen_mask |= (1 << i);
        }

        screen_picker = (ScreenPickerPreference)
            getPreferenceManager().findPreference("screen.picker");
        screen_picker.setOnPreferenceClickListener(
            new ScreenPickerClickListener());
        screen_picker.setScreenPickedListener(new ScreenPickerListener());
        if(pickable_screen == all_screen_mask) {
            screen_picker.setEnabled(false);
        }

        // setup default type item
        PictureSourcePreference def_picsource = (PictureSourcePreference)
            getPreferenceManager().findPreference(
                getKey(SCREEN_PICSOURCE_KEY, -1));
        convertPictureSourcePreference(def_picsource, -1);
        def_picsource.setOnPreferenceChangeListener(
            new OnPictureSourceChangeListener(-1));
        updatePictureSourceSummary(def_picsource, -1);

        ListPreference def_color = (ListPreference)
            getPreferenceManager().findPreference(
                getKey(SCREEN_BGCOLOR_KEY, -1));
        def_color.setOnPreferenceChangeListener(
            new OnColorChangeListener(-1));
        updateColorSummary(def_color, null);

        // setup keyguard screen item
        PictureSourcePreference keyguard_picsource = (PictureSourcePreference)
            getPreferenceManager().findPreference(
                getKey(SCREEN_PICSOURCE_KEY, SCREEN_KEYGUARD));
        keyguard_picsource.setShowDefault(true);
        keyguard_picsource.setOnPreferenceChangeListener(
            new OnPictureSourceChangeListener(SCREEN_KEYGUARD, false));
        updatePictureSourceSummary(keyguard_picsource, SCREEN_KEYGUARD, false);

        ListPreference keyguard_color = (ListPreference)
            getPreferenceManager().findPreference(
                getKey(SCREEN_BGCOLOR_KEY, SCREEN_KEYGUARD));
        keyguard_color.setOnPreferenceChangeListener(
            new OnColorChangeListener(SCREEN_KEYGUARD));
        updateColorSummary(keyguard_color, null);

        // setup maximum memory usage item
        getPreferenceManager().findPreference("memory.max").
            setOnPreferenceChangeListener(new OnMaximumMemoryChangeListener());

        // backward compatibility
        String duration_min_str = pref.getString("folder.duration", null);
        String duration_sec_str = pref.getString("folder.duration_sec", null);
        if(duration_sec_str == null) {
            int duration_sec = (duration_min_str == null ? 60 * 60 :
                                Integer.parseInt(duration_min_str) * 60);
            ListPreference duration = (ListPreference)
                getPreferenceManager().findPreference("folder.duration_sec");
            duration.setValue(Integer.toString(duration_sec));
        }

        boolean workaround_sense_val =
            pref.getBoolean("workaround.htcsense", true);
        String workaround_launcher_str =
            pref.getString("workaround.launcher", null);
        if(workaround_launcher_str == null) {
            ListPreference workaround_launcher = (ListPreference)
                getPreferenceManager().findPreference("workaround.launcher");
            workaround_launcher.setValue(
                (pref.contains("workaround.htcsense") ?
                 (workaround_sense_val ? "htc_sense" : "none") :
                 getString(R.string.workaround_default)));
        }

        // for summary
        setupValueSummary(getKey(SCREEN_CLIP_KEY, -1),
                          R.string.pref_screen_clipratio_summary);
        setupValueSummary(getKey(SCREEN_SATURATION_KEY, -1),
                          R.string.pref_screen_saturation_summary);
        setupValueSummary(getKey(SCREEN_OPACITY_KEY, -1),
                          R.string.pref_screen_opacity_summary);

        setupValueSummary(getKey(SCREEN_CLIP_KEY, SCREEN_KEYGUARD),
                          R.string.pref_screen_clipratio_summary);
        setupValueSummary(getKey(SCREEN_SATURATION_KEY, SCREEN_KEYGUARD),
                          R.string.pref_screen_saturation_summary);
        setupValueSummary(getKey(SCREEN_OPACITY_KEY, SCREEN_KEYGUARD),
                          R.string.pref_screen_opacity_summary);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.setting_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if(item.getItemId() == R.id.menu_share) {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT,
                                getString(R.string.share_text));
                startActivity(Intent.createChooser(
                                  intent, getString(R.string.menu_share)));
            }
            catch(Exception e) {
                e.printStackTrace();
                // ignore
            }
            return true;
        }
        else if(item.getItemId() == R.id.menu_qr) {
            startActivity(
                new Intent(getApplicationContext(), QRViewer.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data)
    {
        if(cur_item == null || resultCode == RESULT_CANCELED || data == null) {
            Log.d(TAG, "picture source: canceled: " + resultCode + ", " + data);
            return;
        }

        String desc = data.getStringExtra(
            PictureSourceContract.EXTRA_DESCRIPTION);
        if(desc == null) {
            desc = "";
        }

        Parcelable serv = data.getParcelableExtra(
            PictureSourceContract.EXTRA_SERVICE_NAME);
        if(serv == null || ! (serv instanceof ComponentName)) {
            Log.d(TAG, "picture source: no serviceName: " + serv);
            return;
        }
        String service_val = ((ComponentName)serv).flattenToString();

        cur_item.setValue(cur_comp);
        persistPictureSourceInfo(cur_key, desc, service_val);

        updatePictureSourceSummary(cur_item, cur_key, cur_is_default);

        cur_item = null;
        cur_key = null;

        Log.d(TAG, "picture source: apply: " + serv);
    }

    private void addScreenPreferences(int idx, boolean check_default)
    {
        String picsource_key = getKey(SCREEN_PICSOURCE_KEY, idx);
        String type_key = getKey(SCREEN_TYPE_KEY, idx);
        String fname_key = getKey(SCREEN_FILE_KEY, idx);
        String bgcolor_key = getKey(SCREEN_BGCOLOR_KEY, idx);
        String clip_key = getKey(SCREEN_CLIP_KEY, idx);
        String satu_key = getKey(SCREEN_SATURATION_KEY, idx);
        String opac_key = getKey(SCREEN_OPACITY_KEY, idx);

        if(check_default) {
            String picsource_str = pref.getString(picsource_key, null);
            String type_str = pref.getString(type_key, null);
            String fname = pref.getString(fname_key, null);
            String bgcolor = pref.getString(bgcolor_key, "use_default");
            String clip = pref.getString(clip_key, "use_default");
            String satu = pref.getString(satu_key, "use_default");
            String opac = pref.getString(opac_key, "use_default");

            if(((picsource_str == null &&
                 ((type_str == null && fname == null) ||
                  ("use_default".equals(type_str)))) ||
                "".equals(picsource_str)) &&
               "use_default".equals(bgcolor) &&
               "use_default".equals(clip) &&
               "use_default".equals(satu) &&
               "use_default".equals(opac)) {
                return;
            }
        }

        // group for each screen
        PreferenceScreen sgroup =
            getPreferenceManager().createPreferenceScreen(this);
        sgroup.setTitle(
            getString(R.string.pref_cat_picture_screen_title, idx + 1));
        sgroup.setSummary(
            getString((idx == 0 ?
                       R.string.pref_cat_picture_screen_left_summary :
                       R.string.pref_cat_picture_screen_summary), idx + 1));
        sgroup.setOrder(idx + 1);
        pref_group.addPreference(sgroup);

        // screen type
        PictureSourcePreference picsource = new PictureSourcePreference(this);
        picsource.setKey(picsource_key);
        picsource.setTitle(R.string.pref_screen_type_title);
        picsource.setDialogTitle(R.string.pref_screen_type_title);
        picsource.setSummary(R.string.pref_screen_type_base_summary);
        picsource.setShowDefault(true);
        picsource.setOnPreferenceChangeListener(
            new OnPictureSourceChangeListener(idx));
        sgroup.addPreference(picsource);

        convertPictureSourcePreference(picsource, idx);
        updatePictureSourceSummary(picsource, idx);

        // background color
        ListPreference color = new ListPreference(this);
        color.setKey(bgcolor_key);
        color.setTitle(R.string.pref_screen_bgcolor_title);
        color.setDialogTitle(color.getTitle());
        color.setSummary(R.string.pref_screen_bgcolor_summary);
        color.setEntries(R.array.pref_screen_bgcolor_entries);
        color.setEntryValues(R.array.pref_screen_bgcolor_entryvalues);
        color.setDefaultValue("use_default");
        color.setOnPreferenceChangeListener(
            new OnColorChangeListener(idx));
        sgroup.addPreference(color);

        updateColorSummary(color, null);

        // clip ratio
        ListPreference clip = new ListPreference(this);
        clip.setKey(clip_key);
        clip.setTitle(R.string.pref_screen_clipratio_title);
        clip.setDialogTitle(clip.getTitle());
        clip.setSummary(R.string.pref_screen_clipratio_summary);
        clip.setEntries(R.array.pref_screen_clipratio_entries);
        clip.setEntryValues(R.array.pref_screen_clipratio_entryvalues);
        clip.setDefaultValue("use_default");
        sgroup.addPreference(clip);
        setupValueSummary(clip_key, R.string.pref_screen_clipratio_summary);

        // saturation
        ListPreference satu = new ListPreference(this);
        satu.setKey(satu_key);
        satu.setTitle(R.string.pref_screen_saturation_title);
        satu.setDialogTitle(satu.getTitle());
        satu.setSummary(R.string.pref_screen_saturation_summary);
        satu.setEntries(R.array.pref_screen_saturation_entries);
        satu.setEntryValues(R.array.pref_screen_saturation_entryvalues);
        satu.setDefaultValue("use_default");
        sgroup.addPreference(satu);
        setupValueSummary(satu_key,
                          R.string.pref_screen_saturation_summary);

        // opacity
        ListPreference opac = new ListPreference(this);
        opac.setKey(opac_key);
        opac.setTitle(R.string.pref_screen_opacity_title);
        opac.setDialogTitle(opac.getTitle());
        opac.setSummary(R.string.pref_screen_opacity_summary);
        opac.setEntries(R.array.pref_screen_opacity_entries);
        opac.setEntryValues(R.array.pref_screen_opacity_entryvalues);
        opac.setDefaultValue("use_default");
        sgroup.addPreference(opac);
        setupValueSummary(opac_key, R.string.pref_screen_opacity_summary);

        // manage pickable screen numbers
        pickable_screen |= (1 << idx);
    }

    private void convertPictureSourcePreference(
        PictureSourcePreference item, int idx)
    {
        String picsource_key = getKey(SCREEN_PICSOURCE_KEY, idx);
        String picsource_val = pref.getString(picsource_key, null);
        if(picsource_val != null) {
            return;
        }

        String type_key = getKey(SCREEN_TYPE_KEY, idx);
        String type_val = pref.getString(type_key, null);

        String file_key = getKey(SCREEN_FILE_KEY, idx);

        Class<?> cls = null;
        String desc = null;

        if("file".equals(type_val) ||
           (type_val == null && pref.getString(file_key, null) != null)) {
            String file_val = pref.getString(file_key, "");

            cls = SingleSource.class;
            desc = getString(R.string.pref_screen_type_file_desc,
                             PictureUtils.getUriFileName(resolver, file_val));
        }
        else if("folder".equals(type_val)) {
            String folder_key = getKey(SCREEN_FOLDER_KEY, idx);
            String folder_val = pref.getString(folder_key, "");

            cls = FolderSource.class;
            desc = getString(R.string.pref_screen_type_folder_desc,
                             folder_val);
        }
        else if("buckets".equals(type_val)) {
            String bucket_key = getKey(SCREEN_BUCKET_KEY, idx);
            String bucket_val = pref.getString(bucket_key, "");

            cls = AlbumSource.class;
            desc = getString(R.string.pref_screen_type_bucket_desc,
                             PictureUtils.getBucketNames(
                                 resolver, bucket_val));
        }
        else if("use_default".equals(type_val) || idx >= 0) {
            cls = null;
            desc = getString(R.string.pref_use_default);
        }

        if(desc != null) {
            persistPictureSourceInfo(idx, desc, null);
        }

        item.setValue(cls != null ? new ComponentName(this, cls) : null);
    }

    private void persistPictureSourceInfo(int idx, String desc, String service)
    {
        persistPictureSourceInfo(getIndexString(idx), desc, service);
    }

    private void persistPictureSourceInfo(String key,
                                          String desc, String service)
    {
        String desc_key = getKey(SCREEN_PICSOURCE_DESC_KEY, key);
        String service_key = getKey(SCREEN_PICSOURCE_SERVICE_KEY, key);

        int serial = pref.getInt(SCREEN_PICSOURCE_SERIAL_KEY, 0);

        SharedPreferences.Editor editor = pref.edit();
        editor.putString(desc_key, desc);
        if(service != null) {
            editor.putString(service_key, service);
        }
        editor.putInt(SCREEN_PICSOURCE_SERIAL_KEY, serial + 1);
        editor.commit();
    }

    private void updatePictureSourceSummary(
        PictureSourcePreference item, int idx)
    {
        updatePictureSourceSummary(item, getIndexString(idx), (idx < 0));
    }

    private void updatePictureSourceSummary(
        PictureSourcePreference item, String key, boolean is_default)
    {
        ComponentName picsource_val = item.getValue();

        StringBuilder summary = new StringBuilder();
        summary.append(getString(R.string.pref_screen_type_base_summary));

        if(picsource_val != null) {
            String desc_key = getKey(SCREEN_PICSOURCE_DESC_KEY, key);
            String desc_val = pref.getString(desc_key, "");
            if(desc_val.length() != 0) {
                summary.append(
                    getString(R.string.pref_screen_val_summary,
                              desc_val));
            }
        }
        else if(! is_default) {
            summary.append(
                getString(R.string.pref_screen_val_summary,
                          getString(R.string.pref_use_default)));
        }

        item.setSummary(summary);
    }

    private void updateColorSummary(ListPreference item, String val)
    {
        updateValueSummary(item, R.string.pref_screen_bgcolor_summary, val);
    }

    private void updateValueSummary(ListPreference item, int res_id, String val)
    {
        if(val == null) {
            val = item.getValue();
        }

        String summary =
            getString(res_id) +
            getString(R.string.pref_screen_val_summary,
                      item.getEntries()[item.findIndexOfValue(val)]);
        item.setSummary(summary);
        try {
            item.getSummary();
        }
        catch(IllegalFormatException e) {
            // workaround for summary formatter...
            item.setSummary(summary.replaceAll("%", "%%"));
        }
    }

    private void setupValueSummary(String key, int res_id)
    {
        ListPreference item = (ListPreference)
            getPreferenceManager().findPreference(key);
        item.setOnPreferenceChangeListener(new OnValueChangeListener(res_id));
        updateValueSummary(item, res_id, null);
    }

    private void startColorPickerDialog(final Preference item, final String key)
    {
        int color = pref.getInt(getKey(SCREEN_BGCOLOR_CUSTOM_KEY, key),
                                0xff000000);

        View view = getLayoutInflater().inflate(R.layout.color_picker, null);
        final ColorPickerView picker =
            (ColorPickerView)view.findViewById(R.id.color_picker_picker);
        final View expl = view.findViewById(R.id.color_picker_explain);

        picker.setOnColorChangeListener(
            new ColorPickerView.OnColorChangeListener() {
                @Override public void onColorChange(int color) {
                    expl.setBackgroundColor(color);
                }
            });
        picker.setColor(color);

        new AlertDialog.Builder(this)
            .setTitle(R.string.pref_screen_bgcolor_title)
            .setView(view)
            .setPositiveButton(
                android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int button) {
                        applyCustomColor((ListPreference)item, key,
                                         picker.getColor());
                    }
                })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void applyCustomColor(ListPreference item, String key, int color)
    {
        String val = "custom";

        updateColorSummary(item, val);
        item.setValue(val);

        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(getKey(SCREEN_BGCOLOR_CUSTOM_KEY, key), color);
        editor.commit();
    }

    private void startMaxMemoryConfirmDialog(final ListPreference item,
                                             final String val)
    {
        new AlertDialog.Builder(this)
            .setTitle(R.string.pref_memory_max_title)
            .setMessage(R.string.pref_memory_max_confirm)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int button) {
                        item.setValue(val);
                    }
                })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private class ScreenPickerClickListener
        implements Preference.OnPreferenceClickListener
    {
        @Override
        public boolean onPreferenceClick(Preference preference)
        {
            int num = 1;
            for(int i = 0; i < ScreenPickerPreference.SCREEN_COUNT; i++) {
                if((pickable_screen & (1 << i)) == 0) {
                    num = i + 1;
                    break;
                }
            }

            screen_picker.setScreenNumber(num);
            return false;
        }
    }

    private class ScreenPickerListener
        implements ScreenPickerPreference.ScreenPickerListener
    {
        @Override
        public boolean onScreenNumberChanging(int screen_num)
        {
            return ((pickable_screen & (1 << (screen_num - 1))) == 0);
        }

        @Override
        public void onScreenNumberPicked(int screen_num)
        {
            addScreenPreferences(screen_num - 1, false);

            if(pickable_screen == all_screen_mask) {
                screen_picker.setEnabled(false);
            }
        }
    }

    private class OnPictureSourceChangeListener
        implements Preference.OnPreferenceChangeListener
    {
        private String key;
        private boolean is_default;

        private OnPictureSourceChangeListener(int idx)
        {
            this(getIndexString(idx), (idx < 0));
        }

        private OnPictureSourceChangeListener(String key, boolean is_default)
        {
            this.key = key;
            this.is_default = is_default;
        }

        @Override
        public boolean onPreferenceChange(Preference item, Object val)
        {
            final PictureSourcePreference picsource =
                (PictureSourcePreference)item;

            if(val != null) {
                cur_comp = (ComponentName)val;
                Intent intent = picsource.createIntent(cur_comp, key);

                Log.d(TAG, "picture source: start: " + key + ", " + intent);
                try {
                    startActivityForResult(intent, REQUEST_CODE_PICSOURCE);
                    cur_item = picsource;
                    cur_key = key;
                }
                catch(ActivityNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(
                        MultiPictureSetting.this,
                        R.string.picsource_not_found, Toast.LENGTH_SHORT)
                        .show();
                }

                return false;
            }
            else {
                handler.post(new Runnable() {
                        public void run() {
                            persistPictureSourceInfo(key, "", "");
                            updatePictureSourceSummary(
                                picsource, key, is_default);
                        }
                    });
                return true;
            }
        }
    }

    private class OnColorChangeListener
        implements Preference.OnPreferenceChangeListener
    {
        private String key;

        private OnColorChangeListener(int idx)
        {
            this(getIndexString(idx));
        }

        private OnColorChangeListener(String key)
        {
            this.key = key;
        }

        @Override
        public boolean onPreferenceChange(final Preference item, Object val)
        {
            if("custom".equals(val)) {
                startColorPickerDialog(item, key);
                return false;
            }
            else {
                updateColorSummary((ListPreference)item, (String)val);
                return true;
            }
        }
    }

    private class OnValueChangeListener
        implements Preference.OnPreferenceChangeListener
    {
        private int res_id;

        private OnValueChangeListener(int res_id)
        {
            this.res_id = res_id;
        }

        @Override
        public boolean onPreferenceChange(Preference item, Object val)
        {
            updateValueSummary((ListPreference)item, res_id, (String)val);
            return true;
        }
    }

    private class OnMaximumMemoryChangeListener
        implements Preference.OnPreferenceChangeListener
    {
        @Override
        public boolean onPreferenceChange(Preference item, Object val)
        {
            int msize = 0;
            try {
                msize = Integer.valueOf(val.toString());
            }
            catch(Exception e) {
                // ignore
            }

            int mclass = getAutoMemoryClass(MultiPictureSetting.this);
            if(msize < 0 || msize > mclass) {
                startMaxMemoryConfirmDialog(
                    (ListPreference)item, val.toString());
                return false;
            }
            else {
                return true;
            }
        }
    }

    private class OnDntClickListener
        implements  Preference.OnPreferenceClickListener
    {
        @Override
        public boolean onPreferenceClick(Preference preference)
        {
            Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.dnt_search_uri)));

            try {
                startActivity(intent);
            }
            catch(ActivityNotFoundException e) {
                Toast.makeText(MultiPictureSetting.this,
                               R.string.market_not_found,
                               Toast.LENGTH_SHORT)
                    .show();
            }

            return false;
        }
    }
}
