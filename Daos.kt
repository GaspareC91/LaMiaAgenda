package it.studiodentista.offline.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM studies WHERE active = 1 ORDER BY name") fun observeAll(): Flow<List<StudyEntity>>
    @Query("SELECT * FROM studies ORDER BY name") suspend fun getAllIncludingInactive(): List<StudyEntity>
    @Query("SELECT * FROM studies WHERE id = :id LIMIT 1") suspend fun get(id: Long): StudyEntity?
    @Query("SELECT * FROM studies ORDER BY id") suspend fun getAllForBackup(): List<StudyEntity>
    @Query("DELETE FROM studies") suspend fun clearAll()
    @Insert suspend fun insert(study: StudyEntity): Long
    @Update suspend fun update(study: StudyEntity)
    @Query("SELECT COUNT(*) FROM studies") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM prestations WHERE studyId = :studyId") suspend fun prestationCount(studyId: Long): Int
}

@Dao
interface PrestazioneDao {
    @Query("SELECT * FROM prestations ORDER BY date DESC, id DESC") fun observeAll(): Flow<List<PrestazioneEntity>>
    @Query("SELECT * FROM prestations WHERE date = :date ORDER BY studyId, id") fun observeForDate(date: String): Flow<List<PrestazioneEntity>>
    @Query("SELECT * FROM prestations WHERE id = :id LIMIT 1") suspend fun get(id: Long): PrestazioneEntity?
    @Query("SELECT * FROM prestations WHERE date >= :from AND date <= :to AND (:studyId IS NULL OR studyId = :studyId) ORDER BY date DESC, id DESC") suspend fun getFiltered(from: String, to: String, studyId: Long?): List<PrestazioneEntity>
    @Query("SELECT * FROM prestations ORDER BY id") suspend fun getAllForBackup(): List<PrestazioneEntity>
    @Query("DELETE FROM prestations") suspend fun clearAll()
    @Insert suspend fun insert(item: PrestazioneEntity): Long
    @Update suspend fun update(item: PrestazioneEntity)
    @Query("DELETE FROM prestations WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface CompensationDao {
    @Query("SELECT * FROM compensation_agreements ORDER BY studyId, validFrom DESC, id DESC") fun observeAll(): Flow<List<CompensationAgreementEntity>>
    @Query("SELECT * FROM compensation_agreements WHERE studyId = :studyId ORDER BY validFrom DESC, id DESC") suspend fun forStudy(studyId: Long): List<CompensationAgreementEntity>
    @Query("SELECT * FROM compensation_agreements ORDER BY id") suspend fun getAllForBackup(): List<CompensationAgreementEntity>
    @Query("DELETE FROM compensation_agreements") suspend fun clearAll()
    @Insert suspend fun insert(item: CompensationAgreementEntity): Long
    @Query("SELECT * FROM compensation_agreements WHERE studyId = :studyId AND validFrom <= :date ORDER BY validFrom DESC, id DESC LIMIT 1") suspend fun effective(studyId: Long, date: String): CompensationAgreementEntity?
}

@Dao
interface DayWorkDao {
    @Query("SELECT * FROM day_work") fun observeAll(): Flow<List<DayWorkEntity>>
    @Query("SELECT * FROM day_work WHERE date = :date") suspend fun forDate(date: String): List<DayWorkEntity>
    @Query("SELECT * FROM day_work ORDER BY date, studyId") suspend fun getAllForBackup(): List<DayWorkEntity>
    @Query("DELETE FROM day_work") suspend fun clearAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: DayWorkEntity)
    @Query("DELETE FROM day_work WHERE date = :date AND studyId = :studyId") suspend fun delete(date: String, studyId: Long)
}
