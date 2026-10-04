package it.studiodentista.offline.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studies")
data class StudyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String = "",
    val preferredDay: Int = 1,
    val active: Boolean = true
)

@Entity(tableName = "prestations")
data class PrestazioneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val studyId: Long,
    val patientName: String,
    val procedure: String,
    val priceCents: Long,
    val gainCents: Long,
    val notes: String = ""
)

@Entity(tableName = "compensation_agreements")
data class CompensationAgreementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studyId: Long,
    val validFrom: String,
    val type: String,
    val fixedCents: Long = 0,
    val percentage: Double = 0.0
)

@Entity(tableName = "day_work", primaryKeys = ["date", "studyId"])
data class DayWorkEntity(
    val date: String,
    val studyId: Long,
    val halfDay: Boolean = false
)
