package com.nigorojr.o_timer;

import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;

public class OTimerPreference extends PreferenceActivity {
    @TargetApi(11)
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 11) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferenceFragment() {
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    addPreferencesFromResource(R.xml.preference_layout);
                }
            }).commit();
        }
        else
            addPreferencesFromResource(R.xml.preference_layout);
    }
}
