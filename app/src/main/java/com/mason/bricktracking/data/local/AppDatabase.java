package com.mason.bricktracking.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.mason.bricktracking.data.model.BrickPlacement;

@Database(entities = {BrickPlacement.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;
    
    public abstract BrickPlacementDao brickPlacementDao();
    
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "mason_brick_tracking.db"
            )
            .fallbackToDestructiveMigration()  // For development: clears old data on schema change
            .build();
        }
        return instance;
    }
}
