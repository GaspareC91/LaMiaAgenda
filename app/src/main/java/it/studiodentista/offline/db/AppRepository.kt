package it.studiodentista.offline.db

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class AppRepository(private val db: AppDatabase) {
    val studies: Flow<List<StudyEntity>> = db.studyDao().observeAll()
    val prestations: Flow<List<PrestazioneEntity>> = db.prestazioneDao().observeAll()
    val dayWorks: Flow<List<DayWorkEntity>> = db.dayWorkDao().observeAll()
    val agreements: Flow<List<CompensationAgreementEntity>> = db.compensationDao().observeAll()

    suspend fun seedStudiesIfEmpty() {
        // No demo studies are created in the production-style build.
    }

    suspend fun addPrestazione(date: String, studyId: Long, patientName: String, procedure: String, priceCents: Long, gainCents: Long, notes: String) {
        db.prestazioneDao().insert(PrestazioneEntity(date=date, studyId=studyId, patientName=patientName.trim(), procedure=procedure.trim(), priceCents=priceCents, gainCents=gainCents, notes=notes.trim()))
    }

    suspend fun updatePrestazione(item: PrestazioneEntity, date: String, studyId: Long, patientName: String, procedure: String, priceCents: Long, gainCents: Long, notes: String) {
        db.prestazioneDao().update(item.copy(date=date, studyId=studyId, patientName=patientName.trim(), procedure=procedure.trim(), priceCents=priceCents, gainCents=gainCents, notes=notes.trim()))
    }

    suspend fun deletePrestazione(id: Long) = db.prestazioneDao().delete(id)

    suspend fun saveStudy(id: Long?, name: String, city: String, preferredDay: Int, type: String, fixedCents: Long, percentage: Double, validFrom: String) {
        val studyId = if (id == null) db.studyDao().insert(StudyEntity(name=name.trim(), city=city.trim(), preferredDay=preferredDay)) else {
            val existing = db.studyDao().get(id) ?: return
            db.studyDao().update(existing.copy(name=name.trim(), city=city.trim(), preferredDay=preferredDay, active=true)); id
        }
        val existingAgreements = db.compensationDao().forStudy(studyId)
        val latest = existingAgreements.firstOrNull()
        if (latest == null || latest.type != type || latest.fixedCents != fixedCents || kotlin.math.abs(latest.percentage - percentage) > 0.0001 || latest.validFrom != validFrom) {
            db.compensationDao().insert(CompensationAgreementEntity(studyId=studyId, validFrom=validFrom, type=type, fixedCents=fixedCents, percentage=percentage))
        }
    }

    suspend fun studyAgreement(studyId: Long, date: String): CompensationAgreementEntity? = db.compensationDao().effective(studyId, date)
    suspend fun studyAgreements(studyId: Long): List<CompensationAgreementEntity> = db.compensationDao().forStudy(studyId)
    suspend fun archiveStudy(id: Long) { db.studyDao().get(id)?.let { db.studyDao().update(it.copy(active=false)) } }
    suspend fun toggleHalfDay(date: String, studyId: Long, halfDay: Boolean) {
        if (halfDay) db.dayWorkDao().upsert(DayWorkEntity(date, studyId, true)) else db.dayWorkDao().delete(date, studyId)
    }
    suspend fun halfDay(date: String, studyId: Long): Boolean = db.dayWorkDao().forDate(date).firstOrNull { it.studyId == studyId }?.halfDay == true
    suspend fun filteredPrestations(from: String, to: String, studyId: Long?): List<PrestazioneEntity> = db.prestazioneDao().getFiltered(from, to, studyId)
}
