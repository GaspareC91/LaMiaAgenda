package it.studiodentista.offline.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Locale

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


    suspend fun exportDatabaseCsv(): String {
        val studies = db.studyDao().getAllIncludingInactive()
        val prestations = db.prestazioneDao().getAllForBackup()
        val agreements = db.compensationDao().getAllForBackup()
        val dayWorks = db.dayWorkDao().getAllForBackup()
        return buildString {
            appendLine("TABLE;studies")
            appendLine("COLUMNS;id;name;city;preferredDay;active")
            studies.forEach { appendCsvRow(this, "ROW", "studies", listOf(it.id.toString(), it.name, it.city, it.preferredDay.toString(), it.active.toString())) }
            appendLine("TABLE;prestations")
            appendLine("COLUMNS;id;date;studyId;patientName;procedure;priceCents;gainCents;notes")
            prestations.forEach { appendCsvRow(this, "ROW", "prestations", listOf(it.id.toString(), it.date, it.studyId.toString(), it.patientName, it.procedure, it.priceCents.toString(), it.gainCents.toString(), it.notes)) }
            appendLine("TABLE;compensation_agreements")
            appendLine("COLUMNS;id;studyId;validFrom;type;fixedCents;percentage")
            agreements.forEach { appendCsvRow(this, "ROW", "compensation_agreements", listOf(it.id.toString(), it.studyId.toString(), it.validFrom, it.type, it.fixedCents.toString(), it.percentage.toString())) }
            appendLine("TABLE;day_work")
            appendLine("COLUMNS;date;studyId;halfDay")
            dayWorks.forEach { appendCsvRow(this, "ROW", "day_work", listOf(it.date, it.studyId.toString(), it.halfDay.toString())) }
        }
    }

    suspend fun importDatabaseCsv(csv: String) {
        val rows = parseBackupCsv(csv.removePrefix("\uFEFF"))
        val expected = linkedMapOf(
            "studies" to listOf("id", "name", "city", "preferredDay", "active"),
            "prestations" to listOf("id", "date", "studyId", "patientName", "procedure", "priceCents", "gainCents", "notes"),
            "compensation_agreements" to listOf("id", "studyId", "validFrom", "type", "fixedCents", "percentage"),
            "day_work" to listOf("date", "studyId", "halfDay")
        )
        val parsed = linkedMapOf<String, MutableList<List<String>>>()
        var table: String? = null
        var columns: List<String>? = null
        for (row in rows) {
            if (row.isEmpty()) continue
            when (row[0]) {
                "TABLE" -> {
                    require(row.size == 2 && expected.containsKey(row[1])) { "Struttura CSV non valida." }
                    table = row[1]; columns = null; parsed.getOrPut(row[1]) { mutableListOf() }
                }
                "COLUMNS" -> {
                    val t = table ?: throw IllegalArgumentException("Struttura CSV non valida.")
                    require(row.drop(1) == expected[t]) { "Colonne non valide per la tabella $t." }
                    columns = row.drop(1)
                }
                "ROW" -> {
                    val t = table ?: throw IllegalArgumentException("Struttura CSV non valida.")
                    require(columns == expected[t]) { "Intestazione mancante o non valida per la tabella $t." }
                    require(row.size == expected[t]!!.size + 2 && row[1] == t) { "Riga non valida per la tabella $t." }
                    parsed.getOrPut(t) { mutableListOf() }.add(row.drop(2))
                }
                else -> throw IllegalArgumentException("Formato CSV non riconosciuto.")
            }
        }
        require(expected.keys.all { parsed.containsKey(it) }) { "Il file non contiene tutte le tabelle del database." }

        val studies = parsed["studies"]!!.map { StudyEntity(it[0].toLong(), it[1], it[2], it[3].toInt(), it[4].toBooleanStrict()) }
        val prestations = parsed["prestations"]!!.map { PrestazioneEntity(it[0].toLong(), it[1], it[2].toLong(), it[3], it[4], it[5].toLong(), it[6].toLong(), it[7]) }
        val agreements = parsed["compensation_agreements"]!!.map { CompensationAgreementEntity(it[0].toLong(), it[1].toLong(), it[2], it[3], it[4].toLong(), it[5].toDouble()) }
        val dayWorks = parsed["day_work"]!!.map { DayWorkEntity(it[0], it[1].toLong(), it[2].toBooleanStrict()) }
        val studyIds = studies.map { it.id }.toSet()
        require(prestations.all { it.studyId in studyIds } && agreements.all { it.studyId in studyIds } && dayWorks.all { it.studyId in studyIds }) { "Il file contiene riferimenti a studi inesistenti." }

        db.withTransaction {
            db.dayWorkDao().clearAll()
            db.prestazioneDao().clearAll()
            db.compensationDao().clearAll()
            db.studyDao().clearAll()
            studies.forEach { db.studyDao().insert(it) }
            prestations.forEach { db.prestazioneDao().insert(it) }
            agreements.forEach { db.compensationDao().insert(it) }
            dayWorks.forEach { db.dayWorkDao().upsert(it) }
        }
    }

    private fun appendCsvRow(out: StringBuilder, marker: String, table: String, values: List<String>) {
        out.append(marker).append(';').append(table)
        values.forEach { value -> out.append(';').append(csvEscape(value)) }
        out.append('\n')
    }

    private fun csvEscape(value: String): String = if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    private fun parseBackupCsv(csv: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun finishCell() { row.add(cell.toString()); cell.clear() }
        fun finishRow() { finishCell(); if (row.any { it.isNotEmpty() }) result.add(row.toList()); row.clear() }
        while (i < csv.length) {
            val c = csv[i]
            when {
                c == '"' && quoted && i + 1 < csv.length && csv[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> finishCell()
                (c == '\n' || c == '\r') && !quoted -> { if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++; finishRow() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        return result
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
