package com.finale.nextgen.offline;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for queued presence updates (QR scans) stored locally when offline.
 */
@Entity(tableName = "pending_presences",
        indices = {@Index(value = {"examId", "studentId"})})
public class PendingPresence {
    @PrimaryKey
    @NonNull
    public String id; // UUID

    public String examId;
    public String studentId;
    public long timestamp; // when scanned locally
    public String status; // "PENDING", "SYNCING", "FAILED"
}