package com.nigorojr.o_timer;

import java.util.Calendar;

import android.app.Service;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class OTimer {
    private Calendar startDate;
    private Calendar goalDate;
    private Calendar goalSetAt;
    // The following has to be -1 because this is what will be returned in
    // getInitialSecondsToGoal() when no goalDate is set.
    private boolean started = false;
    private boolean goalSet = false;
    private boolean congratulated = false;
    
    public OTimer() {
    }
    
    // If the program last exited while timer was working
    public OTimer(long startDate) {
        this.startDate = Calendar.getInstance();
        this.startDate.setTimeInMillis(startDate);
        started = true;
    }
    
    public void start() {
        if (!started) {
            startDate = Calendar.getInstance();
            started = true;
        }
    }
    
    public void stop() {
        started = false;
        goalSet = false;
    }
    
    public Calendar getStartDate() {
        return startDate;
    }
    
    public Calendar getGoalDate() {
        return goalDate;
    }
    
    public Calendar getGoalSetAt() {
        return goalSetAt;
    }

    public long getInitialSecondsToGoal() {
        if (!goalSet)
            return -1;
        return (goalDate.getTimeInMillis() - goalSetAt.getTimeInMillis()) / 1000;
    }
    
    public long getSecondsToGoal() {
        if (!goalSet)
            return -1;
        Calendar current = Calendar.getInstance();
        long seconds = (goalDate.getTimeInMillis() - current.getTimeInMillis()) / 1000;

        return seconds;
    }
    
    public int[] getDiff() {
        if (!started)
            return null;

        Calendar current = Calendar.getInstance();
        long diff = current.getTimeInMillis() - startDate.getTimeInMillis();

        long days = (diff / 1000) / (60 * 60 * 24);
        diff = (diff / 1000) % (60 * 60 * 24);
        long hours = diff / (60 * 60);
        diff = diff % (60 * 60);
        long minutes = diff / 60;
        diff = diff % 60;
        long seconds = diff;

        int[] ret = {(int)days, (int)hours, (int)minutes, (int)seconds};
        return ret;
    }
    
    public void setStartingDate(int year, int month, int day) {
        if (!started)
            start();
        startDate.set(year, month, day);
    }
    
    /**
     * This is an absolute scale. If being specified 5 days, this method assumes that the goal
     * is on the 5th day from the beginning.
     * @param millisecOfGoal Millisecond from the epoch representing the absolute goal date.
     * @return -1 if the goal was specified to be set BEFORE today. 0 if otherwise.
     */
    public int setGoal(long millisecOfGoal) {
        // Check if goal is really AFTER today
        Calendar test = Calendar.getInstance();
        test.setTimeInMillis(millisecOfGoal);
        if (test.before(Calendar.getInstance()))
            return -1;
        if (goalDate == null)
            goalDate = Calendar.getInstance();
        goalDate.setTimeInMillis(millisecOfGoal);
        goalSetAt = Calendar.getInstance();
        goalSet = true;
        return 0;
    }
    
    public int setGoalAbsolute(int days) {
        if (!started)
            start();
        return setGoal(startDate.getTimeInMillis() + (long)days * 60 * 60 * 24 * 1000);
    }
    
    /**
     * This is a relative scale. If 5 days is specified, it means that the goal is set to 5 days from today.
     * @param days When goal date is.
     */
    public void setGoalRelative(int days) {
        if (!started)
            start();
        if (goalDate == null)
            goalDate = Calendar.getInstance();
        // Milliseconds that will be added to the current date
        long plus = (long)days * 60 * 60 * 24 * 1000;

        setGoal((Calendar.getInstance().getTimeInMillis() + plus));
    }
    
    public void setGoalSetAt(long goalSetAtMillisec) {
        goalSetAt = Calendar.getInstance();
        goalSetAt.setTimeInMillis(goalSetAtMillisec);
    }

    public void setCongratulated(boolean congratulated) {
        this.congratulated = congratulated;
    }

    public boolean isStarted() {
        return started;
    }
    
    public boolean isGoalSet() {
        return goalSet;
    }
    
    public boolean isCongratulated() {
        return congratulated;
    }
}