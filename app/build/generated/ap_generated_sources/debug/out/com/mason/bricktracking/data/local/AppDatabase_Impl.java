package com.mason.bricktracking.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile BrickPlacementDao _brickPlacementDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `brick_placements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `masonId` TEXT, `brickNumber` TEXT, `timestamp` INTEGER NOT NULL, `synced` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `buildSessionId` TEXT, `eventSeq` INTEGER NOT NULL, `rssiAvg` INTEGER NOT NULL, `rssiPeak` INTEGER NOT NULL, `readsInWindow` INTEGER NOT NULL, `powerLevel` INTEGER NOT NULL, `decisionStatus` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '448eb88d90c1c79a86cd16eda776bf34')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `brick_placements`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsBrickPlacements = new HashMap<String, TableInfo.Column>(16);
        _columnsBrickPlacements.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("masonId", new TableInfo.Column("masonId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("brickNumber", new TableInfo.Column("brickNumber", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("synced", new TableInfo.Column("synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("latitude", new TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("longitude", new TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("altitude", new TableInfo.Column("altitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("accuracy", new TableInfo.Column("accuracy", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("buildSessionId", new TableInfo.Column("buildSessionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("eventSeq", new TableInfo.Column("eventSeq", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("rssiAvg", new TableInfo.Column("rssiAvg", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("rssiPeak", new TableInfo.Column("rssiPeak", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("readsInWindow", new TableInfo.Column("readsInWindow", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("powerLevel", new TableInfo.Column("powerLevel", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBrickPlacements.put("decisionStatus", new TableInfo.Column("decisionStatus", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBrickPlacements = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBrickPlacements = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBrickPlacements = new TableInfo("brick_placements", _columnsBrickPlacements, _foreignKeysBrickPlacements, _indicesBrickPlacements);
        final TableInfo _existingBrickPlacements = TableInfo.read(db, "brick_placements");
        if (!_infoBrickPlacements.equals(_existingBrickPlacements)) {
          return new RoomOpenHelper.ValidationResult(false, "brick_placements(com.mason.bricktracking.data.model.BrickPlacement).\n"
                  + " Expected:\n" + _infoBrickPlacements + "\n"
                  + " Found:\n" + _existingBrickPlacements);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "448eb88d90c1c79a86cd16eda776bf34", "1747409136f8807f193990a928c15bbc");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "brick_placements");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `brick_placements`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(BrickPlacementDao.class, BrickPlacementDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public BrickPlacementDao brickPlacementDao() {
    if (_brickPlacementDao != null) {
      return _brickPlacementDao;
    } else {
      synchronized(this) {
        if(_brickPlacementDao == null) {
          _brickPlacementDao = new BrickPlacementDao_Impl(this);
        }
        return _brickPlacementDao;
      }
    }
  }
}
