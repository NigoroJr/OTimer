package com.nigorojr.o_timer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpStatus;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * A timer app that counts how many days, hours, minutes, and seconds has passed since the start.
 * You can send your records to the server and see the rankings by going to http://nigorojr.com/naoki/apps/otimer/
 * Use this app when you have something you know you shouldn't do but can't stop (such as smoking, drinking, etc... ;P)
 * @author Naoki Mizuno
 * TODO: Add a User class that keeps track of user's past records.
 * TODO: Improve debug info sending (make a new Activity)
 * TODO: Add JavaDoc.
 *
 */

public class MainActivity extends FragmentActivity implements View.OnClickListener {
    
    public static final int REQUEST_TIME = 0;
    public static final String TIMER_STOPPED = "timerStopped";
    public static final String ALERT_KEY = "titleForAlertUser";
    private static OTimer timer;
    private long id;
    // Using SharedPreferences each time username is needed is safer since
    // we don't know if the "username" variable has been updated after changed via preference.
    // private String username;
    private Timer t;
    
    public static final int SEND_DEBUG_INFO = 1;
    public static final String DEFAULT_SERVER_ADDRESS = "http://nigorojr.com/naoki/cgi-bin/otimer.cgi";
    public static final String RANKING_URL = "http://nigorojr.com/naoki/apps/otimer/";
    
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            
            updateTextViews(timer.getDiff());

            long timeToGoal = timer.getSecondsToGoal();
            int secondsSoFar = (int)(timer.getInitialSecondsToGoal() - timeToGoal);
            // Log.i("debug", secondsSoFar + " / " + timer.getInitialSecondsToGoal());

            // Prepare Progress bar
            ProgressBar pb = (ProgressBar)findViewById(R.id.progress);
            pb.setMax((int)timer.getInitialSecondsToGoal());

            // When setting the text on the button,
            // first assume that the timer is started. Check if it really is a few lines later.
            Button b = (Button)findViewById(R.id.button_start_stop);
            b.setText(getResources().getString(R.string.button_stop));

            // Set the TextViews to 0 days 0 hours 0 minutes 0 seconds if the timer isn't started
            if (!timer.isStarted()) {
                updateTextViews(new int[]{0, 0, 0, 0});
                b.setText(getResources().getString(R.string.button_start));
            }

            // When no goal is set
            if (timeToGoal == -1) {
                findViewById(R.id.goal_left_row).setVisibility(View.GONE);
                // TODO: Progress until next rank when no goal is set
                pb.setProgress(0);
            }
            // When the user accomplished his goal
            else if (timer.getGoalDate().before(Calendar.getInstance()) && !timer.isCongratulated())
                congratulate();
            else {
                findViewById(R.id.goal_left_row).setVisibility(View.VISIBLE);

                TextView tv = (TextView)findViewById(R.id.goal_left);
                float percent =  (float)(100 * (1 - timer.getSecondsToGoal() * 1.0 / timer.getInitialSecondsToGoal()));
                String str = String.format(Locale.getDefault(), "%d/%d/%d (%.3f%%, %s%d %s)"
                    , timer.getGoalDate().get(Calendar.YEAR)
                    , timer.getGoalDate().get(Calendar.MONTH) + 1
                    , timer.getGoalDate().get(Calendar.DATE)
//                    , timer.getGoalDate().get(Calendar.HOUR)
//                    , timer.getGoalDate().get(Calendar.MINUTE)
//                    , timer.getGoalDate().get(Calendar.SECOND)
                    , percent
                    , getResources().getString(R.string.goal_left_2)
                    , timer.getSecondsToGoal() / 60
                    , getResources().getString(R.string.unit_minutes));
                tv.setText(str);

                pb.setProgress(secondsSoFar);
            }
            
            // DEBUG
            //showStatus();

            // Sends status if the user opened preference and entered a title in the debug section
            sendStatus();

            // Use a flag or something to detect change
            //saveStartDateAndGoal();
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        init();

        // Set OnClickListener to buttons
        findViewById(R.id.button_start_stop).setOnClickListener(this);
        findViewById(R.id.button_send_record).setOnClickListener(this);
        findViewById(R.id.button_check_ranking).setOnClickListener(this);

        t = new Timer();
        // Send request for time every second
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(0);
            }
        };
        t.scheduleAtFixedRate(task, 0, 1000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_set_start_date:
                setStartDate();
                break;
            case R.id.action_set_goal:
                setGoal();
                // Don't start timer yet because user might cancel
                break;
            case R.id.action_settings:
                // TODO: Change this so that we don't receive debug info every time the user opens preference
                startActivityForResult(new Intent(this, OTimerPreference.class), SEND_DEBUG_INFO);
                break;
        }
        return true;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }
    
    /**
     * This method is called when the buttons are clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_send_record:
                sendRecord();
                break;
            case R.id.button_start_stop:
                if (timer.isStarted())
                    //stopTimer();
                    confirmStop();
                else
                    startTimer();
                break;
            case R.id.button_check_ranking:
                if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
                        getBoolean("show_ranking", false))
                    loadWebView(RANKING_URL);
                else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RANKING_URL));
                    startActivity(intent);
                }
                break;
        }
    }
    
    /**
     * This method is called when the application is closed. It cancels the Timer object's job, which
     * is counting every second, and saves the starting date and the goal date (if any) only if the
     * OTimer object has been started.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        t.cancel();
        
        // Save the start day only if the timer is started
        if (timer.isStarted())
            saveStartDateAndGoal();
    }
    
    /**
     * Initializes the application. This method loads the date that the timer started, the goal date,
     * user's ID (generate one if none is assigned), username, and decides whether to show the ranking
     * in the main menu.
     */
    public void init() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        long startDate = sp.getLong("startDate", -1);
        // If the user exited the program after stopping the timer
        if (startDate != -1)
            timer = new OTimer(startDate);
        else
            timer = new OTimer();

        long goalDate = sp.getLong("goalDate", -1);
        long goalSetAt = sp.getLong("goalSetAt", -1);
        if (goalDate != -1) {
            timer.setGoal(goalDate);
            timer.setGoalSetAt(goalSetAt);
        }
        else
            findViewById(R.id.goal_left_row).setVisibility(View.GONE);
        
        // Get the ID. If no ID is assigned, generate one
        // TODO: Contact the server if there is no duplicate
        id = sp.getLong("id", -1);
        if (id == -1) {
            SharedPreferences.Editor editor = sp.edit();
            java.util.Random r = new java.util.Random();
            id = Math.abs(r.nextLong());
            editor.putLong("id", id);
            editor.commit();
        }
        
        // Prompt for a username if there is none
        String username = sp.getString("username", "");
        if (username.equals("")) {
            PromptUsernameDialog pund = new PromptUsernameDialog();
            pund.show(getSupportFragmentManager(), "tag");
        }
        
        if (sp.getBoolean("show_ranking", true)) {
            ((ImageButton)findViewById(R.id.button_check_ranking))
                .setContentDescription(getResources().getString(R.string.button_update_ranking));
            findViewById(R.id.webview_ranking).setVisibility(View.VISIBLE);
            loadWebView(RANKING_URL);
        }
    }
    
    /**
     * Starts the timer. An OTimer object should have been created in the init() method.
     */
    public void startTimer() {
        timer.start();
    }
    
    /**
     * Stops the timer. This should be used after confirming with a dialog.
     */
    public void stopTimer() {
        resetStartDateAndGoal();
        
        // Show dialog
        // TODO: Use the username
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String title = sp.getString("reset_title", "");
        String message = sp.getString("reset_message", "");
        if (title.equals(""))
            title = getResources().getString(R.string.reset_title);
        if (message.equals(""))
            message = getResources().getString(R.string.reset_message);
        showAlertDialog(title, message);

        title = getResources().getString(R.string.timer_stopped_title);
        String unit_days = getResources().getString(R.string.unit_days);
        String unit_hours = getResources().getString(R.string.unit_hours);
        String unit_minutes = getResources().getString(R.string.unit_minutes);
        String unit_seconds = getResources().getString(R.string.unit_seconds);
        message = String.format(Locale.getDefault(), "%d%s %d%s %d%s %d%s",
                timer.getDiff()[0], unit_days,
                timer.getDiff()[1], unit_hours,
                timer.getDiff()[2], unit_minutes,
                timer.getDiff()[3], unit_seconds);
        // Show a dialog
        showAlertDialog(title, message);
        
        // Stop the timer, of course
        timer.stop();
        
        // Send to the server that the user has reseted
        sendRecord();
    }
    
    public void updateTextViews(int[] value) {
        // Change the TextView to the elapsed time
        if (value == null)
            return;
        int[] ids = {R.id.days, R.id.hours, R.id.minutes, R.id.seconds};
        for (int i = 0; i < value.length; i++) {
            TextView tv = (TextView)findViewById(ids[i]);
            tv.setText(" " + String.valueOf(value[i]));
        }
    }
    
    public void congratulate() {
        timer.setCongratulated(true);
        showAlertDialog(getResources().getString(R.string.congratulate_title),
                getResources().getString(R.string.congratulate));
    }
    
    public void showAlertDialog(String title, String message) {
        AlertUser au = new AlertUser();
        Bundle bundle = new Bundle();
        String[] titleAndMessage = {title, message};
        bundle.putStringArray(ALERT_KEY, titleAndMessage);
        au.setArguments(bundle);
        au.show(getSupportFragmentManager(), "tag");
    }
    
    public void saveStartDateAndGoal() {
        // Add to SharedPreferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sp.edit();
        if (timer.isStarted())
            editor.putLong("startDate", timer.getStartDate().getTimeInMillis());
        if (timer.isGoalSet()) {
            editor.putLong("goalDate", timer.getGoalDate().getTimeInMillis());
            editor.putLong("goalSetAt", timer.getGoalSetAt().getTimeInMillis());
        }
        editor.commit();
    }
    
    public void resetStartDateAndGoal() {
        // Reset the start date to default
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove("startDate");
        editor.remove("goalDate");
        editor.remove("goalSetAt");
        editor.commit();
    }
    
    public void printDebug() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(getApplicationContext(), "External media not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File f = new File(Environment.getExternalStorageDirectory() + "/OTimerDebugOutput.txt");
            FileOutputStream fop = new FileOutputStream(f, true);
            PrintWriter pw = new PrintWriter(fop);
            pw.append(getStatus());
            //pw.println(getStatus());
            pw.flush();
            pw.close();
            Toast.makeText(getApplicationContext(), "Successfully written!", Toast.LENGTH_SHORT).show();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void setStartDate() {
        FragmentManager fm = getSupportFragmentManager();
        DatePickingDialog dp = new DatePickingDialog();
        dp.show(fm, "tag");
    }

    public static class DatePickingDialog extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Calendar cal;
            if (timer.getStartDate() != null)
                cal = timer.getStartDate();
            else
                cal = Calendar.getInstance();

            DatePickerDialog dpd = new DatePickerDialog(getActivity(), this,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DATE));
            dpd.setTitle(this.getResources().getString(R.string.dialog_title));
            return dpd;
        }

        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            Calendar toSet = Calendar.getInstance();
            // A little check to see if the entered value is valid
            toSet.set(year, monthOfYear, dayOfMonth);
            if (toSet.after(Calendar.getInstance())) {
                Toast.makeText(getActivity(), getResources().getString(R.string.set_to_future), Toast.LENGTH_SHORT).show();
                return;
            }
            timer.setStartingDate(year, monthOfYear, dayOfMonth);
        }
    }
    
    // TODO: Add a checkbox that let's the user choose whether the goal is set relatively or absolutely to today.
    public void setGoal() {
        FragmentManager fm = getSupportFragmentManager();
        SetGoalDialog sgd = new SetGoalDialog();
        sgd.show(fm, "tag");
    }

    public static class SetGoalDialog extends DialogFragment implements DialogInterface.OnClickListener {
        EditText goalEditText;
        CheckBox isRelative;
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    if (goalEditText.getText().toString().equals(""))
                        return;
                    if (isRelative.isChecked())
                        timer.setGoalRelative(Integer.parseInt(goalEditText.getText().toString()));
                    else if (-1 == timer.setGoalAbsolute(Integer.parseInt(goalEditText.getText().toString())))
                            Toast.makeText(getActivity(), getResources().getString(R.string.set_to_future),
                                    Toast.LENGTH_SHORT).show();
                    // Start timer at this point!
                    timer.start();
                    break;
            }
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(getResources().getString(R.string.dialog_ok), this);
            builder.setNeutralButton(getResources().getString(R.string.dialog_cancel), this);
            // Checkbox
            isRelative = new CheckBox(getActivity());
            isRelative.setChecked(true);
            isRelative.setText(getResources().getString(R.string.goal_checkbox));
            // Input dialog
            goalEditText = new EditText(getActivity());
            goalEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
            goalEditText.setHint(getResources().getString(R.string.goal_dialog_hint));
            // Set 1 day as default
            // TODO: make this configurable
            goalEditText.setText("1");
            // Set cursor to the very end
            goalEditText.setSelection(goalEditText.getText().toString().length());
            // Unit
            TextView tv = new TextView(getActivity());
            tv.setText(getResources().getString(R.string.unit_days));

            // Put those together
            LinearLayout input = new LinearLayout(getActivity());
            input.setOrientation(LinearLayout.HORIZONTAL);
            input.setGravity(Gravity.CENTER_HORIZONTAL);
            input.addView(goalEditText);
            input.addView(tv);

            LinearLayout layout = new LinearLayout(getActivity());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(isRelative);
            layout.addView(input);

            builder.setView(layout);
            
            dialog = builder.create();
            dialog.setTitle(getResources().getString(R.string.goal_dialog_title));
            return dialog;
        }
    }
    
    public static class AlertUser extends DialogFragment implements DialogInterface.OnClickListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle bundle = getArguments();
            String[] titleAndMessage = null;
            if (bundle != null)
                titleAndMessage = bundle.getStringArray(ALERT_KEY);

            AlertDialog dialog = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton("OK", this);
            // Change the message depending on the argument
            if (titleAndMessage != null) {
                if (!titleAndMessage[0].equals(""))
                    builder.setTitle(titleAndMessage[0]);
                if (!titleAndMessage[1].equals(""))
                builder.setMessage(titleAndMessage[1]);
            }
            
            dialog = builder.create();
            return dialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
        }
    }

    public void confirmStop() {
        ConfirmDialog cd = new ConfirmDialog();
        Bundle bundle = new Bundle();
        bundle.putString("title", getResources().getString(R.string.confirm_stop_title));
        bundle.putString("message", getResources().getString(R.string.confirm_stop_message));
        cd.setArguments(bundle);
        cd.show(getSupportFragmentManager(), "tag");
    }
    
    public static class ConfirmDialog extends DialogFragment implements DialogInterface.OnClickListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            Bundle passed = getArguments();
            String title = passed.getString("title");
            String message = passed.getString("message");
            
            AlertDialog dialog = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(getResources().getString(R.string.dialog_yes), this);
            builder.setNegativeButton(getResources().getString(R.string.dialog_no), this);
            builder.setTitle(title);
            builder.setMessage(message);
            dialog = builder.create();
            return dialog;
        }
        
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    MainActivity main = (MainActivity)getActivity();
                    main.stopTimer();
                    break;
                case AlertDialog.BUTTON_NEGATIVE:
                    break;
            }
        }
    }
    
    public static class PromptUsernameDialog extends DialogFragment implements DialogInterface.OnClickListener {
        EditText usernameEditText;
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    if (usernameEditText.getText().toString().equals("")
                            || usernameEditText.getText().toString().equals("anonymous")) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.preference_username_invalid),
                                Toast.LENGTH_SHORT).show();
                        Toast.makeText(getActivity(), getResources().getString(R.string.preference_username_invalid_suggest),
                                Toast.LENGTH_SHORT).show();
                    }
                    else {
                        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
                        editor.putString("username", usernameEditText.getText().toString());
                        editor.commit();
                    }
                    break;
            }
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(getResources().getString(R.string.dialog_ok), this);
            //builder.setNeutralButton(getResources().getString(R.string.dialog_cancel), this);
            // Input dialog
            usernameEditText = new EditText(getActivity());

            LinearLayout layout = new LinearLayout(getActivity());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(usernameEditText);

            builder.setView(layout);
            
            dialog = builder.create();
            dialog.setTitle(getResources().getString(R.string.preference_username_dialog_title));
            return dialog;
        }
    }
    
    private void showStatus() {
        Log.i("status", getStatus());
    }
    
    private void sendStatus() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        String message = sp.getString("debug_editText", "no_input");
        if (message.equals("no_input"))
            return;
        else
            message += "\n" + getStatus();

        // Get the server's URL if changed
        String serverAddress = sp.getString("debug_change_server", DEFAULT_SERVER_ADDRESS);
        String username = sp.getString("username", "");
        if (username.equals("anonymous")) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.preference_username_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        else if (username.equals(""))
            username = "anonymous";
        SendToServer sts = new SendToServer(serverAddress, username + " (" + id + ")\n" + message,  "debug");
        
        // Send and see how it turns out
        toastHttpResponce(sts.send());
        
        // Clear
        SharedPreferences.Editor editor = sp.edit();
        editor.remove("debug_editText");
        editor.commit();
    }
    
    private String getStatus() {
        String str = String.format("Current time: %d/%d/%d %d:%02d:%02d\n"
                    , Calendar.getInstance().get(Calendar.YEAR)
                    , Calendar.getInstance().get(Calendar.MONTH) + 1
                    , Calendar.getInstance().get(Calendar.DATE)
                    , Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    , Calendar.getInstance().get(Calendar.MINUTE)
                    , Calendar.getInstance().get(Calendar.SECOND));
        if (timer == null)
            return "Timer is null.\n\n";
        else if (!timer.isStarted())
            str = "Timer is not started.\n";
        else
            str = String.format("Timer is started at %d/%d/%d %d:%02d:%02d which is %d days %d hours %d minutes and %d seconds from now"
                    , timer.getStartDate().get(Calendar.YEAR)
                    , timer.getStartDate().get(Calendar.MONTH) + 1
                    , timer.getStartDate().get(Calendar.DATE)
                    , timer.getStartDate().get(Calendar.HOUR_OF_DAY)
                    , timer.getStartDate().get(Calendar.MINUTE)
                    , timer.getStartDate().get(Calendar.SECOND)
                    , timer.getDiff()[0]
                    , timer.getDiff()[1]
                    , timer.getDiff()[2]
                    , timer.getDiff()[3]);

        if (!timer.isGoalSet())
            str += ", goal is NOT set.\n\n";
        else
            str += String.format(", goal is set to %d/%d/%d %d:%02d:%02d which is %d seconds from now.\n\n"
                    , timer.getGoalDate().get(Calendar.YEAR)
                    , timer.getGoalDate().get(Calendar.MONTH) + 1
                    , timer.getGoalDate().get(Calendar.DATE)
                    , timer.getGoalDate().get(Calendar.HOUR_OF_DAY)
                    , timer.getGoalDate().get(Calendar.MINUTE)
                    , timer.getGoalDate().get(Calendar.SECOND)
                    , timer.getSecondsToGoal());
        return str;
    }
    
    public void sendRecord() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String serverAddress = sp.getString("serverAddress", DEFAULT_SERVER_ADDRESS);
        String username = sp.getString("username", "anonymous");
        if (username.equals("") || username.equals("anonymous")) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.preference_username_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        SendToServer sts;
        // Send a "0" days when the timer is not started
        if (!timer.isStarted())
	        sts = new SendToServer(serverAddress, "0", "records", username, id);
        else
	        sts = new SendToServer(serverAddress, timer.getDiff()[0] + "", "records", username, id);

        // Send and see how it turns out
        toastHttpResponce(sts.send());
        
        // Update ranking after sending
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
                getBoolean("show_ranking", true))
            loadWebView(RANKING_URL);
    }
    
    public void toastHttpResponce(int status) {
        String str = "";
        switch (status) {
            case HttpStatus.SC_OK:
                str = getResources().getString(R.string.debug_response_ok);
                break;
            case HttpStatus.SC_NOT_FOUND:
                str = getResources().getString(R.string.debug_response_not_found);
                break;
            case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                str = getResources().getString(R.string.debug_response_internal_server_error);
                break;
            default:
                str = getResources().getString(R.string.debug_response_other);
                break;
        }
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }
    
    public void loadWebView(String url) {
        WebView wv = (WebView)findViewById(R.id.webview_ranking);
        wv.setWebViewClient((new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        }));
        wv.getSettings().setUserAgentString("OTimerWebView");
        wv.loadUrl(RANKING_URL);
    }
}
