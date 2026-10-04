package it.studiodentista.offline

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.studiodentista.offline.db.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val IT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
private val DATE_INPUT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val IT_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private const val PERCENTAGE = "PERCENTAGE"
private const val FIXED = "FIXED"
private const val FIXED_PER_PRESTATION = "FIXED_PER_PRESTATION"
private val APP_EDGE_PADDING = 38.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AppRepository(AppDatabase.get(this))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DentalViewModel(repository) as T
        }
        val vm = ViewModelProvider(this, factory)[DentalViewModel::class.java]
        setContent { DentalApp(vm) }
    }
}

class DentalViewModel(private val repository: AppRepository) : ViewModel() {
    val studies = repository.studies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val prestations = repository.prestations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val agreements = repository.agreements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dayWorks = repository.dayWorks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { viewModelScope.launch { repository.seedStudiesIfEmpty() } }
    fun addPrestazione(date:String, studyId:Long, patient:String, procedure:String, price:Long, gain:Long, notes:String, done:()->Unit={}) = viewModelScope.launch { repository.addPrestazione(date,studyId,patient,procedure,price,gain,notes); done() }
    fun updatePrestazione(item:PrestazioneEntity,date:String,studyId:Long,patient:String,procedure:String,price:Long,gain:Long,notes:String,done:()->Unit={}) = viewModelScope.launch { repository.updatePrestazione(item,date,studyId,patient,procedure,price,gain,notes); done() }
    fun deletePrestazione(id:Long,done:()->Unit={}) = viewModelScope.launch { repository.deletePrestazione(id); done() }
    fun saveStudy(id:Long?,name:String,city:String,preferredDay:Int,type:String,fixed:Long,percentage:Double,validFrom:String,done:()->Unit={}) = viewModelScope.launch { repository.saveStudy(id,name,city,preferredDay,type,fixed,percentage,validFrom); done() }
    fun deleteStudy(id:Long,done:()->Unit={}) = viewModelScope.launch { repository.archiveStudy(id); done() }
    fun setHalfDay(date:String,studyId:Long,value:Boolean) = viewModelScope.launch { repository.toggleHalfDay(date,studyId,value) }
    suspend fun exportDatabaseCsv(): String = repository.exportDatabaseCsv()
    suspend fun importDatabaseCsv(csv:String) = repository.importDatabaseCsv(csv)
}

@Composable
fun DentalApp(vm:DentalViewModel) {
    val studies by vm.studies.collectAsState()
    val prestations by vm.prestations.collectAsState()
    val agreements by vm.agreements.collectAsState()
    val dayWorks by vm.dayWorks.collectAsState()
    var screen by remember { mutableStateOf("calendar") }
    var selectedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var editingPrestazione by remember { mutableStateOf<PrestazioneEntity?>(null) }
    var editingStudy by remember { mutableStateOf<StudyEntity?>(null) }
    var summaryMonth by remember { mutableStateOf(YearMonth.now()) }
    MaterialTheme(colorScheme=lightColorScheme(background=Color.White,surface=Color.White)) {
        Surface(Modifier.fillMaxSize(),color=Color.White) {
            Box(Modifier.fillMaxSize().padding(top=APP_EDGE_PADDING,bottom=APP_EDGE_PADDING)) {
                when(screen) {
                    "calendar" -> CalendarScreen(prestations,studies,agreements,dayWorks, onDate={ d -> selectedDate=d; screen="prestazioni" }, onStudies={screen="studies"}, onSummary={month -> summaryMonth=month; screen="summary"})
                    "prestazioni" -> PrestazioniScreen(selectedDate,prestations,studies,agreements,dayWorks,
                        onBack={screen="calendar"}, onNew={editingPrestazione=null;screen="newPrestazione"}, onEdit={editingPrestazione=it;screen="editPrestazione"}, onHalfDay={sid,v->vm.setHalfDay(selectedDate,sid,v)})
                    "newPrestazione" -> PrestazioneFormScreen(null,selectedDate,studies,prestations,agreements,onBack={screen="prestazioni"},onSave={d,s,p,pr,price,gain,n->vm.addPrestazione(d,s,p,pr,price,gain,n){screen="prestazioni"}})
                    "editPrestazione" -> editingPrestazione?.let { PrestazioneFormScreen(it,selectedDate,studies,prestations,agreements,onBack={screen="prestazioni"},onSave={d,s,p,pr,price,gain,n->vm.updatePrestazione(it,d,s,p,pr,price,gain,n){screen="prestazioni"}},onDelete={vm.deletePrestazione(it.id){screen="prestazioni"}}) } ?: run { screen="prestazioni" }
                    "studies" -> StudiesScreen(studies,agreements,onBack={screen="calendar"},onEdit={editingStudy=it;screen="editStudy"},onNew={editingStudy=null;screen="newStudy"})
                    "newStudy" -> StudyFormScreen(null,emptyList(),agreements,onBack={screen="studies"},onSave={n,c,d,t,f,p,v->vm.saveStudy(null,n,c,d,t,f,p,v){screen="studies"}})
                    "editStudy" -> editingStudy?.let { StudyFormScreen(it,agreements.filter{a->a.studyId==it.id},agreements,onBack={screen="studies"},onSave={n,c,d,t,f,p,v->vm.saveStudy(it.id,n,c,d,t,f,p,v){screen="studies"}},onDelete={vm.deleteStudy(it.id){screen="studies"}}) } ?: run {screen="studies"}
                    "summary" -> SummaryScreen(prestations,studies,agreements,dayWorks,onBack={screen="calendar"},vm=vm,initialMonth=summaryMonth)
                }
            }
        }
    }
}

@Composable
fun TopBar(title:String,onBack:()->Unit) { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("‹",fontSize=30.sp,modifier=Modifier.clickable{onBack()});Spacer(Modifier.width(10.dp));Text(title,fontSize=20.sp,fontWeight=FontWeight.Medium)} }

@Composable
fun CalendarScreen(prestations:List<PrestazioneEntity>,studies:List<StudyEntity>,agreements:List<CompensationAgreementEntity>,dayWorks:List<DayWorkEntity>,onDate:(String)->Unit,onStudies:()->Unit,onSummary:(YearMonth)->Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var reveal by remember { mutableStateOf(false) }
    val map=studies.associateBy{it.id}
    val days=month.atDay(1).let { first -> val offset=(first.dayOfWeek.value-1); (0 until (offset+month.lengthOfMonth())).map{n->if(n<offset)null else month.atDay(n-offset+1)} }
    val monthItems=prestations.filter{it.date.startsWith(month.toString())}
    val total=totalGain(monthItems,agreements,dayWorks)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("La mia agenda",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f))}
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Text("‹",fontSize=28.sp,modifier=Modifier.clickable{month=month.minusMonths(1)});Text(month.format(IT_MONTH).replaceFirstChar{it.uppercase()},Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);Text("›",fontSize=28.sp,modifier=Modifier.clickable{month=month.plusMonths(1)});Button(onClick=onStudies,contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)){Text("Studi")}}
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()){listOf("L","M","M","G","V","S","D").forEach{Text(it,Modifier.weight(1f),fontWeight=FontWeight.Bold)} }
        Spacer(Modifier.height(3.dp))
        days.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    Box(Modifier.weight(1f).height(92.dp).padding(2.dp).border(1.dp,Color.LightGray,RoundedCornerShape(6.dp)).clickable(enabled=d!=null){ d?.let{ onDate(it.toString()) } }.padding(5.dp)) {
                        if (d != null) {
                            Column(Modifier.fillMaxSize()) {
                                Text("${d.dayOfMonth}", fontWeight=if(d==LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.height(2.dp))
                                val names=monthItems.filter{it.date==d.toString()}.mapNotNull{map[it.studyId]?.name}.distinct()
                                Column(Modifier.padding(start=2.dp)){ names.take(3).forEach { Text(it,maxLines=1,style=MaterialTheme.typography.labelSmall) } }
                            }
                        }
                    }
                }
                repeat(7-row.size){ Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth().clickable{reveal=!reveal},colors=CardDefaults.cardColors(containerColor=Color(0xFFF5F5F5))){Column(Modifier.padding(12.dp)){Text("Guadagni del mese",style=MaterialTheme.typography.labelLarge);Text(if(reveal)money(total) else "••••••",fontSize=23.sp,fontWeight=FontWeight.Bold);Text(if(reveal)"Tocca per oscurare" else "Tocca per visualizzare",style=MaterialTheme.typography.bodySmall,color=Color.Gray)}}
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){OutlinedButton(onClick={onDate(LocalDate.now().toString())},modifier=Modifier.weight(1f)){Text("Prestazioni di oggi")};OutlinedButton(onClick={onSummary(month)}){Text("Riepiloghi")}}
    }
}

@Composable
fun PrestazioniScreen(date:String, prestations:List<PrestazioneEntity>, studies:List<StudyEntity>, agreements:List<CompensationAgreementEntity>, dayWorks:List<DayWorkEntity>, onBack:()->Unit, onNew:()->Unit, onEdit:(PrestazioneEntity)->Unit, onHalfDay:(Long,Boolean)->Unit) {
    val groups = prestations.filter { it.date == date }.groupBy { it.studyId }
    val map = studies.associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        TopBar("Prestazioni • ${formatDate(date)}", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if (groups.isEmpty()) item { Text("Nessuna prestazione per questa data.", color=Color.Gray) }
            groups.toSortedMap().forEach { (sid, items) ->
                item(key="head$sid") {
                    val study = map[sid]
                    val agreement = effectiveAgreement(sid, date, agreements)
                    val fixed = agreement?.type == FIXED
                    val half = dayWorks.any { it.date == date && it.studyId == sid && it.halfDay }
                    Column(Modifier.fillMaxWidth().border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(study?.name ?: "Studio #$sid", fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
                            Text(money(groupGain(items, agreement, half)), fontWeight=FontWeight.Bold)
                        }
                        if (fixed) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=half, onCheckedChange={ onHalfDay(sid, it) })
                                Text("Mezza giornata")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        items.forEach { item ->
                            Row(Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.patientName.ifBlank { "Paziente non indicato" })
                                    Text(item.procedure.ifBlank { "Prestazione non indicata" }, style=MaterialTheme.typography.bodySmall, color=Color.Gray)
                                    if (item.notes.isNotBlank()) Text(item.notes, style=MaterialTheme.typography.bodySmall)
                                }
                                if (!fixed) Text(money(item.gainCents), fontWeight=FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick=onNew, modifier=Modifier.fillMaxWidth().padding(16.dp), colors=ButtonDefaults.buttonColors(containerColor=Color.Black)) { Text("+ NUOVA PRESTAZIONE", color=Color.White) }
    }
}

@Composable
fun PrestazioneFormScreen(
    item: PrestazioneEntity?,
    initialDate: String,
    studies: List<StudyEntity>,
    prestations: List<PrestazioneEntity>,
    agreements: List<CompensationAgreementEntity>,
    onBack: () -> Unit,
    onSave: (String, Long, String, String, Long, Long, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var date by remember { mutableStateOf(displayDate(item?.date ?: initialDate)) }
    var patient by remember { mutableStateOf(item?.patientName?.let(::capitalizeWords) ?: "") }
    var procedure by remember { mutableStateOf(item?.procedure?.let(::capitalizeWords) ?: "") }
    var price by remember { mutableStateOf(if (item == null || item.priceCents == 0L) "" else formatInputMoney(item.priceCents)) }
    var gain by remember { mutableStateOf(if (item == null) "" else formatInputMoney(item.gainCents)) }
    var gainManuallyEdited by remember { mutableStateOf(item != null) }
    var note by remember { mutableStateOf(item?.notes ?: "") }
    var selected by remember {
        mutableStateOf(
            studies.firstOrNull { it.id == item?.studyId }
                ?: studies.firstOrNull {
                    it.preferredDay == runCatching {
                        LocalDate.parse(initialDate, ISO_DATE).dayOfWeek.value
                    }.getOrDefault(-1)
                }
                ?: studies.firstOrNull()
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var confirmBack by remember { mutableStateOf(false) }
    var patientFocus by remember { mutableStateOf(false) }
    var procedureFocus by remember { mutableStateOf(false) }

    val agreement = selected?.let { effectiveAgreement(it.id, parseDateInput(date), agreements) }
    val fixedDay = agreement?.type == FIXED
    val fixedPerPrestazione = agreement?.type == FIXED_PER_PRESTATION

    LaunchedEffect(selected?.id, date) {
        if (fixedPerPrestazione) {
            price = "0,00"
        }
        if (!gainManuallyEdited && fixedDay) {
            gain = "0,00"
        }
        if (!gainManuallyEdited && agreement?.type == PERCENTAGE) {
            gain = formatInputMoney(calcPercentageGain(price, agreement))
        }
    }

    BackHandlerWithConfirm(dirty, confirmBack, { confirmBack = true }, { confirmBack = false; onBack() })

    Column(Modifier.fillMaxSize()) {
        TopBar(if (item == null) "Nuova prestazione" else "Modifica prestazione") {
            if (dirty) confirmBack = true else onBack()
        }
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            DateField(date, { date = formatDateInput(it); dirty = true }, "Data (GG-MM-AAAA)")
            Spacer(Modifier.height(8.dp))

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selected?.name ?: "Seleziona studio")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    studies.forEach { study ->
                        DropdownMenuItem(
                            text = { Text(study.name) },
                            onClick = {
                                selected = study
                                expanded = false
                                dirty = true
                                gainManuallyEdited = false
                                if (effectiveAgreement(study.id, parseDateInput(date), agreements)?.type == FIXED_PER_PRESTATION) {
                                    price = "0,00"
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            SuggestionField(
                "Paziente",
                patient,
                { value -> patient = capitalizeWordsPreserveSpaces(value); dirty = true; patientFocus = true },
                suggestionsFor(patient, selected?.id, prestations) { it.patientName },
                patientFocus,
                { chosen -> patientFocus = false; patient = chosen }
            )
            Spacer(Modifier.height(8.dp))

            SuggestionField(
                "Prestazione",
                procedure,
                { value -> procedure = capitalizeWordsPreserveSpaces(value); dirty = true; procedureFocus = true },
                suggestionsFor(procedure, selected?.id, prestations) { it.procedure },
                procedureFocus,
                { chosen ->
                    procedureFocus = false
                    procedure = chosen
                    dirty = true
                    val currentAgreement = selected?.let {
                        effectiveAgreement(it.id, parseDateInput(date), agreements)
                    }
                    when (currentAgreement?.type) {
                        PERCENTAGE -> {
                            val historicalPrice = modeValue(
                                prestations.filter {
                                    it.studyId == selected?.id &&
                                        it.procedure.equals(chosen, ignoreCase = true) &&
                                        it.priceCents > 0
                                }
                            ) { it.priceCents }
                            if (historicalPrice != null) {
                                price = formatInputMoney(historicalPrice)
                                gain = formatInputMoney(calcPercentageGain(price, currentAgreement))
                                gainManuallyEdited = false
                            }
                        }
                        FIXED_PER_PRESTATION -> {
                            price = "0,00"
                            val historicalGain = modeValue(
                                prestations.filter {
                                    it.studyId == selected?.id &&
                                        it.procedure.equals(chosen, ignoreCase = true) &&
                                        it.gainCents > 0
                                }
                            ) { it.gainCents }
                            if (historicalGain != null) {
                                gain = formatInputMoney(historicalGain)
                                gainManuallyEdited = false
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))

            if (!fixedPerPrestazione) {
                NextField(
                    price,
                    { value ->
                        price = value
                        dirty = true
                        if (!gainManuallyEdited && agreement?.type == PERCENTAGE) {
                            gain = formatInputMoney(calcPercentageGain(value, agreement))
                        }
                    },
                    "Prezzo (€)",
                    KeyboardType.Decimal
                )
                Spacer(Modifier.height(8.dp))
            }

            if (!fixedDay) {
                NextField(
                    gain,
                    { value -> gain = value; dirty = true; gainManuallyEdited = true },
                    "Guadagno (€)",
                    KeyboardType.Decimal
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                note,
                { value -> note = value; dirty = true },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    selected?.let { study ->
                        onSave(
                            parseDateInput(date),
                            study.id,
                            patient,
                            procedure,
                            if (fixedDay) 0L else parseMoney(price),
                            parseMoney(gain),
                            note
                        )
                    }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva") }
            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina") }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("Annullare le modifiche?") },
            text = { Text("Le modifiche non salvate verranno perse.") },
            confirmButton = {
                TextButton(onClick = { confirmBack = false; onBack() }) { Text("Annulla modifiche") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBack = false }) { Text("Continua modifica") }
            }
        )
    }
}

@Composable
fun SuggestionField(label:String,value:String,onValue:(String)->Unit,suggestions:List<String>,expanded:Boolean,onSelect:(String)->Unit){
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(value,onValue,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Words,imeAction=androidx.compose.ui.text.input.ImeAction.Next))
        if(expanded && value.isNotBlank() && suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top=2.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max=180.dp)) { suggestions.take(6).forEach { suggestion -> Text(suggestion,Modifier.fillMaxWidth().clickable{onSelect(suggestion)}.padding(horizontal=12.dp,vertical=10.dp)) } }
            }
        }
    }
}

private fun suggestionsFor(query:String,studyId:Long?,prestations:List<PrestazioneEntity>,selector:(PrestazioneEntity)->String):List<String>{
    if(studyId==null || query.isBlank()) return emptyList()
    val q=query.trim().lowercase(Locale.ITALIAN)
    return prestations.asSequence().filter{it.studyId==studyId}.map(selector).filter{it.isNotBlank()}.distinct().filter{it.lowercase(Locale.ITALIAN).contains(q)}.sorted().take(8).map(::capitalizeWords).toList()
}

private fun capitalizeWords(value:String):String = capitalizeWordsPreserveSpaces(value).trim()
private fun capitalizeWordsPreserveSpaces(value:String):String = buildString { value.forEachIndexed { index, ch -> if (ch.isLetter() && (index == 0 || value[index - 1].isWhitespace())) append(ch.uppercaseChar()) else append(ch) } }

@Composable fun NextField(value:String,onValue:(String)->Unit,label:String,type:KeyboardType=KeyboardType.Text){
    val focusManager=LocalFocusManager.current
    OutlinedTextField(value,onValue,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type,capitalization=if(type==KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,imeAction=androidx.compose.ui.text.input.ImeAction.Next),keyboardActions=KeyboardActions(onNext={focusManager.moveFocus(FocusDirection.Down)}) )
}

@Composable
fun DateField(value:String,onValue:(String)->Unit,label:String,modifier:Modifier=Modifier){
    var state by remember {
        val initial = normalizeDateFieldText(value)
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                initial,
                androidx.compose.ui.text.TextRange(initial.length.coerceAtMost(10))
            )
        )
    }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        val normalized = normalizeDateFieldText(value)
        if (state.text != normalized) {
            state = androidx.compose.ui.text.input.TextFieldValue(
                normalized,
                androidx.compose.ui.text.TextRange(normalized.length.coerceAtMost(10))
            )
        }
    }

    OutlinedTextField(
        value = state,
        onValueChange = { incoming ->
            val old = state
            val oldText = old.text.take(10)
            val oldCursor = old.selection.start.coerceIn(0, oldText.length)
            val incomingText = incoming.text

            when {
                // Backspace/delete: the IME removes one character from the text.
                // We restore the fixed 10-character date field and replace the
                // logical previous digit with a space instead.
                incomingText.length < oldText.length -> {
                    var target = oldCursor - 1
                    if (target >= 0 && oldText[target] == '-') target--
                    if (target >= 0 && oldText[target] != '-') {
                        val chars = oldText.toCharArray()
                        chars[target] = ' '
                        val updated = chars.concatToString().take(10)
                        var cursor = target
                        if (cursor < updated.length && updated[cursor] == '-') cursor++
                        state = androidx.compose.ui.text.input.TextFieldValue(
                            updated,
                            androidx.compose.ui.text.TextRange(cursor.coerceAtMost(10))
                        )
                        onValue(updated)
                    }
                }

                // Typing a digit: Compose normally sends an 11-character string
                // because it inserted the digit. Do not truncate it before detecting
                // the inserted digit; that was the source of the previous bug.
                incomingText.length > oldText.length -> {
                    val typedDigit = findInsertedDigit(oldText, incomingText)
                    if (typedDigit != null) {
                        var target = oldCursor
                        if (target < oldText.length && oldText[target] == '-') target++
                        if (target in 0 until 10) {
                            val chars = oldText.toCharArray()
                            chars[target] = typedDigit
                            val updated = chars.concatToString()
                            var cursor = target + 1
                            if (cursor < 10 && updated[cursor] == '-') cursor++
                            state = androidx.compose.ui.text.input.TextFieldValue(
                                updated,
                                androidx.compose.ui.text.TextRange(cursor.coerceAtMost(10))
                            )
                            onValue(updated)
                        }
                    }
                }

                // Cursor movement or selection changes without a text change.
                else -> {
                    var cursor = incoming.selection.start.coerceIn(0, oldText.length)
                    if (cursor < oldText.length && oldText[cursor] == '-') cursor++
                    state = incoming.copy(
                        text = oldText,
                        selection = androidx.compose.ui.text.TextRange(cursor.coerceAtMost(10))
                    )
                }
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = androidx.compose.ui.text.input.ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        )
    )
}

private fun findInsertedDigit(oldText:String,newText:String):Char? {
    if (newText.length != oldText.length + 1) return null
    for (index in newText.indices) {
        if (newText.removeRange(index, index + 1) == oldText) {
            return newText[index].takeIf { it.isDigit() }
        }
    }
    return null
}

private fun normalizeDateFieldText(value:String):String {
    val raw = value.take(10)
    val result = CharArray(10) { ' ' }
    val source = if (raw.length == 10 && raw[2] == '-' && raw[5] == '-') {
        raw
    } else {
        formatDateInput(raw).padEnd(10, ' ')
    }
    source.take(10).forEachIndexed { index, ch ->
        result[index] = if (ch.isDigit() || ch == '-') ch else ' '
    }
    if (result.size > 2) result[2] = '-'
    if (result.size > 5) result[5] = '-'
    return result.concatToString()
}


@Composable
fun StudyFormScreen(
    study: StudyEntity?,
    history: List<CompensationAgreementEntity>,
    all: List<CompensationAgreementEntity>,
    onBack: () -> Unit,
    onSave: (String, String, Int, String, Long, Double, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val current = history.firstOrNull()
    var name by remember { mutableStateOf(study?.name?.let(::capitalizeWordsPreserveSpaces) ?: "") }
    var city by remember { mutableStateOf(study?.city ?: "") }
    var preferredDay by remember { mutableStateOf(study?.preferredDay ?: 1) }
    var type by remember { mutableStateOf(current?.type ?: FIXED) }
    var fixed by remember { mutableStateOf(if (current == null) "" else formatInputMoney(current.fixedCents)) }
    var pct by remember { mutableStateOf(current?.percentage?.toString() ?: "") }
    var validFrom by remember { mutableStateOf(displayDate(current?.validFrom ?: LocalDate.now().toString())) }
    var changed by remember { mutableStateOf(study == null) }
    var confirm by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (study == null) "Nuovo studio" else "Modifica studio") {
            if (changed) confirm = true else onBack()
        }
        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            NextField(name, { name = capitalizeWordsPreserveSpaces(it); changed = true }, "Nome studio")
            Spacer(Modifier.height(8.dp))
            NextField(city, { city = capitalizeWordsPreserveSpaces(it); changed = true }, "Città")
            Spacer(Modifier.height(10.dp))

            Text("Giorno predefinito di lavoro", fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton(
                    onClick = { dayExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(dayName(preferredDay))
                }
                DropdownMenu(
                    expanded = dayExpanded,
                    onDismissRequest = { dayExpanded = false }
                ) {
                    (1..6).forEach { d ->
                        DropdownMenuItem(
                            text = { Text(dayName(d)) },
                            onClick = {
                                preferredDay = d
                                dayExpanded = false
                                changed = true
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Tipo compenso", fontWeight = FontWeight.Medium)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = type == FIXED,
                        onClick = { type = FIXED; changed = true }
                    )
                    Text("Fisso a giornata")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = type == PERCENTAGE,
                        onClick = { type = PERCENTAGE; changed = true }
                    )
                    Text("A percentuale")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = type == FIXED_PER_PRESTATION,
                        onClick = { type = FIXED_PER_PRESTATION; changed = true }
                    )
                    Text("Fisso a prestazione")
                }
            }

            Spacer(Modifier.height(8.dp))
            when (type) {
                FIXED -> NextField(
                    fixed,
                    { fixed = it; changed = true },
                    "Compenso giornaliero (€)",
                    KeyboardType.Decimal
                )
                PERCENTAGE -> NextField(
                    pct,
                    { pct = it; changed = true },
                    "Percentuale (%)",
                    KeyboardType.Decimal
                )
                FIXED_PER_PRESTATION -> Text(
                    "Il guadagno viene determinato per singola prestazione.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(8.dp))
            DateField(
                validFrom,
                { validFrom = formatDateInput(it); changed = true },
                "Valido dal (GG-MM-AAAA)"
            )
            Text(
                "La data di validità è necessaria quando cambia l'accordo: le prestazioni successive useranno il nuovo compenso.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    onSave(
                        name,
                        city,
                        preferredDay,
                        type,
                        parseMoney(fixed),
                        pct.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        parseDateInput(validFrom)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva")
            }

            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elimina")
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Annullare le modifiche?") },
            text = { Text("Le modifiche non salvate verranno perse.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onBack() }) {
                    Text("Annulla modifiche")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("Continua modifica")
                }
            }
        )
    }
}

@Composable fun StudiesScreen(studies:List<StudyEntity>,agreements:List<CompensationAgreementEntity>,onBack:()->Unit,onEdit:(StudyEntity)->Unit,onNew:()->Unit){Column(Modifier.fillMaxSize()){TopBar("Studi",onBack);LazyColumn(Modifier.weight(1f).padding(16.dp)){items(studies){s->val a=effectiveAgreement(s.id,LocalDate.now().toString(),agreements);Column(Modifier.fillMaxWidth().clickable{onEdit(s)}.padding(vertical=10.dp)){Text(s.name,fontWeight=FontWeight.Medium);Text(listOf(s.city,dayName(s.preferredDay),a?.let{when(it.type){FIXED -> "Fisso ${money(it.fixedCents)}/giornata"; PERCENTAGE -> "${it.percentage}% a prestazione"; FIXED_PER_PRESTATION -> "Fisso a prestazione"; else -> it.type}}).filterNotNull().filter{it.isNotBlank()}.joinToString(" • "),style=MaterialTheme.typography.bodySmall,color=Color.Gray);HorizontalDivider()}}};Button(onClick=onNew,modifier=Modifier.fillMaxWidth().padding(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.Black)){Text("+ NUOVO STUDIO",color=Color.White)}}}

@Composable
fun SummaryScreen(prestations:List<PrestazioneEntity>, studies:List<StudyEntity>, agreements:List<CompensationAgreementEntity>, dayWorks:List<DayWorkEntity>, onBack:()->Unit, vm:DentalViewModel, initialMonth:YearMonth) {
    var from by remember(initialMonth) { mutableStateOf(initialMonth.atDay(1).format(DATE_INPUT)) }
    var to by remember(initialMonth) { mutableStateOf(initialMonth.atEndOfMonth().format(DATE_INPUT)) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val fromIso=parseDateInput(from)
    val toIso=parseDateInput(to)
    val filtered=prestations.filter{(fromIso.isBlank()||it.date>=fromIso)&&(toIso.isBlank()||it.date<=toIso)&&(selected==null||it.studyId==selected)}
    val map=studies.associateBy{it.id}
    val monthly=filtered.groupBy{it.date.take(7)}.toSortedMap(compareByDescending{it})
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){u->
        if(u!=null) scope.launch {
            runCatching {
                val text=vm.exportDatabaseCsv()
                context.contentResolver.openOutputStream(u)?.use { out ->
                    out.write(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()))
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Impossibile creare il file.")
            }.onFailure { importError=it.message ?: "Esportazione non riuscita." }
        }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->
        if(u!=null) scope.launch {
            runCatching { context.contentResolver.openInputStream(u)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Impossibile leggere il file.") }
                .onSuccess { pendingImport=it }
                .onFailure { importError=it.message ?: "Impossibile leggere il file." }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TopBar("Riepiloghi",onBack)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DateField(from,{from=formatDateInput(it)},"Da (GG-MM-AAAA)",Modifier.weight(1f))
            DateField(to,{to=formatDateInput(it)},"A (GG-MM-AAAA)",Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(map[selected]?.name ?: "Tutti gli studi")}
            DropdownMenu(expanded,{expanded=false}) {
                DropdownMenuItem(text={Text("Tutti gli studi")},onClick={selected=null;expanded=false})
                studies.forEach{s->DropdownMenuItem(text={Text(s.name)},onClick={selected=s.id;expanded=false})}
            }
        }
        Spacer(Modifier.height(12.dp))
        monthly.forEach { (month,items) ->
            Text(month.replace('-','/'),fontWeight=FontWeight.Bold)
            items.groupBy{it.studyId}.forEach { (sid,studyItems) -> Text("${map[sid]?.name ?: "Studio #$sid"} • ${money(totalGain(studyItems,agreements,dayWorks))}",Modifier.padding(vertical=5.dp)) }
            HorizontalDivider(Modifier.padding(vertical=6.dp))
        }
        Text("TOTALE",style=MaterialTheme.typography.labelMedium)
        Text(money(totalGain(filtered,agreements,dayWorks)),style=MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={exportLauncher.launch("la_mia_agenda_database.csv")}, modifier=Modifier.weight(1f)){Text("Esporta dati")}
            OutlinedButton(onClick={importLauncher.launch(arrayOf("text/csv","text/plain","application/csv"))}, modifier=Modifier.weight(1f)){Text("Importa dati")}
        }
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest={pendingImport=null},
            title={Text("Importa dati")},
            text={Text("Continuando, tutti i dati attualmente presenti nel database verranno cancellati e sostituiti con quelli del file selezionato. Vuoi continuare?")},
            confirmButton={TextButton(onClick={
                val csvText=pendingImport ?: return@TextButton
                pendingImport=null
                scope.launch { runCatching { vm.importDatabaseCsv(csvText) }.onFailure { importError=it.message ?: "Importazione non riuscita." } }
            }){Text("Continua")}},
            dismissButton={TextButton(onClick={pendingImport=null}){Text("Annulla")}}
        )
    }
    if (importError != null) {
        AlertDialog(
            onDismissRequest={importError=null},
            title={Text("Operazione non riuscita")},
            text={Text(importError ?: "")},
            confirmButton={TextButton(onClick={importError=null}){Text("OK")}}
        )
    }
}

@Composable fun BackHandlerWithConfirm(dirty:Boolean,confirm:Boolean,onBackRequest:()->Unit,onConfirm:()->Unit){androidx.activity.compose.BackHandler(enabled=true){if(dirty)onBackRequest() else onConfirm()}}

private fun effectiveAgreement(studyId:Long,date:String,agreements:List<CompensationAgreementEntity>)=agreements.filter{it.studyId==studyId&&it.validFrom<=date}.maxWithOrNull(compareBy<CompensationAgreementEntity>({it.validFrom},{it.id}))
private fun gainFor(item:PrestazioneEntity,agreements:List<CompensationAgreementEntity>,dayWorks:List<DayWorkEntity>):Long{val a=effectiveAgreement(item.studyId,item.date,agreements);return if(a?.type==FIXED){val half=dayWorks.firstOrNull{it.date==item.date&&it.studyId==item.studyId}?.halfDay==true;if(half)a.fixedCents/2 else a.fixedCents}else item.gainCents}
private fun groupGain(items:List<PrestazioneEntity>,a:CompensationAgreementEntity?,half:Boolean):Long = when(a?.type){FIXED -> if(half)a.fixedCents/2 else a.fixedCents;PERCENTAGE -> items.fold(0L){acc,it->acc+it.gainCents};else -> items.fold(0L){acc,it->acc+it.gainCents}}
private fun totalGain(items:List<PrestazioneEntity>,agreements:List<CompensationAgreementEntity>,dayWorks:List<DayWorkEntity>):Long = items.groupBy{it.date to it.studyId}.entries.fold(0L){acc,entry->val key=entry.key;val group=entry.value;val a=effectiveAgreement(key.second,key.first,agreements);val half=dayWorks.any{it.date==key.first&&it.studyId==key.second&&it.halfDay};acc+groupGain(group,a,half)}
private fun <T> modeValue(items:List<PrestazioneEntity>,selector:(PrestazioneEntity)->T):T? = items.groupingBy(selector).eachCount().maxByOrNull{it.value}?.key
private fun calcPercentageGain(price:String,a:CompensationAgreementEntity?):Long = if(a?.type==PERCENTAGE)(parseMoney(price)*a.percentage/100.0).roundToLong() else 0L
private fun parseMoney(v:String)=runCatching{v.trim().replace(',','.').toBigDecimal().movePointRight(2).longValueExact()}.getOrDefault(0L)
private fun formatInputMoney(c:Long)=String.format(Locale.ITALY,"%.2f",c/100.0)
private fun money(c:Long)=NumberFormat.getCurrencyInstance(Locale.ITALY).format(c/100.0)
private fun formatDate(s:String)=runCatching{LocalDate.parse(s, ISO_DATE).format(IT_DATE)}.getOrElse{s}
private fun displayDate(value:String):String = runCatching {
    LocalDate.parse(value.trim(), ISO_DATE).format(DATE_INPUT)
}.getOrElse {
    runCatching { LocalDate.parse(value.trim(), DATE_INPUT).format(DATE_INPUT) }.getOrElse { formatDateInput(value) }
}
private fun formatDateInput(value:String):String{
    val digits=value.filter{it.isDigit()}.take(8)
    return when {
        digits.length<=2 -> digits
        digits.length<=4 -> digits.take(2)+"-"+digits.drop(2)
        else -> digits.take(2)+"-"+digits.substring(2,4)+"-"+digits.drop(4)
    }
}
private fun parseDateInput(value:String):String = runCatching {
    LocalDate.parse(value.trim(), DATE_INPUT).format(ISO_DATE)
}.getOrElse { value.trim() }
private fun dayName(day:Int):String = when(day){1->"Lunedì";2->"Martedì";3->"Mercoledì";4->"Giovedì";5->"Venerdì";6->"Sabato";else->"Nessun giorno predefinito"}

private fun saveCsv(context:Context,uri:Uri,items:List<PrestazioneEntity>,map:Map<Long,StudyEntity>,agreements:List<CompensationAgreementEntity>,dayWorks:List<DayWorkEntity>){val text=buildString{appendLine("Data;Studio;Paziente;Prestazione;Prezzo;Guadagno;Note");items.forEach{i->appendLine(listOf(formatDate(i.date),map[i.studyId]?.name.orEmpty(),i.patientName,i.procedure,money(i.priceCents),money(gainFor(i,agreements,dayWorks)),i.notes).joinToString(";"){it.replace(";",",")})}};context.contentResolver.openOutputStream(uri)?.use{it.write(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()));it.write(text.toByteArray(Charsets.UTF_8))}}
private fun savePdf(context:Context,uri:Uri,items:List<PrestazioneEntity>,map:Map<Long,StudyEntity>,from:String,to:String,agreements:List<CompensationAgreementEntity>,dayWorks:List<DayWorkEntity>){val doc=PdfDocument();val pageW=595;val pageH=842;var pageNo=1;var page=doc.startPage(PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());var y=45f;val paint=Paint().apply{textSize=11f};fun line(s:String){if(y>800){doc.finishPage(page);pageNo++;page=doc.startPage(PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());y=45f};page.canvas.drawText(s.take(92),24f,y,paint);y+=18f};line("RIEPILOGO PRESTAZIONI");if(from.isNotBlank()||to.isNotBlank())line("Periodo: ${from.ifBlank{"inizio"}} - ${to.ifBlank{"fine"}}");items.forEach{i->line("${formatDate(i.date)} | ${map[i.studyId]?.name.orEmpty()} | ${i.patientName} | Guadagno ${money(gainFor(i,agreements,dayWorks))}")};line("");line("TOTALE: ${money(totalGain(items,agreements,dayWorks))}");doc.finishPage(page);context.contentResolver.openOutputStream(uri)?.use{doc.writeTo(it)};doc.close()}
