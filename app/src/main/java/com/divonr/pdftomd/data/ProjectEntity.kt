package com.divonr.pdftomd.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pdfUriString: String, // Persisted as a string, likely a file path in internal storage
    val markdownContent: String,
    val lastModified: Long = System.currentTimeMillis()
)
