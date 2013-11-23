package com.nigorojr.o_timer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.os.AsyncTask;

public class SendToServer {
    String username;
    String type;
    String message;
    String serverAddress;
    String id = "none";
    
    public SendToServer(String serverAddress) {
        this(serverAddress, "");
    }
    
    public SendToServer(String serverAddress, String message) {
        this(serverAddress, message, "");
    }
    
    public SendToServer(String serverAddress, String message, String type) {
        // TODO: Handle empty user name (prompt the user if there are none?)
        this(serverAddress, message, type, "");
    }
    
    public SendToServer(String serverAddress, String message, String type, String username) {
        this.serverAddress = serverAddress;
        this.message = message;
        this.type = type;
        this.username = username;
    }
    
    public SendToServer(String serverAddress, String message, String type, String username, long id) {
        this(serverAddress, message, type, username);
        this.id = id + "";
    }
    
    public int send() {
        AsyncSend as = new AsyncSend();
        AsyncTask<String,Integer,Integer> result = as.execute(message);
        int ret = -1;
        try {
            ret = result.get().intValue();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            e.printStackTrace();
        }
        return ret;
    }
    
    public class AsyncSend extends AsyncTask<String, Integer, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(serverAddress);
            
            ArrayList<NameValuePair> list = new ArrayList<NameValuePair>();
            list.add(new BasicNameValuePair("message", message));
            if (!type.equals(""))
                list.add(new BasicNameValuePair("type", type));
            if (!username.equals(""))
                list.add(new BasicNameValuePair("username", username));
            
            // Add ID if exists
            if (!id.equals("none"))
                list.add(new BasicNameValuePair("id", id));
            
            HttpResponse res = null;
            
            try {
                post.setEntity(new UrlEncodedFormEntity(list, "UTF-8"));
                res = client.execute(post);
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            return res.getStatusLine().getStatusCode();
        }
    }
}
