package com.mason.bricktracking.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mason.bricktracking.data.model.BrickPlacement;

import java.util.List;

@Dao
public interface BrickPlacementDao {
    
    @Insert
    long insert(BrickPlacement placement);
    
    @Update
    void update(BrickPlacement placement);
    
    @Query("SELECT * FROM brick_placements WHERE synced = 0 ORDER BY timestamp ASC")
    List<BrickPlacement> getUnsyncedPlacements();
    
    @Query("SELECT * FROM brick_placements WHERE masonId = :masonId ORDER BY timestamp DESC")
    List<BrickPlacement> getPlacementsByMason(String masonId);
    
    @Query("SELECT COUNT(*) FROM brick_placements WHERE synced = 0")
    int getUnsyncedCount();
    
    @Query("DELETE FROM brick_placements WHERE synced = 1")
    void deleteSyncedPlacements();
    
    @Query("DELETE FROM brick_placements")
    void deleteAll();
    
    @Query("SELECT * FROM brick_placements WHERE masonId = :masonId AND synced = 0 ORDER BY timestamp ASC")
    List<BrickPlacement> getUnsyncedPlacementsByMason(String masonId);
}
