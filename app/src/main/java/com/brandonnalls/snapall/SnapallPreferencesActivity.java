package com.brandonnalls.snapall;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by brandonn on 11/30/14.
 */
public class SnapallPreferencesActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle neglectedSavedInstanceState) {
        super.onCreate(neglectedSavedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.settings);
    }
}
