package com.mason.bricktracking.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitors network connectivity and notifies listeners when connection state changes.
 * Used for automatic sync retry when connection is restored.
 */
public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";
    
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private NetworkListener listener;
    private boolean isConnected = false;
    private boolean isRegistered = false;
    
    public interface NetworkListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }
    
    public NetworkMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.isConnected = checkCurrentConnectivity();
    }
    
    public void setNetworkListener(NetworkListener listener) {
        this.listener = listener;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Check current network connectivity status
     */
    public boolean checkCurrentConnectivity() {
        if (connectivityManager == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) return false;
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }
    
    /**
     * Start monitoring network connectivity changes
     */
    public void startMonitoring() {
        if (isRegistered || connectivityManager == null) {
            return;
        }
        
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available");
                boolean wasConnected = isConnected;
                isConnected = true;
                
                // Only notify if this is a transition from disconnected to connected
                if (!wasConnected && listener != null) {
                    listener.onNetworkAvailable();
                }
            }
            
            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "Network lost");
                // Check if we still have any connectivity
                isConnected = checkCurrentConnectivity();
                
                if (!isConnected && listener != null) {
                    listener.onNetworkLost();
                }
            }
            
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                     capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                
                if (hasInternet && !isConnected) {
                    Log.d(TAG, "Network capabilities restored");
                    isConnected = true;
                    if (listener != null) {
                        listener.onNetworkAvailable();
                    }
                } else if (!hasInternet && isConnected) {
                    Log.d(TAG, "Network capabilities lost");
                    isConnected = false;
                    if (listener != null) {
                        listener.onNetworkLost();
                    }
                }
            }
        };
        
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            isRegistered = true;
            Log.d(TAG, "Network monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }
    
    /**
     * Stop monitoring network connectivity changes
     */
    public void stopMonitoring() {
        if (!isRegistered || connectivityManager == null || networkCallback == null) {
            return;
        }
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            isRegistered = false;
            Log.d(TAG, "Network monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister network callback", e);
        }
    }
}
