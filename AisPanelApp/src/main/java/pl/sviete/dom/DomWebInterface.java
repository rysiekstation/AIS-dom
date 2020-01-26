package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_ACTIVITY_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;

public class DomWebInterface {
    final static String TAG = DomWebInterface.class.getName();

    private static void doPost(JSONObject message, Context context){
        // do the simple HTTP post
        String url =  pl.sviete.dom.AisCoreUtils.getAisDomUrl() + "/api/webhook/aisdomprocesscommandfromframe";
        AsyncHttpPost post = new AsyncHttpPost(url);
        JSONObjectBody body = new JSONObjectBody(message);
        post.addHeader("Content-Type", "application/json");
        post.setBody(body);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new AsyncHttpClient.JSONObjectCallback() {
            // Callback is invoked with any exceptions/errors, and the result, if available.
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse response, JSONObject result) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }
                Log.e(TAG, result.toString());
                Log.e(TAG, context.getClass().toString());
                if (result.has("say_it")){
                    try {
                    String text = result.getString("say_it");
                    Intent intent = null;
                    if (context.getClass().toString().equals("pl.sviete.dom.AisPanel")) {
                        // service is runing
                        intent = new Intent(BROADCAST_SERVICE_SAY_IT);
                    } else {
                        //  pl.sviete.dom.BrowserActivityNative
                        intent = new Intent(BROADCAST_ACTIVITY_SAY_IT);
                    }
                    intent.putExtra(BROADCAST_SAY_IT_TEXT, text);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                    bm.sendBroadcast(intent);
                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    public static void publishMessage(String message, String topicPostfix, Context context){
        // publish via http rest to local instance
        JSONObject json = new JSONObject();
        try {
            json.put("topic", "ais/" + topicPostfix);
            json.put("ais_gate_client_id", AisCoreUtils.AIS_GATE_ID);
            json.put("payload", message);
        } catch (JSONException e) {
            Log.e("publishMessage", e.toString());
        }

        doPost(json, context);
    }

    // new
    public static void publishJson(JSONObject message, String topic, Context context){
        JSONObject json = new JSONObject();
        try {
            json.put("topic", "ais/" + topic);
            json.put("ais_gate_client_id", AisCoreUtils.AIS_GATE_ID);
            json.put("payload", message);
        } catch (JSONException e) {
            Log.e("publishJson", e.toString());
        }

        doPost(json, context);
    }

}
