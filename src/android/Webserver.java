package org.apache.cordova.plugin;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLServerSocketFactory;

import fi.iki.elonen.NanoHTTPD;


public class Webserver extends CordovaPlugin {

    public HashMap<String, Object> responses;
    public CallbackContext onRequestCallbackContext;
    public NanoHTTPDWebserver nanoHTTPDWebserver;

    private ExecutorService executorService;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.responses = new HashMap<>();
        executorService = cordova.getThreadPool();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if ("start".equals(action)) {
            executorService.execute(() -> start(args, callbackContext));
            return true;
        }
        if ("stop".equals(action)) {
            executorService.execute(() -> stop(args, callbackContext));
            return true;
        }
        if ("onRequest".equals(action)) {
            executorService.execute(() -> onRequest(args, callbackContext));
            return true;
        }
        if ("sendResponse".equals(action)) {
            executorService.execute(() -> sendResponse(args, callbackContext));
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    /**
     * Starts the server
     *
     * @param args
     * @param callbackContext
     */
    private void start(JSONArray args, CallbackContext callbackContext) {
        int port = 8080;

        if (args.length() >= 1) {
            try {
                port = args.getInt(0);
            } catch (JSONException e) {
                e.printStackTrace();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
                return;
            }
        }

        if (this.nanoHTTPDWebserver != null) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Server already running"));
            return;
        }

        try {
            this.nanoHTTPDWebserver = new NanoHTTPDWebserver(port, this);

            if (args.length() >= 3) {
                String keystorePath = args.getString(1);
                Log.d(this.getClass().getName(), "Setting up SSL with keystore " + keystorePath);
                String keystorePassword = args.getString(2);
                SSLServerSocketFactory socketFactory = NanoHTTPD.makeSSLSocketFactory(keystorePath, keystorePassword.toCharArray());
                this.nanoHTTPDWebserver.makeSecure(socketFactory, null);
            } else {
                Log.d(this.getClass().getName(), String.format("No SLL detected args: %d", args.length()));
            }

            this.nanoHTTPDWebserver.start();
        } catch (Exception e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            return;
        }

        String message = "Server is running on: " +
                this.nanoHTTPDWebserver.getHostname() + ":" +
                this.nanoHTTPDWebserver.getListeningPort();
        Log.d(this.getClass().getName(), message);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, message));
    }

    /**
     * Stops the server
     *
     * @param args
     * @param callbackContext
     */
    private void stop(JSONArray args, CallbackContext callbackContext) {
        if (this.nanoHTTPDWebserver != null) {
            this.nanoHTTPDWebserver.stop();
            this.nanoHTTPDWebserver = null;
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }

    /**
     * Will be called if the js context sends an response to the webserver
     *
     * @param args            {UUID: {...}}
     * @param callbackContext
     * @throws JSONException
     */
    private void sendResponse(JSONArray args, CallbackContext callbackContext) {
        Log.d(this.getClass().getName(), "Got sendResponse: " + args.toString());
        try {
            this.responses.put(args.getString(0), args.get(1));
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
        }
    }

    /**
     * Just register the onRequest and send no result. This is needed to save the callbackContext to
     * invoke it later
     *
     * @param args
     * @param callbackContext
     */
    private void onRequest(JSONArray args, CallbackContext callbackContext) {
        this.onRequestCallbackContext = callbackContext;
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.onRequestCallbackContext.sendPluginResult(pluginResult);
    }
}
