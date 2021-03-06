package lb.themike10452.hellscorekernelmanagerl.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lb.themike10452.hellscorekernelmanagerl.CustomWidgets.NumberModifier;
import lb.themike10452.hellscorekernelmanagerl.R;
import lb.themike10452.hellscorekernelmanagerl.Settings;
import lb.themike10452.hellscorekernelmanagerl.utils.HKMTools;
import lb.themike10452.hellscorekernelmanagerl.utils.Library;

import static lb.themike10452.hellscorekernelmanagerl.Settings.Constants.SET_GOV_SETTINGS_ON_BOOT;
import static lb.themike10452.hellscorekernelmanagerl.utils.HKMTools.ScriptUtils.GOV_SETTINGS_SCRIPT_NAME;

/**
 * Created by Mike on 2/28/2015.
 */
public class CPUGovernorCfg extends Fragment {

    private static CPUGovernorCfg instance;

    private Activity mActivity;
    private LinearLayout list;
    private SharedPreferences sharedPreferences;
    private String title;
    private View mView;
    private mAdapter adapter;

    public static CPUGovernorCfg getNewInstance(@NonNull String title) {
        if (instance == null) {
            instance = new CPUGovernorCfg();
        }
        instance.title = title;
        return instance;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        sharedPreferences = mActivity.getSharedPreferences(Settings.Constants.SHARED_PREFS_ID, Context.MODE_PRIVATE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        Switch setOnBootSwitch = ((Switch) menu.findItem(R.id.action_setOnBoot).getActionView().findViewById(R.id.sob_switch));
        boolean sobEnabled = sharedPreferences.getBoolean(SET_GOV_SETTINGS_ON_BOOT, false);
        setOnBootSwitch.setChecked(sobEnabled);
        setOnBootSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeSetOnBootState(isChecked);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                refresh();
                return true;
            case R.id.action_apply:
                saveAll(true);
                return true;
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mView = inflater.inflate(R.layout.fragment_gov_cfg, null, false);
        ((TextView) mView.findViewById(R.id.title)).setText(title);

        File cfgDir = new File(String.format(Library.CPU_GOV_CFG_PATH, title));
        if (cfgDir.exists() && cfgDir.isDirectory()) {
            final File[] params = cfgDir.listFiles();
            adapter = new mAdapter(mActivity, params);
            list = (LinearLayout) mView.findViewById(R.id.listView);
            for (int i = 0; i < adapter.getCount(); i++) {
                View v = adapter.getView(i, null, null);
                if (v != null)
                    list.addView(v);
            }
        }

        return mView;
    }

    public void refresh() {
        adapter.reset();
        list.removeAllViews();
        for (int i = 0; i < adapter.getCount(); i++) {
            View v = adapter.getView(i, null, null);
            if (v != null)
                list.addView(v);
        }
    }

    public void saveAll(boolean fromUser) {
        HKMTools tools = HKMTools.getInstance();
        tools.getReady();
        for (int i = 0; i < adapter.getCount(); i++) {
            String value = adapter.getValue(i);
            if (value != null) {
                HKMTools.getInstance().addCommand("echo ".concat(value).concat(" > ").concat(adapter.getItem(i).getAbsolutePath()));
            }
        }
        createScript(tools.getRecentCommandsList());
        tools.flush();
        if (fromUser) {
            Toast.makeText(mActivity.getApplicationContext(), R.string.message_applied_successfully, Toast.LENGTH_SHORT).show();
        }
    }

    public void changeSetOnBootState(boolean enabled) {
        sharedPreferences.edit().putBoolean(SET_GOV_SETTINGS_ON_BOOT, enabled).apply();
        saveAll(false);
        Toast.makeText(mActivity, enabled ? R.string.message_set_on_boot_enabled : R.string.message_set_on_boot_disabled, Toast.LENGTH_SHORT).show();
    }

    private PopupWindow getPopupWindow() {
        PopupWindow popupWindow = new PopupWindow(mActivity);
        popupWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setHeight(1);
        popupWindow.setWidth(1000);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.popup_window_background));
        popupWindow.setClippingEnabled(false);
        popupWindow.setElevation(10f);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        return popupWindow;
    }

    private void createScript(List<String> commandList) {
        boolean sobEnabled = sharedPreferences.getBoolean(SET_GOV_SETTINGS_ON_BOOT, false);
        if (!sobEnabled) {
            HKMTools.ScriptUtils.clearScript(mActivity.getApplicationContext(), GOV_SETTINGS_SCRIPT_NAME);
        } else {
            try {
                HKMTools.ScriptUtils.writeScript(mActivity.getApplicationContext(), GOV_SETTINGS_SCRIPT_NAME, commandList, true);
            } catch (IOException e) {
                Toast.makeText(mActivity.getApplicationContext(), R.string.message_script_failed, Toast.LENGTH_SHORT).show();
                Toast.makeText(mActivity.getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    class mAdapter extends ArrayAdapter<File> {

        private Context mContext;
        private ArrayList<TextView> valueHolders;
        private ArrayList<String> values;

        public mAdapter(Context context, File[] objects) {
            super(context, R.layout.gov_cfg_list_item, objects);
            mContext = context;
            values = new ArrayList<>();
            valueHolders = new ArrayList<>();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View mView = convertView;
            if (mView == null)
                mView = LayoutInflater.from(mContext).inflate(R.layout.gov_cfg_list_item, null, false);

            String value = HKMTools.getInstance().readBlockFromFile(getItem(position));
            try {
                Integer.parseInt(value);
            } catch (Exception e) {
                Log.d("TAG", e.toString());
                values.add(null);
                valueHolders.add(null);
                return null;
            }

            values.add(value);

            ((TextView) mView.findViewById(R.id.fileName)).setText(getItem(position).getName());

            final TextView textView = (TextView) mView.findViewById(R.id.value);
            textView.setText(value);
            valueHolders.add(textView);

            mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupWindow popupWindow = getPopupWindow();
                    String value = textView.getText().toString();
                    final NumberModifier modifier = new NumberModifier(mActivity);
                    modifier.setValue(value);
                    modifier.setPadding(20, 20, 20, 20);
                    modifier.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    popupWindow.setContentView(modifier);
                    popupWindow.showAsDropDown(view, view.getMeasuredWidth() - modifier.getMeasuredWidth(), 0);
                    popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            updateValue(position, modifier.getValue());
                        }
                    });
                }
            });

            return mView;
        }

        public void reset() {
            values.clear();
            valueHolders.clear();
        }

        public String getValue(int position) {
            return values.get(position);
        }

        public TextView getValueHolder(int position) {
            return valueHolders.get(position);
        }

        public void updateValue(int position, String value) {
            values.remove(position);
            values.add(position, value);
            getValueHolder(position).setText(value);
        }

    }

}
