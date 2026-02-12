package com.mason.bricktracking.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mason.bricktracking.data.local.AppDatabase;
import com.mason.bricktracking.data.local.BrickPlacementDao;
import com.mason.bricktracking.data.model.BrickPlacement;
import com.mason.bricktracking.data.remote.ApiClient;
import com.mason.bricktracking.data.remote.ApiService;
import com.mason.bricktracking.data.remote.SyncRequest;
import com.mason.bricktracking.data.remote.SyncResponse;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final int SYNC_THRESHOLD = 1; // Sync immediately after each scan
    
    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 2000; // 2 seconds
    private static final long MAX_RETRY_DELAY_MS = 60000; // 1 minute max
    
    private Context context;
    private BrickPlacementDao dao;
    private ApiService apiService;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SyncListener syncListener;
    private int unsyncedCount = 0;
    private boolean isSyncing = false;
    
    // Retry state
    private int retryAttempts = 0;
    private boolean retryScheduled = false;
    private Runnable retryRunnable;
    
    public interface SyncListener {
        void onSyncStarted();
        void onSyncSuccess(int lastPlacementNumber, int palletCount, int placementCount);
        void onSyncFailed(String error);
        void onSyncRetrying(int attempt, long delayMs);
        void onCounterUpdated(int unsyncedCount);
    }
    
    public SyncManager(Context context) {
        this.context = context;
        this.apiService = ApiClient.getApiService();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        // DAO will be initialized lazily on background thread when first needed
    }
    
    private BrickPlacementDao getDao() {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).brickPlacementDao();
        }
        return dao;
    }
    
    public void setSyncListener(SyncListener listener) {
        this.syncListener = listener;
    }
    
    public void addPlacement(BrickPlacement placement) {
        executorService.execute(() -> {
            getDao().insert(placement);
            unsyncedCount = getDao().getUnsyncedCount();
            
            mainHandler.post(() -> {
                if (syncListener != null) {
                    syncListener.onCounterUpdated(unsyncedCount);
                }
            });
            
            // Attempt sync if threshold reached or exceeded
            if (unsyncedCount >= SYNC_THRESHOLD) {
                attemptSync();
            }
        });
    }
    
    public void attemptSync() {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping");
            return;
        }
        
        executorService.execute(() -> {
            List<BrickPlacement> unsyncedPlacements = getDao().getUnsyncedPlacements();
            
            if (unsyncedPlacements.isEmpty()) {
                Log.d(TAG, "No placements to sync");
                return;
            }
            
            Log.d(TAG, "Starting sync for " + unsyncedPlacements.size() + " placements");
            
            isSyncing = true;
            mainHandler.post(() -> {
                if (syncListener != null) {
                    syncListener.onSyncStarted();
                }
            });
            
            // Prepare sync request
            String masonId = unsyncedPlacements.get(0).getMasonId();
            SyncRequest.PlacementData[] placementData = new SyncRequest.PlacementData[unsyncedPlacements.size()];
            
            for (int i = 0; i < unsyncedPlacements.size(); i++) {
                BrickPlacement placement = unsyncedPlacements.get(i);
                placementData[i] = new SyncRequest.PlacementData(
                    placement.getBrickNumber(),
                    placement.getTimestamp(),
                    placement.getLatitude(),
                    placement.getLongitude(),
                    placement.getAltitude(),
                    placement.getAccuracy(),
                    placement.getBuildSessionId(),
                    placement.getEventSeq(),
                    placement.getRssiAvg(),
                    placement.getRssiPeak(),
                    placement.getReadsInWindow(),
                    placement.getPowerLevel(),
                    placement.getDecisionStatus(),
                    placement.getScanType() != null ? placement.getScanType() : "placement"
                );
            }
            
            SyncRequest request = new SyncRequest(masonId, placementData);
            
            // Check if API service is available
            if (apiService == null) {
                isSyncing = false;
                Log.e(TAG, "API service not initialized");
                mainHandler.post(() -> {
                    if (syncListener != null) {
                        syncListener.onSyncFailed("Backend API not configured");
                    }
                });
                return;
            }
            
            // Make API call
            Call<SyncResponse> call = apiService.syncPlacements(request);
            call.enqueue(new Callback<SyncResponse>() {
                @Override
                public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                    isSyncing = false;
                    
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        SyncResponse syncResponse = response.body();
                        
                        // Mark placements as synced
                        executorService.execute(() -> {
                            for (BrickPlacement placement : unsyncedPlacements) {
                                placement.setSynced(true);
                                getDao().update(placement);
                            }
                            
                            // Clear synced placements from local cache
                            getDao().deleteSyncedPlacements();
                            unsyncedCount = getDao().getUnsyncedCount();
                            
                            mainHandler.post(() -> {
                                if (syncListener != null) {
                                    syncListener.onSyncSuccess(
                                        syncResponse.getLastPlacementNumber(),
                                        syncResponse.getPalletCount(),
                                        syncResponse.getPlacementCount()
                                    );
                                    syncListener.onCounterUpdated(unsyncedCount);
                                }
                            });
                        });
                        
                        // Reset retry counter on success
                        retryAttempts = 0;
                        cancelPendingRetry();
                        Log.d(TAG, "Sync successful");
                    } else {
                        String error = "Sync failed: " + (response.body() != null ? response.body().getMessage() : "Unknown error");
                        Log.e(TAG, error);
                        
                        // Server responded but with error - don't retry (data issue, not network)
                        mainHandler.post(() -> {
                            if (syncListener != null) {
                                syncListener.onSyncFailed(error);
                            }
                        });
                    }
                }
                
                @Override
                public void onFailure(Call<SyncResponse> call, Throwable t) {
                    isSyncing = false;
                    Log.e(TAG, "Sync failed (network)", t);
                    
                    // Network failure - schedule retry with exponential backoff
                    scheduleRetry();
                    
                    mainHandler.post(() -> {
                        if (syncListener != null) {
                            syncListener.onSyncFailed(t.getMessage());
                        }
                    });
                }
            });
        });
    }
    
    /**
     * Schedule a retry with exponential backoff
     */
    private void scheduleRetry() {
        if (retryScheduled || retryAttempts >= MAX_RETRY_ATTEMPTS) {
            if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Max retry attempts reached (" + MAX_RETRY_ATTEMPTS + "), waiting for network restore");
            }
            return;
        }
        
        retryAttempts++;
        long delay = Math.min(INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retryAttempts - 1), MAX_RETRY_DELAY_MS);
        
        Log.d(TAG, "Scheduling retry attempt " + retryAttempts + "/" + MAX_RETRY_ATTEMPTS + " in " + delay + "ms");
        
        mainHandler.post(() -> {
            if (syncListener != null) {
                syncListener.onSyncRetrying(retryAttempts, delay);
            }
        });
        
        retryRunnable = () -> {
            retryScheduled = false;
            Log.d(TAG, "Executing retry attempt " + retryAttempts);
            attemptSync();
        };
        
        retryScheduled = true;
        mainHandler.postDelayed(retryRunnable, delay);
    }
    
    /**
     * Cancel any pending retry
     */
    private void cancelPendingRetry() {
        if (retryRunnable != null && retryScheduled) {
            mainHandler.removeCallbacks(retryRunnable);
            retryScheduled = false;
            Log.d(TAG, "Cancelled pending retry");
        }
    }
    
    /**
     * Called when network connection is restored.
     * Resets retry counter and immediately attempts sync if there are unsynced placements.
     */
    public void onNetworkRestored() {
        Log.d(TAG, "Network restored, resetting retry counter");
        retryAttempts = 0;
        cancelPendingRetry();
        
        // Check if we have unsynced placements and attempt sync
        executorService.execute(() -> {
            int count = getDao().getUnsyncedCount();
            if (count > 0) {
                Log.d(TAG, "Network restored with " + count + " unsynced placements, attempting sync");
                attemptSync();
            }
        });
    }
    
    /**
     * Force an immediate sync attempt, resetting retry state
     */
    public void forceSyncNow() {
        retryAttempts = 0;
        cancelPendingRetry();
        attemptSync();
    }
    
    public void fetchLastPlacementNumber(String masonId, FetchListener listener) {
        // Check if API service is available
        if (apiService == null) {
            Log.e(TAG, "API service not initialized");
            mainHandler.post(() -> listener.onFetchFailed("Backend API not configured"));
            return;
        }
        
        Call<SyncResponse> call = apiService.getLastPlacementNumber(masonId);
        call.enqueue(new Callback<SyncResponse>() {
            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    int lastNumber = response.body().getLastPlacementNumber();
                    mainHandler.post(() -> listener.onFetchSuccess(lastNumber));
                } else {
                    mainHandler.post(() -> listener.onFetchFailed("Failed to fetch last placement number"));
                }
            }
            
            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                mainHandler.post(() -> listener.onFetchFailed(t.getMessage()));
            }
        });
    }
    
    public interface FetchListener {
        void onFetchSuccess(int lastPlacementNumber);
        void onFetchFailed(String error);
    }
    
    public int getUnsyncedCount() {
        return unsyncedCount;
    }
    
    public void clearUnsyncedPlacements() {
        executorService.execute(() -> {
            getDao().deleteAll();
            unsyncedCount = 0;
            
            mainHandler.post(() -> {
                if (syncListener != null) {
                    syncListener.onCounterUpdated(0);
                }
            });
        });
    }
    
    public void shutdown() {
        cancelPendingRetry();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
