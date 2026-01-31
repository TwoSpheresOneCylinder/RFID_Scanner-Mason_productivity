package com.mason.bricktracking.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.mason.bricktracking.data.model.BrickPlacement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"unchecked", "deprecation"})
public final class BrickPlacementDao_Impl implements BrickPlacementDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BrickPlacement> __insertionAdapterOfBrickPlacement;

  private final EntityDeletionOrUpdateAdapter<BrickPlacement> __updateAdapterOfBrickPlacement;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSyncedPlacements;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public BrickPlacementDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBrickPlacement = new EntityInsertionAdapter<BrickPlacement>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `brick_placements` (`id`,`masonId`,`brickNumber`,`timestamp`,`synced`,`latitude`,`longitude`,`altitude`,`accuracy`,`buildSessionId`,`eventSeq`,`rssiAvg`,`rssiPeak`,`readsInWindow`,`powerLevel`,`decisionStatus`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final BrickPlacement entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getMasonId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getMasonId());
        }
        if (entity.getBrickNumber() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getBrickNumber());
        }
        statement.bindLong(4, entity.getTimestamp());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindDouble(6, entity.getLatitude());
        statement.bindDouble(7, entity.getLongitude());
        statement.bindDouble(8, entity.getAltitude());
        statement.bindDouble(9, entity.getAccuracy());
        if (entity.getBuildSessionId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getBuildSessionId());
        }
        statement.bindLong(11, entity.getEventSeq());
        statement.bindLong(12, entity.getRssiAvg());
        statement.bindLong(13, entity.getRssiPeak());
        statement.bindLong(14, entity.getReadsInWindow());
        statement.bindLong(15, entity.getPowerLevel());
        if (entity.getDecisionStatus() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getDecisionStatus());
        }
      }
    };
    this.__updateAdapterOfBrickPlacement = new EntityDeletionOrUpdateAdapter<BrickPlacement>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `brick_placements` SET `id` = ?,`masonId` = ?,`brickNumber` = ?,`timestamp` = ?,`synced` = ?,`latitude` = ?,`longitude` = ?,`altitude` = ?,`accuracy` = ?,`buildSessionId` = ?,`eventSeq` = ?,`rssiAvg` = ?,`rssiPeak` = ?,`readsInWindow` = ?,`powerLevel` = ?,`decisionStatus` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final BrickPlacement entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getMasonId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getMasonId());
        }
        if (entity.getBrickNumber() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getBrickNumber());
        }
        statement.bindLong(4, entity.getTimestamp());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindDouble(6, entity.getLatitude());
        statement.bindDouble(7, entity.getLongitude());
        statement.bindDouble(8, entity.getAltitude());
        statement.bindDouble(9, entity.getAccuracy());
        if (entity.getBuildSessionId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getBuildSessionId());
        }
        statement.bindLong(11, entity.getEventSeq());
        statement.bindLong(12, entity.getRssiAvg());
        statement.bindLong(13, entity.getRssiPeak());
        statement.bindLong(14, entity.getReadsInWindow());
        statement.bindLong(15, entity.getPowerLevel());
        if (entity.getDecisionStatus() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getDecisionStatus());
        }
        statement.bindLong(17, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteSyncedPlacements = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM brick_placements WHERE synced = 1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM brick_placements";
        return _query;
      }
    };
  }

  @Override
  public long insert(final BrickPlacement placement) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfBrickPlacement.insertAndReturnId(placement);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final BrickPlacement placement) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfBrickPlacement.handle(placement);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteSyncedPlacements() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSyncedPlacements.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteSyncedPlacements.release(_stmt);
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public List<BrickPlacement> getUnsyncedPlacements() {
    final String _sql = "SELECT * FROM brick_placements WHERE synced = 0 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMasonId = CursorUtil.getColumnIndexOrThrow(_cursor, "masonId");
      final int _cursorIndexOfBrickNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "brickNumber");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
      final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
      final int _cursorIndexOfAltitude = CursorUtil.getColumnIndexOrThrow(_cursor, "altitude");
      final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
      final int _cursorIndexOfBuildSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "buildSessionId");
      final int _cursorIndexOfEventSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "eventSeq");
      final int _cursorIndexOfRssiAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiAvg");
      final int _cursorIndexOfRssiPeak = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiPeak");
      final int _cursorIndexOfReadsInWindow = CursorUtil.getColumnIndexOrThrow(_cursor, "readsInWindow");
      final int _cursorIndexOfPowerLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "powerLevel");
      final int _cursorIndexOfDecisionStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "decisionStatus");
      final List<BrickPlacement> _result = new ArrayList<BrickPlacement>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final BrickPlacement _item;
        _item = new BrickPlacement();
        final int _tmpId;
        _tmpId = _cursor.getInt(_cursorIndexOfId);
        _item.setId(_tmpId);
        final String _tmpMasonId;
        if (_cursor.isNull(_cursorIndexOfMasonId)) {
          _tmpMasonId = null;
        } else {
          _tmpMasonId = _cursor.getString(_cursorIndexOfMasonId);
        }
        _item.setMasonId(_tmpMasonId);
        final String _tmpBrickNumber;
        if (_cursor.isNull(_cursorIndexOfBrickNumber)) {
          _tmpBrickNumber = null;
        } else {
          _tmpBrickNumber = _cursor.getString(_cursorIndexOfBrickNumber);
        }
        _item.setBrickNumber(_tmpBrickNumber);
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.setTimestamp(_tmpTimestamp);
        final boolean _tmpSynced;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _tmpSynced = _tmp != 0;
        _item.setSynced(_tmpSynced);
        final double _tmpLatitude;
        _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
        _item.setLatitude(_tmpLatitude);
        final double _tmpLongitude;
        _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
        _item.setLongitude(_tmpLongitude);
        final double _tmpAltitude;
        _tmpAltitude = _cursor.getDouble(_cursorIndexOfAltitude);
        _item.setAltitude(_tmpAltitude);
        final double _tmpAccuracy;
        _tmpAccuracy = _cursor.getDouble(_cursorIndexOfAccuracy);
        _item.setAccuracy(_tmpAccuracy);
        final String _tmpBuildSessionId;
        if (_cursor.isNull(_cursorIndexOfBuildSessionId)) {
          _tmpBuildSessionId = null;
        } else {
          _tmpBuildSessionId = _cursor.getString(_cursorIndexOfBuildSessionId);
        }
        _item.setBuildSessionId(_tmpBuildSessionId);
        final int _tmpEventSeq;
        _tmpEventSeq = _cursor.getInt(_cursorIndexOfEventSeq);
        _item.setEventSeq(_tmpEventSeq);
        final int _tmpRssiAvg;
        _tmpRssiAvg = _cursor.getInt(_cursorIndexOfRssiAvg);
        _item.setRssiAvg(_tmpRssiAvg);
        final int _tmpRssiPeak;
        _tmpRssiPeak = _cursor.getInt(_cursorIndexOfRssiPeak);
        _item.setRssiPeak(_tmpRssiPeak);
        final int _tmpReadsInWindow;
        _tmpReadsInWindow = _cursor.getInt(_cursorIndexOfReadsInWindow);
        _item.setReadsInWindow(_tmpReadsInWindow);
        final int _tmpPowerLevel;
        _tmpPowerLevel = _cursor.getInt(_cursorIndexOfPowerLevel);
        _item.setPowerLevel(_tmpPowerLevel);
        final String _tmpDecisionStatus;
        if (_cursor.isNull(_cursorIndexOfDecisionStatus)) {
          _tmpDecisionStatus = null;
        } else {
          _tmpDecisionStatus = _cursor.getString(_cursorIndexOfDecisionStatus);
        }
        _item.setDecisionStatus(_tmpDecisionStatus);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<BrickPlacement> getPlacementsByMason(final String masonId) {
    final String _sql = "SELECT * FROM brick_placements WHERE masonId = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (masonId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, masonId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMasonId = CursorUtil.getColumnIndexOrThrow(_cursor, "masonId");
      final int _cursorIndexOfBrickNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "brickNumber");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
      final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
      final int _cursorIndexOfAltitude = CursorUtil.getColumnIndexOrThrow(_cursor, "altitude");
      final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
      final int _cursorIndexOfBuildSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "buildSessionId");
      final int _cursorIndexOfEventSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "eventSeq");
      final int _cursorIndexOfRssiAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiAvg");
      final int _cursorIndexOfRssiPeak = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiPeak");
      final int _cursorIndexOfReadsInWindow = CursorUtil.getColumnIndexOrThrow(_cursor, "readsInWindow");
      final int _cursorIndexOfPowerLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "powerLevel");
      final int _cursorIndexOfDecisionStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "decisionStatus");
      final List<BrickPlacement> _result = new ArrayList<BrickPlacement>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final BrickPlacement _item;
        _item = new BrickPlacement();
        final int _tmpId;
        _tmpId = _cursor.getInt(_cursorIndexOfId);
        _item.setId(_tmpId);
        final String _tmpMasonId;
        if (_cursor.isNull(_cursorIndexOfMasonId)) {
          _tmpMasonId = null;
        } else {
          _tmpMasonId = _cursor.getString(_cursorIndexOfMasonId);
        }
        _item.setMasonId(_tmpMasonId);
        final String _tmpBrickNumber;
        if (_cursor.isNull(_cursorIndexOfBrickNumber)) {
          _tmpBrickNumber = null;
        } else {
          _tmpBrickNumber = _cursor.getString(_cursorIndexOfBrickNumber);
        }
        _item.setBrickNumber(_tmpBrickNumber);
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.setTimestamp(_tmpTimestamp);
        final boolean _tmpSynced;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _tmpSynced = _tmp != 0;
        _item.setSynced(_tmpSynced);
        final double _tmpLatitude;
        _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
        _item.setLatitude(_tmpLatitude);
        final double _tmpLongitude;
        _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
        _item.setLongitude(_tmpLongitude);
        final double _tmpAltitude;
        _tmpAltitude = _cursor.getDouble(_cursorIndexOfAltitude);
        _item.setAltitude(_tmpAltitude);
        final double _tmpAccuracy;
        _tmpAccuracy = _cursor.getDouble(_cursorIndexOfAccuracy);
        _item.setAccuracy(_tmpAccuracy);
        final String _tmpBuildSessionId;
        if (_cursor.isNull(_cursorIndexOfBuildSessionId)) {
          _tmpBuildSessionId = null;
        } else {
          _tmpBuildSessionId = _cursor.getString(_cursorIndexOfBuildSessionId);
        }
        _item.setBuildSessionId(_tmpBuildSessionId);
        final int _tmpEventSeq;
        _tmpEventSeq = _cursor.getInt(_cursorIndexOfEventSeq);
        _item.setEventSeq(_tmpEventSeq);
        final int _tmpRssiAvg;
        _tmpRssiAvg = _cursor.getInt(_cursorIndexOfRssiAvg);
        _item.setRssiAvg(_tmpRssiAvg);
        final int _tmpRssiPeak;
        _tmpRssiPeak = _cursor.getInt(_cursorIndexOfRssiPeak);
        _item.setRssiPeak(_tmpRssiPeak);
        final int _tmpReadsInWindow;
        _tmpReadsInWindow = _cursor.getInt(_cursorIndexOfReadsInWindow);
        _item.setReadsInWindow(_tmpReadsInWindow);
        final int _tmpPowerLevel;
        _tmpPowerLevel = _cursor.getInt(_cursorIndexOfPowerLevel);
        _item.setPowerLevel(_tmpPowerLevel);
        final String _tmpDecisionStatus;
        if (_cursor.isNull(_cursorIndexOfDecisionStatus)) {
          _tmpDecisionStatus = null;
        } else {
          _tmpDecisionStatus = _cursor.getString(_cursorIndexOfDecisionStatus);
        }
        _item.setDecisionStatus(_tmpDecisionStatus);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getUnsyncedCount() {
    final String _sql = "SELECT COUNT(*) FROM brick_placements WHERE synced = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<BrickPlacement> getUnsyncedPlacementsByMason(final String masonId) {
    final String _sql = "SELECT * FROM brick_placements WHERE masonId = ? AND synced = 0 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (masonId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, masonId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfMasonId = CursorUtil.getColumnIndexOrThrow(_cursor, "masonId");
      final int _cursorIndexOfBrickNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "brickNumber");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
      final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
      final int _cursorIndexOfAltitude = CursorUtil.getColumnIndexOrThrow(_cursor, "altitude");
      final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
      final int _cursorIndexOfBuildSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "buildSessionId");
      final int _cursorIndexOfEventSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "eventSeq");
      final int _cursorIndexOfRssiAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiAvg");
      final int _cursorIndexOfRssiPeak = CursorUtil.getColumnIndexOrThrow(_cursor, "rssiPeak");
      final int _cursorIndexOfReadsInWindow = CursorUtil.getColumnIndexOrThrow(_cursor, "readsInWindow");
      final int _cursorIndexOfPowerLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "powerLevel");
      final int _cursorIndexOfDecisionStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "decisionStatus");
      final List<BrickPlacement> _result = new ArrayList<BrickPlacement>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final BrickPlacement _item;
        _item = new BrickPlacement();
        final int _tmpId;
        _tmpId = _cursor.getInt(_cursorIndexOfId);
        _item.setId(_tmpId);
        final String _tmpMasonId;
        if (_cursor.isNull(_cursorIndexOfMasonId)) {
          _tmpMasonId = null;
        } else {
          _tmpMasonId = _cursor.getString(_cursorIndexOfMasonId);
        }
        _item.setMasonId(_tmpMasonId);
        final String _tmpBrickNumber;
        if (_cursor.isNull(_cursorIndexOfBrickNumber)) {
          _tmpBrickNumber = null;
        } else {
          _tmpBrickNumber = _cursor.getString(_cursorIndexOfBrickNumber);
        }
        _item.setBrickNumber(_tmpBrickNumber);
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.setTimestamp(_tmpTimestamp);
        final boolean _tmpSynced;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _tmpSynced = _tmp != 0;
        _item.setSynced(_tmpSynced);
        final double _tmpLatitude;
        _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
        _item.setLatitude(_tmpLatitude);
        final double _tmpLongitude;
        _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
        _item.setLongitude(_tmpLongitude);
        final double _tmpAltitude;
        _tmpAltitude = _cursor.getDouble(_cursorIndexOfAltitude);
        _item.setAltitude(_tmpAltitude);
        final double _tmpAccuracy;
        _tmpAccuracy = _cursor.getDouble(_cursorIndexOfAccuracy);
        _item.setAccuracy(_tmpAccuracy);
        final String _tmpBuildSessionId;
        if (_cursor.isNull(_cursorIndexOfBuildSessionId)) {
          _tmpBuildSessionId = null;
        } else {
          _tmpBuildSessionId = _cursor.getString(_cursorIndexOfBuildSessionId);
        }
        _item.setBuildSessionId(_tmpBuildSessionId);
        final int _tmpEventSeq;
        _tmpEventSeq = _cursor.getInt(_cursorIndexOfEventSeq);
        _item.setEventSeq(_tmpEventSeq);
        final int _tmpRssiAvg;
        _tmpRssiAvg = _cursor.getInt(_cursorIndexOfRssiAvg);
        _item.setRssiAvg(_tmpRssiAvg);
        final int _tmpRssiPeak;
        _tmpRssiPeak = _cursor.getInt(_cursorIndexOfRssiPeak);
        _item.setRssiPeak(_tmpRssiPeak);
        final int _tmpReadsInWindow;
        _tmpReadsInWindow = _cursor.getInt(_cursorIndexOfReadsInWindow);
        _item.setReadsInWindow(_tmpReadsInWindow);
        final int _tmpPowerLevel;
        _tmpPowerLevel = _cursor.getInt(_cursorIndexOfPowerLevel);
        _item.setPowerLevel(_tmpPowerLevel);
        final String _tmpDecisionStatus;
        if (_cursor.isNull(_cursorIndexOfDecisionStatus)) {
          _tmpDecisionStatus = null;
        } else {
          _tmpDecisionStatus = _cursor.getString(_cursorIndexOfDecisionStatus);
        }
        _item.setDecisionStatus(_tmpDecisionStatus);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
