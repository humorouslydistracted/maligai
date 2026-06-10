package com.maligai.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.Locale

fun formatRs(d: Double): String =
    if (d == d.toLong().toDouble()) "\u20B9${d.toLong()}" else String.format(Locale.US, "\u20B9%.2f", d)

fun formatQty(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.2f", d)

private fun formatAmountField(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else formatQty(d)

@Composable
fun BillScreen(
    modifier: Modifier = Modifier,
    viewModel: BillViewModel = hiltViewModel()
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val activeBillId by viewModel.activeBillId.collectAsStateWithLifecycle()
    val items by viewModel.activeItems.collectAsStateWithLifecycle()
    val grandTotal by viewModel.grandTotal.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val recognizing by viewModel.recognizing.collectAsStateWithLifecycle()
    val modelReady by viewModel.modelReady.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    val catalogItems by viewModel.catalogItems.collectAsStateWithLifecycle()

    var newItemPick by remember { mutableStateOf<Suggestion?>(null) }
    var catalogPick by remember { mutableStateOf<Suggestion?>(null) }
    var editingLine by remember { mutableStateOf<BillItem?>(null) }
    var editingMenuItem by remember { mutableStateOf<MenuItem?>(null) }
    var showLoanDialog by remember { mutableStateOf(false) }
    var clearCanvasSignal by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showClearBillConfirm by remember { mutableStateOf(false) }
    var showBillPreview by remember { mutableStateOf(false) }
    var showAddItemTab by remember { mutableStateOf(false) }
    var forceRecognizeSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.ensureBillDayReset()
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) { delay(2500); statusMessage = null }
    }

    Column(modifier = modifier.fillMaxSize()) {

        // --- Parked bill tabs ---
        BillTabsRow(
            drafts = drafts,
            activeBillId = activeBillId,
            addItemSelected = showAddItemTab,
            onSelect = { id ->
                showAddItemTab = false
                viewModel.selectBill(id)
            },
            onNew = {
                showAddItemTab = false
                viewModel.newBill()
            },
            onAddItemTab = { showAddItemTab = true },
            onResetBillCounter = { viewModel.resetBillCounter() }
        )

        if (showAddItemTab) {
            AddItemTabContent(
                viewModel = viewModel,
                modelReady = modelReady,
                appSettings = appSettings,
                recognizing = recognizing,
                clearCanvasSignal = clearCanvasSignal,
                onClearCanvas = { clearCanvasSignal++ },
                onStatus = { statusMessage = it }
            )
        } else {
        if (!modelReady) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Handwriting packs not ready \u2014 download English and your shop language from setup.",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // --- Running bill: grows with screen; compact rows show more lines ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.12f)
                .heightIn(min = 160.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
                if (items.isEmpty()) {
                    val hint = ScriptLanguages.byMlKitTag(appSettings?.primaryScriptTag ?: ScriptLanguages.DEFAULT_TAG)
                        ?.canvasHint ?: ScriptLanguages.defaultCanvasHint()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(items, key = { it.id }) { item ->
                            BillItemRow(
                                item = item,
                                compact = true,
                                onEdit = {
                                    val menu = viewModel.findMenuItemForLine(item, catalogItems)
                                    if (menu != null) {
                                        editingLine = item
                                        editingMenuItem = menu
                                    } else {
                                        editingLine = item
                                        editingMenuItem = null
                                    }
                                },
                                onRemove = { viewModel.removeItem(item) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                    )
                    Text(
                        formatRs(grandTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Suggestions + refresh ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactSuggestionRows(
                recognizing = recognizing,
                suggestions = suggestions,
                onCatalogPick = { catalogPick = it },
                onNewItemPick = { newItemPick = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { forceRecognizeSignal++ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh suggestions")
            }
        }

        // --- Canvas sits directly above action buttons ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.14f)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            DrawingCanvas(
                clearSignal = clearCanvasSignal,
                forceRecognizeSignal = forceRecognizeSignal,
                canvasHint = ScriptLanguages.byMlKitTag(appSettings?.primaryScriptTag ?: ScriptLanguages.DEFAULT_TAG)
                    ?.canvasHint ?: ScriptLanguages.defaultCanvasHint(),
                onRecognize = { strokes -> viewModel.recognize(strokes) },
                onCleared = { viewModel.clearSuggestions() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Actions: single row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactPillButton(
                label = "Clear canvas",
                onClick = { clearCanvasSignal++ },
                modifier = Modifier.weight(1f)
            )
            CompactPillButton(
                label = "Clear bill",
                onClick = {
                    if (items.isNotEmpty()) showClearBillConfirm = true
                    else {
                        viewModel.clearActive()
                        clearCanvasSignal++
                    }
                },
                modifier = Modifier.weight(1f)
            )
            CompactPillButton(
                label = "Kadan",
                onClick = { if (items.isNotEmpty()) showLoanDialog = true },
                modifier = Modifier.weight(1f)
            )
            CompactPillButton(
                label = "Preview",
                onClick = {
                    if (items.isNotEmpty()) showBillPreview = true
                    else statusMessage = "Add items first"
                },
                modifier = Modifier.weight(1f)
            )
        }

        } // end bill mode

        statusMessage?.let {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    // --- Catalog pick: item matched but no handwritten amount ---
    if (catalogPick != null && catalogPick?.item != null) {
        val s = catalogPick!!
        val item = s.item!!
        CatalogItemPickDialog(
            item = item,
            mode = ItemPickMode.Add(
                initialTotal = s.parsed.lineTotal,
                initialQuantity = s.parsed.parsedQuantity,
                initialUnitLabel = s.parsed.parsedUnitLabel
            ),
            onDismiss = { catalogPick = null },
            onUpdateItem = { updated -> viewModel.updateCatalogItem(updated) },
            onConfirmQty = { updatedItem, qty ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addFromCatalog(updatedItem, qty, learnKey.ifBlank { null })
                catalogPick = null
                clearCanvasSignal++
            },
            onConfirmTotal = { updatedItem, qty, total ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addFromCatalogWithTotal(updatedItem, qty, total, learnKey.ifBlank { null })
                catalogPick = null
                clearCanvasSignal++
            },
            onConfirmPriceSync = { updatedItem, qty, total, updateCatalog ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addFromCatalogUpdatingPrice(
                    updatedItem, qty, total, learnKey.ifBlank { null }, updateCatalog
                )
                catalogPick = null
                clearCanvasSignal++
            }
        )
    }

    // --- New handwritten item (not in catalog) ---
    newItemPick?.let { s ->
        NewItemPickDialog(
            parsed = s.parsed,
            onDismiss = { newItemPick = null },
            onProceed = { nameLocal, nameLatin, unitType, unitLabel, pricePerUnit, onResult ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addCatalogItem(
                    nameLocal, nameLatin, unitType, unitLabel, pricePerUnit, learnKey
                ) { ok, msg, item -> onResult(ok, msg, item) }
            },
            onUpdateCatalog = { item, nameLocal, nameLatin, unitType, unitLabel, pricePerUnit, onResult ->
                val local = nameLocal.trim()
                val latin = nameLatin.trim()
                val label = unitLabel.trim().ifBlank { defaultUnitLabel(unitType) }
                viewModel.updateCatalogItem(
                    item.copy(
                        nameLocal = local.ifBlank { item.nameLocal },
                        nameLatin = latin.ifBlank { item.nameLatin },
                        unitType = unitType,
                        unitLabel = label,
                        pricePerUnit = pricePerUnit
                    )
                ) { ok, msg -> onResult(ok, msg) }
            },
            onAddToBill = { nameLocal, nameLatin, unitType, unitLabel, unitPrice, qty, lineTotal, catalogItem ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addNewItemToBill(
                    catalogItem, nameLocal, nameLatin, unitType, unitLabel, unitPrice, qty, lineTotal, learnKey
                ) { result ->
                    statusMessage = when (result) {
                        AddItemResult.DuplicateCatalog -> "Item added to bill"
                        AddItemResult.Success -> "Item added to bill"
                    }
                    newItemPick = null
                    clearCanvasSignal++
                }
            },
            onAddWithPriceSync = { catalogItem, qty, total, updateCatalog ->
                val learnKey = (s.parsed.matchHint ?: s.parsed.displayText).trim()
                viewModel.addFromCatalogUpdatingPrice(
                    catalogItem, qty, total, learnKey.ifBlank { null }, updateCatalog
                )
                statusMessage = "Item added to bill"
                newItemPick = null
                clearCanvasSignal++
            }
        )
    }

    // --- Edit existing bill row ---
    editingLine?.let { line ->
        val menu = editingMenuItem
        if (menu != null) {
            CatalogItemPickDialog(
                item = menu,
                mode = ItemPickMode.Edit(line),
                onDismiss = { editingLine = null; editingMenuItem = null },
                onUpdateItem = { updated -> viewModel.updateCatalogItem(updated) },
                onConfirmQty = { updatedItem, qty ->
                    val total = qty * updatedItem.pricePerUnit
                    viewModel.updateLineFromCatalog(line, updatedItem, qty, total)
                    editingLine = null
                    editingMenuItem = null
                },
                onConfirmTotal = { updatedItem, _, total ->
                    viewModel.updateLineFromCatalog(line, updatedItem, null, total)
                    editingLine = null
                    editingMenuItem = null
                }
            )
        } else {
            SimpleLineEditDialog(
                item = line,
                onDismiss = { editingLine = null },
                onConfirm = { name, total, qty ->
                    viewModel.updateLineItem(line, name, total, qty)
                    editingLine = null
                }
            )
        }
    }

    // --- Loan dialog ---
    if (showLoanDialog) {
        LoanDialog(
            total = grandTotal,
            onDismiss = { showLoanDialog = false },
            onConfirm = { name, phone, print ->
                showLoanDialog = false
                viewModel.finalizeBill(true, name, phone, print) { bill, _ ->
                    statusMessage = if (bill == null) "Add items first" else "Saved as kadan for $name"
                    clearCanvasSignal++
                }
            }
        )
    }

    if (showBillPreview) {
        BillPreviewDialog(
            items = items,
            total = grandTotal,
            onDismiss = { showBillPreview = false },
            onPrint = {
                showBillPreview = false
                viewModel.finalizeBill(false, "", "", print = true) { bill, pr ->
                    statusMessage = when {
                        bill == null -> "Add items first"
                        pr != null && !pr.ok -> "Saved. Print: ${pr.message}"
                        else -> "Bill completed & printed"
                    }
                    clearCanvasSignal++
                }
            },
            onDone = {
                showBillPreview = false
                viewModel.finalizeBill(false, "", "", print = false) { bill, _ ->
                    statusMessage = if (bill == null) "Add items first" else "Bill completed"
                    clearCanvasSignal++
                }
            }
        )
    }

    if (showClearBillConfirm) {
        AlertDialog(
            onDismissRequest = { showClearBillConfirm = false },
            title = { Text("Clear bill?") },
            text = { Text("Remove all items from this bill and start fresh?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearActive()
                    clearCanvasSignal++
                    showClearBillConfirm = false
                }) { Text("Clear bill") }
            },
            dismissButton = {
                TextButton(onClick = { showClearBillConfirm = false }) { Text("Cancel") }
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillTabsRow(
    drafts: List<Bill>,
    activeBillId: Long?,
    addItemSelected: Boolean,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onAddItemTab: () -> Unit,
    onResetBillCounter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(drafts, key = { it.id }) { bill ->
                val selected = !addItemSelected && bill.id == activeBillId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(bill.id) },
                    label = { Text(bill.name, maxLines = 1) }
                )
            }
            item {
                IconButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = "New bill")
                }
            }
            item {
                FilterChip(
                    selected = addItemSelected,
                    onClick = onAddItemTab,
                    label = { Text("Add Item", maxLines = 1) }
                )
            }
        }
        IconButton(
            onClick = onResetBillCounter,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset bill number to 1")
        }
    }
}

/**
 * Homepage "Add Item" tab.
 * Layout (no scroll): item name → canvas → suggestion chips → unit type → amount → buttons.
 */
@Composable
private fun AddItemTabContent(
    viewModel: BillViewModel,
    modelReady: Boolean,
    appSettings: AppSettings?,
    recognizing: Boolean,
    clearCanvasSignal: Int,
    onClearCanvas: () -> Unit,
    onStatus: (String) -> Unit
) {
    var nameLocal by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf(UnitType.COUNT) }
    var price by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var canvasSuggestions by remember { mutableStateOf<List<ParsedLine>>(emptyList()) }
    var forceRecognizeSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(clearCanvasSignal) { if (clearCanvasSignal > 0) canvasSuggestions = emptyList() }

    fun resetForm() {
        nameLocal = ""; unitType = UnitType.COUNT; price = ""; formError = null; canvasSuggestions = emptyList()
    }

    val canHint = ScriptLanguages.byMlKitTag(appSettings?.primaryScriptTag ?: ScriptLanguages.DEFAULT_TAG)
        ?.canvasHint ?: ScriptLanguages.defaultCanvasHint()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ① Item name — typed, system keyboard
        OutlinedTextField(
            value = nameLocal,
            onValueChange = { nameLocal = it; formError = null },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        // ② Canvas — handwriting input
        if (!modelReady) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Handwriting packs not ready \u2014 download from setup.",
                    modifier = Modifier.padding(6.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DrawingCanvas(
            clearSignal = clearCanvasSignal,
            forceRecognizeSignal = forceRecognizeSignal,
            canvasHint = canHint,
            onRecognize = { strokes ->
                viewModel.parseStrokes(strokes) { parsedLines ->
                    canvasSuggestions = parsedLines
                        .map { p -> p.copy(displayText = p.displayText.ifBlank { p.raw }.trim()) }
                        .filter { it.displayText.isNotBlank() }
                        .distinctBy { it.displayText }
                        .take(10)
                }
            },
            onCleared = { canvasSuggestions = emptyList() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
        )

        // "Recognize" button — manual trigger for latest strokes
        OutlinedButton(
            onClick = { forceRecognizeSignal++ },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("Recognize", fontSize = 12.sp) }

        Spacer(Modifier.height(4.dp))

        // ③ Suggestion chips — fixed compact height so canvas never shrinks
        CompactParsedLineRows(
            recognizing = recognizing,
            lines = canvasSuggestions,
            onPick = { label -> nameLocal = label; formError = null },
            selectedLabel = nameLocal
        )

        // ④ Unit type
        Text("Unit type", style = MaterialTheme.typography.labelMedium)
        UnitTypeChipRow(
            selected = unitType,
            onSelect = { type -> unitType = type }
        )

        Spacer(Modifier.height(6.dp))

        // ⑤ Price — tapping opens system numeric keyboard
        OutlinedTextField(
            value = price,
            onValueChange = { v -> price = v.filter { c -> c.isDigit() || c == '.' }; formError = null },
            label = { Text("Price per ${defaultUnitLabel(unitType)} (\u20B9)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        formError?.let {
            Text(it, color = LoanColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ⑥ Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onClearCanvas(); canvasSuggestions = emptyList() },
                modifier = Modifier.weight(1f)
            ) { Text("Clear canvas") }
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    val n = nameLocal.trim()
                    viewModel.addCatalogItem(
                        n,
                        if (isMostlyLatin(n)) n else "",
                        unitType,
                        defaultUnitLabel(unitType),
                        p
                    ) { ok, msg, _ ->
                        if (ok) {
                            resetForm()
                            onClearCanvas()
                            onStatus("Item saved to catalog")
                        } else {
                            formError = msg
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = nameLocal.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save item") }
        }
    }
}

@Composable
private fun BillPreviewDialog(
    items: List<BillItem>,
    total: Double,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    line.itemName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                                if (line.showsQtyBreakdown()) {
                                    Text(
                                        "${formatQty(line.quantity)} ${line.unitLabel} \u00D7 ${formatRs(line.unitPrice)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                formatRs(line.lineTotal),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatRs(total),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrint) { Text("Print") }
                Button(onClick = onDone) { Text("Done") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CompactPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        shape = RoundedCornerShape(50)
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * For parsed handwritten lines (unitLabel == ""), the qty×unit subtitle is
 * suppressed — only [displayText] on the left and ₹amount on the right.
 */
@Composable
private fun BillItemRow(
    item: BillItem,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.itemName,
                style = if (compact) {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp, lineHeight = 19.sp)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (item.showsQtyBreakdown()) {
                Text(
                    "${formatQty(item.quantity)} ${item.unitLabel} \u00D7 ${formatRs(item.unitPrice)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 13.sp else MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = if (compact) 14.sp else MaterialTheme.typography.bodySmall.lineHeight
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Text(
            formatRs(item.lineTotal),
            style = if (compact) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp)
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(if (compact) 34.dp else 40.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(if (compact) 17.dp else 20.dp)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(if (compact) 34.dp else 40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove",
                tint = LoanColor,
                modifier = Modifier.size(if (compact) 17.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun DrawingCanvas(
    clearSignal: Int,
    canvasHint: String,
    onRecognize: (List<List<TimedPoint>>) -> Unit,
    onCleared: () -> Unit,
    forceRecognizeSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val strokes = remember { mutableStateOf<List<List<TimedPoint>>>(emptyList()) }
    val currentStroke = remember { mutableStateOf<List<TimedPoint>>(emptyList()) }
    var redraw by remember { mutableIntStateOf(0) }
    var recognizeTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(clearSignal) {
        if (clearSignal > 0) {
            strokes.value = emptyList()
            currentStroke.value = emptyList()
            redraw++
            onCleared()
        }
    }

    // Immediate recognition on demand (e.g. "Recognize" button tap)
    LaunchedEffect(forceRecognizeSignal) {
        if (forceRecognizeSignal > 0 && strokes.value.isNotEmpty()) {
            onRecognize(strokes.value)
        }
    }

    // 800ms debounce after each stroke lift
    LaunchedEffect(recognizeTrigger) {
        if (recognizeTrigger > 0 && strokes.value.isNotEmpty()) {
            delay(800)
            onRecognize(strokes.value)
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke.value = listOf(TimedPoint(offset.x, offset.y, System.currentTimeMillis()))
                            redraw++
                        },
                        onDrag = { change, _ ->
                            currentStroke.value = currentStroke.value +
                                TimedPoint(change.position.x, change.position.y, System.currentTimeMillis())
                            change.consume()
                            redraw++
                        },
                        onDragEnd = {
                            if (currentStroke.value.isNotEmpty()) {
                                strokes.value = strokes.value + listOf(currentStroke.value)
                                currentStroke.value = emptyList()
                                redraw++
                                recognizeTrigger++
                            }
                        }
                    )
                }
        ) {
            redraw
            val allStrokes = strokes.value + listOf(currentStroke.value)
            for (stroke in allStrokes) {
                if (stroke.size < 2) {
                    if (stroke.size == 1) {
                        drawCircle(Color.Black, radius = 3f, center = Offset(stroke[0].x, stroke[0].y))
                    }
                    continue
                }
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (strokes.value.isEmpty() && currentStroke.value.isEmpty()) {
            Text(
                canvasHint,
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFBBBBBB),
                fontSize = 18.sp
            )
        }

        IconButton(
            onClick = {
                if (strokes.value.isNotEmpty()) {
                    strokes.value = strokes.value.dropLast(1)
                    currentStroke.value = emptyList()
                    redraw++
                    if (strokes.value.isNotEmpty()) {
                        recognizeTrigger++
                    } else {
                        onCleared()
                    }
                }
            },
            enabled = strokes.value.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(8.dp)
                )
        ) {
            Icon(
                Icons.Filled.Undo,
                contentDescription = "Undo last stroke",
                tint = if (strokes.value.isNotEmpty()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

/** Pick qty from catalog using unit-type quick buttons; default price = qty × pricePerUnit. */
private enum class PickMode { QUANTITY, AMOUNT }

private sealed class ItemPickMode {
    data class Add(
        val initialTotal: Double? = null,
        val initialQuantity: Double? = null,
        val initialUnitLabel: String? = null
    ) : ItemPickMode()

    data class Edit(val line: BillItem) : ItemPickMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogPriceSyncDialog(
    itemName: String,
    unitLabel: String,
    catalogPricePerUnit: Double,
    quantity: Double,
    catalogTotal: Double,
    writtenTotal: Double,
    onUpdateAndAdd: () -> Unit,
    onAddOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val impliedPrice = writtenTotal / quantity.coerceAtLeast(1.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price changed?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "$itemName: catalog ${formatRs(catalogPricePerUnit)}/$unitLabel \u2192 " +
                        "${formatQty(quantity)} \u00D7 ${formatRs(catalogPricePerUnit)} = ${formatRs(catalogTotal)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "You wrote ${formatRs(writtenTotal)} (${formatRs(impliedPrice)}/$unitLabel)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdateAndAdd) { Text("Update catalog & add") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onAddOnly) { Text("Add at ${formatRs(writtenTotal)} only") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogItemPickDialog(
    item: MenuItem,
    mode: ItemPickMode,
    onDismiss: () -> Unit,
    onUpdateItem: (MenuItem) -> Unit,
    onConfirmQty: (MenuItem, Double) -> Unit,
    onConfirmTotal: (MenuItem, Double, Double) -> Unit,
    onConfirmPriceSync: ((MenuItem, Double, Double, Boolean) -> Unit)? = null
) {
    val isEdit = mode is ItemPickMode.Edit
    val editLine = (mode as? ItemPickMode.Edit)?.line

    var catalogItem by remember(item.id) { mutableStateOf(item) }
    var showPriceSync by remember { mutableStateOf(false) }
    var pickMode by remember(mode) {
        mutableStateOf(
            when (mode) {
                is ItemPickMode.Add ->
                    when {
                        mode.initialQuantity != null -> PickMode.QUANTITY
                        mode.initialTotal != null && mode.initialTotal > 0 -> PickMode.AMOUNT
                        else -> PickMode.QUANTITY
                    }
                is ItemPickMode.Edit ->
                    if (mode.line.showsQtyBreakdown()) PickMode.QUANTITY else PickMode.AMOUNT
            }
        )
    }
    var customTotal by remember(mode) {
        mutableStateOf(
            when (mode) {
                is ItemPickMode.Add -> mode.initialTotal?.let { formatAmountField(it) } ?: ""
                is ItemPickMode.Edit ->
                    if (mode.line.showsQtyBreakdown()) "" else formatAmountField(mode.line.lineTotal)
            }
        )
    }
    var selectedQty by remember(mode) {
        mutableStateOf(
            when (mode) {
                is ItemPickMode.Add -> mode.initialQuantity ?: 1.0
                is ItemPickMode.Edit ->
                    if (mode.line.showsQtyBreakdown()) mode.line.quantity else 1.0
            }
        )
    }
    var resetSignal by remember { mutableIntStateOf(0) }
    var showQuickEdit by remember { mutableStateOf(false) }
    var editPrice by remember(catalogItem) {
        mutableStateOf(formatAmountField(catalogItem.pricePerUnit))
    }
    var editUnitType by remember(catalogItem) { mutableStateOf(catalogItem.unitType) }
    var editUnitLabel by remember(catalogItem) { mutableStateOf(catalogItem.unitLabel) }

    val customTotalValue = customTotal.toDoubleOrNull()
    val pickerQty = if (pickMode == PickMode.QUANTITY) selectedQty else -1.0
    val confirmLabel = if (isEdit) "Save" else "Add"

    fun resetPicker() {
        when (val m = mode) {
            is ItemPickMode.Add -> {
                selectedQty = m.initialQuantity ?: 1.0
                customTotal = m.initialTotal?.let { formatAmountField(it) } ?: ""
                pickMode = when {
                    m.initialQuantity != null -> PickMode.QUANTITY
                    m.initialTotal != null && m.initialTotal > 0 -> PickMode.AMOUNT
                    else -> PickMode.QUANTITY
                }
            }
            is ItemPickMode.Edit -> {
                selectedQty = if (m.line.showsQtyBreakdown()) m.line.quantity else 1.0
                customTotal = if (m.line.showsQtyBreakdown()) "" else formatAmountField(m.line.lineTotal)
                pickMode = if (m.line.showsQtyBreakdown()) PickMode.QUANTITY else PickMode.AMOUNT
            }
        }
        resetSignal++
    }

    fun writtenTotalForAdd(): Double? {
        val add = mode as? ItemPickMode.Add ?: return null
        return when {
            pickMode == PickMode.AMOUNT -> customTotalValue?.takeIf { it > 0 }
            add.initialTotal != null && add.initialTotal > 0 -> add.initialTotal
            customTotalValue != null && customTotalValue > 0 -> customTotalValue
            else -> null
        }
    }

    fun tryConfirmAdd() {
        if (pickMode == PickMode.AMOUNT && customTotalValue != null && customTotalValue > 0) {
            onConfirmTotal(catalogItem, selectedQty.coerceAtLeast(1.0), customTotalValue)
            return
        }
        val written = writtenTotalForAdd()
        val catalogTotal = selectedQty * catalogItem.pricePerUnit
        if (!isEdit && onConfirmPriceSync != null && written != null && written > 0 &&
            !totalsMatch(written, catalogTotal)
        ) {
            showPriceSync = true
        } else {
            onConfirmQty(catalogItem, selectedQty)
        }
    }

    if (showPriceSync) {
        val written = writtenTotalForAdd()
        if (written != null) {
            CatalogPriceSyncDialog(
                itemName = catalogItem.nameLocal,
                unitLabel = catalogItem.unitLabel,
                catalogPricePerUnit = catalogItem.pricePerUnit,
                quantity = selectedQty,
                catalogTotal = selectedQty * catalogItem.pricePerUnit,
                writtenTotal = written,
                onUpdateAndAdd = {
                    showPriceSync = false
                    onConfirmPriceSync?.invoke(catalogItem, selectedQty, written, true)
                },
                onAddOnly = {
                    showPriceSync = false
                    onConfirmPriceSync?.invoke(catalogItem, selectedQty, written, false)
                },
                onDismiss = { showPriceSync = false }
            )
            return
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { tryConfirmAdd() },
                enabled = when (pickMode) {
                    PickMode.AMOUNT -> customTotalValue != null && customTotalValue > 0
                    PickMode.QUANTITY -> selectedQty > 0
                }
            ) {
                val preview = when (pickMode) {
                    PickMode.AMOUNT -> customTotalValue ?: 0.0
                    PickMode.QUANTITY -> writtenTotalForAdd()?.takeIf { w ->
                        !totalsMatch(w, selectedQty * catalogItem.pricePerUnit)
                    } ?: (selectedQty * catalogItem.pricePerUnit)
                }
                Text("$confirmLabel  ${formatRs(preview)}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(catalogItem.nameLocal, modifier = Modifier.weight(1f))
                IconButton(onClick = { showQuickEdit = !showQuickEdit }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit catalog item")
                }
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { resetPicker() }) { Text("Reset") }
                }
                if (showQuickEdit) {
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Price per unit (\u20B9)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Unit type", style = MaterialTheme.typography.labelMedium)
                    UnitTypeChipRow(
                        selected = editUnitType,
                        onSelect = { type ->
                            editUnitType = type
                            editUnitLabel = defaultUnitLabel(type)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUnitLabel,
                        onValueChange = { editUnitLabel = it },
                        label = { Text("Unit label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val price = editPrice.toDoubleOrNull() ?: catalogItem.pricePerUnit
                            val updated = catalogItem.copy(
                                unitType = editUnitType,
                                unitLabel = editUnitLabel.trim().ifBlank { defaultUnitLabel(editUnitType) },
                                pricePerUnit = price
                            )
                            onUpdateItem(updated)
                            catalogItem = updated
                            showQuickEdit = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save catalog changes") }
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    "Default: ${formatRs(catalogItem.pricePerUnit)} / ${catalogItem.unitLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                key(resetSignal) {
                    QuantityPicker(
                        unitType = catalogItem.unitType,
                        unitLabel = catalogItem.unitLabel,
                        selectedQty = pickerQty,
                        showSelectedLine = pickMode == PickMode.QUANTITY,
                        resetSignal = resetSignal,
                        onQtyChange = {
                            selectedQty = it
                            pickMode = PickMode.QUANTITY
                            customTotal = ""
                        }
                    )
                }
                if (pickMode == PickMode.QUANTITY) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Price: ${formatRs(selectedQty * catalogItem.pricePerUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Or enter custom total (\u20B9) to override catalog price:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = customTotal,
                    onValueChange = { v ->
                        customTotal = v.filter { c -> c.isDigit() || c == '.' }
                        if (customTotal.isNotBlank()) pickMode = PickMode.AMOUNT
                        else if (!isEdit) pickMode = PickMode.QUANTITY
                        else if (editLine != null && editLine.showsQtyBreakdown()) pickMode = PickMode.QUANTITY
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Custom total (\u20B9)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun SimpleLineEditDialog(
    item: BillItem,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double?) -> Unit
) {
    var name by remember { mutableStateOf(item.itemName) }
    var amount by remember { mutableStateOf(formatAmountField(item.lineTotal)) }
    val amountValue = amount.toDoubleOrNull()
    val isValid = name.isNotBlank() && amountValue != null && amountValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { if (isValid) onConfirm(name, amountValue!!, null) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit line") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { c -> c.isDigit() || c == '.' } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Amount (\u20B9)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

/** Compact single-row unit type chips for pick dialogs. */
@Composable
private fun UnitTypeChipRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(UnitType.WEIGHT, UnitType.VOLUME, UnitType.COUNT).forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { if (enabled) onSelect(type) },
                enabled = enabled,
                label = { Text(unitTypeShortLabel(type), maxLines = 1, fontSize = 12.sp) }
            )
        }
    }
}

/** Compact chip used in fixed-height suggestion rows. */
@Composable
private fun CompactSuggestionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
        modifier = Modifier.height(26.dp)
    )
}

/** Bill-page suggestions: fixed 52dp height so canvas never resizes. */
@Composable
private fun CompactSuggestionRows(
    recognizing: Boolean,
    suggestions: List<Suggestion>,
    onCatalogPick: (Suggestion) -> Unit,
    onNewItemPick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val regionalChips = suggestions.filter { it.item != null || !it.isLatinScript }
    val englishChips = suggestions.filter { it.item == null && it.isLatinScript }

    Box(
        modifier = modifier.height(52.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            recognizing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recognizing\u2026", style = MaterialTheme.typography.bodySmall)
            }
            regionalChips.isNotEmpty() || englishChips.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (regionalChips.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(regionalChips, key = { s -> "r_" + (s.item?.id?.toString() ?: s.parsed.displayText.take(10)) }) { s ->
                            val label = buildString {
                                if (s.item == null) append("+ ")
                                append(s.item?.nameLocal ?: s.parsed.displayText.ifBlank { s.parsed.raw })
                                val hint = LineParser.formatParsedHint(s.parsed)
                                if (hint.isNotBlank()) append(" \u00B7 $hint")
                            }
                            CompactSuggestionChip(label, selected = false) {
                                if (s.item != null) onCatalogPick(s) else onNewItemPick(s)
                            }
                        }
                    }
                }
                if (englishChips.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(englishChips, key = { s -> "en_" + s.parsed.displayText.take(10) }) { s ->
                            val hint = LineParser.formatParsedHint(s.parsed)
                            val label = buildString {
                                append("+ ${s.parsed.displayText.ifBlank { s.parsed.raw }}")
                                if (hint.isNotBlank()) append(" \u00B7 $hint")
                            }
                            CompactSuggestionChip(label, selected = false) { onNewItemPick(s) }
                        }
                    }
                }
            }
        }
    }
}

/** Add-item tab suggestions: fixed 52dp height so canvas never resizes. */
@Composable
private fun CompactParsedLineRows(
    recognizing: Boolean,
    lines: List<ParsedLine>,
    onPick: (String) -> Unit,
    selectedLabel: String,
    modifier: Modifier = Modifier
) {
    val regional = lines.filter { !isMostlyLatin(it.displayText) }
    val latin = lines.filter { isMostlyLatin(it.displayText) && it.displayText.length >= 3 }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            recognizing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recognizing\u2026", style = MaterialTheme.typography.bodySmall)
            }
            regional.isNotEmpty() || latin.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (regional.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(regional, key = { it.displayText }) { p ->
                            CompactSuggestionChip(p.displayText, selected = selectedLabel == p.displayText) {
                                onPick(p.displayText)
                            }
                        }
                    }
                }
                if (latin.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(latin, key = { it.displayText }) { p ->
                            CompactSuggestionChip(p.displayText, selected = selectedLabel == p.displayText) {
                                onPick(p.displayText)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** New handwritten item — two steps: define item (name/type/price), then quantity → add to bill. */
@Composable
private fun NewItemPickDialog(
    parsed: ParsedLine,
    onDismiss: () -> Unit,
    onProceed: (
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        pricePerUnit: Double,
        onResult: (Boolean, String?, MenuItem?) -> Unit
    ) -> Unit,
    onUpdateCatalog: (
        item: MenuItem,
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        pricePerUnit: Double,
        onResult: (Boolean, String?) -> Unit
    ) -> Unit,
    onAddToBill: (
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        unitPrice: Double,
        quantity: Double,
        lineTotal: Double,
        catalogItem: MenuItem?
    ) -> Unit,
    onAddWithPriceSync: (MenuItem, Double, Double, Boolean) -> Unit = { _, _, _, _ -> }
) {
    val display = parsed.displayText.ifBlank { parsed.raw }.trim()
    var nameLocal by remember(parsed) { mutableStateOf(display) }
    var unitType by remember(parsed) {
        mutableStateOf(LineParser.unitTypeFromLabel(parsed.parsedUnitLabel))
    }
    var unitPrice by remember(parsed) {
        mutableStateOf(
            parsed.lineTotal?.let { t ->
                val q = parsed.parsedQuantity ?: 1.0
                if (q > 0) formatAmountField(t / q) else formatAmountField(t)
            } ?: ""
        )
    }
    var pickMode by remember(parsed) {
        mutableStateOf(
            when {
                parsed.parsedQuantity != null -> PickMode.QUANTITY
                parsed.lineTotal != null -> PickMode.AMOUNT
                else -> PickMode.QUANTITY
            }
        )
    }
    var customTotal by remember(parsed) {
        mutableStateOf(parsed.lineTotal?.let { formatAmountField(it) } ?: "")
    }
    var detailsLocked by remember { mutableStateOf(false) }
    var savedCatalogItem by remember { mutableStateOf<MenuItem?>(null) }
    var proceedError by remember { mutableStateOf<String?>(null) }
    var proceedBusy by remember { mutableStateOf(false) }
    var selectedQty by remember(parsed) { mutableStateOf(parsed.parsedQuantity ?: 1.0) }
    var resetSignal by remember { mutableIntStateOf(0) }
    var showPriceSync by remember { mutableStateOf(false) }

    val unitLabel = defaultUnitLabel(unitType)
    val unitPriceValue = unitPrice.toDoubleOrNull()
    val customTotalValue = customTotal.toDoubleOrNull()
    val canProceed = nameLocal.isNotBlank() && unitPriceValue != null && unitPriceValue > 0 && !proceedBusy
    val qtyLineTotal = if (unitPriceValue != null && selectedQty > 0) selectedQty * unitPriceValue else 0.0
    val effectiveLineTotal = when (pickMode) {
        PickMode.AMOUNT -> customTotalValue ?: 0.0
        PickMode.QUANTITY -> qtyLineTotal
    }
    val canAdd = detailsLocked && when (pickMode) {
        PickMode.AMOUNT -> customTotalValue != null && customTotalValue > 0
        PickMode.QUANTITY -> selectedQty > 0 && qtyLineTotal > 0
    }
    val pickerQty = if (pickMode == PickMode.QUANTITY) selectedQty else -1.0

    fun resetSetup() {
        nameLocal = display
        unitType = LineParser.unitTypeFromLabel(parsed.parsedUnitLabel)
        unitPrice = parsed.lineTotal?.let { t ->
            val q = parsed.parsedQuantity ?: 1.0
            if (q > 0) formatAmountField(t / q) else formatAmountField(t)
        } ?: ""
        pickMode = when {
            parsed.parsedQuantity != null -> PickMode.QUANTITY
            parsed.lineTotal != null -> PickMode.AMOUNT
            else -> PickMode.QUANTITY
        }
        customTotal = parsed.lineTotal?.let { formatAmountField(it) } ?: ""
        proceedError = null
        detailsLocked = false
        savedCatalogItem = null
        selectedQty = parsed.parsedQuantity ?: 1.0
        resetSignal++
    }

    fun performAdd(updateCatalog: Boolean? = null) {
        if (!canAdd) return
        val n = nameLocal.trim()
        val qty = if (pickMode == PickMode.QUANTITY) selectedQty else 1.0
        val total = when {
            updateCatalog != null && savedCatalogItem != null ->
                parsed.lineTotal?.takeIf { it > 0 } ?: customTotalValue ?: qtyLineTotal
            pickMode == PickMode.AMOUNT -> customTotalValue!!
            else -> qtyLineTotal
        }
        if (updateCatalog != null && savedCatalogItem != null) {
            onAddWithPriceSync(savedCatalogItem!!, qty, total, updateCatalog)
            return
        }
        onAddToBill(
            n,
            if (isMostlyLatin(n)) n else "",
            unitType,
            unitLabel,
            unitPriceValue ?: 0.0,
            qty,
            total,
            savedCatalogItem
        )
    }

    if (showPriceSync && savedCatalogItem != null) {
        val written = parsed.lineTotal?.takeIf { it > 0 } ?: qtyLineTotal
        CatalogPriceSyncDialog(
            itemName = savedCatalogItem!!.nameLocal,
            unitLabel = unitLabel,
            catalogPricePerUnit = unitPriceValue ?: savedCatalogItem!!.pricePerUnit,
            quantity = selectedQty,
            catalogTotal = selectedQty * (unitPriceValue ?: 0.0),
            writtenTotal = written,
            onUpdateAndAdd = {
                showPriceSync = false
                performAdd(updateCatalog = true)
            },
            onAddOnly = {
                showPriceSync = false
                performAdd(updateCatalog = false)
            },
            onDismiss = { showPriceSync = false }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New item", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (detailsLocked) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Editable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = nameLocal,
                    onValueChange = { nameLocal = it; proceedError = null },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                UnitTypeChipRow(
                    selected = unitType,
                    onSelect = { unitType = it; proceedError = null }
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { v ->
                        unitPrice = v.filter { c -> c.isDigit() || c == '.' }
                        proceedError = null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Price per $unitLabel (\u20B9)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { resetSetup() },
                        enabled = !detailsLocked,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("Reset", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (!canProceed) return@Button
                            proceedBusy = true
                            proceedError = null
                            val n = nameLocal.trim()
                            val latin = if (isMostlyLatin(n)) n else ""
                            if (!detailsLocked) {
                                onProceed(n, latin, unitType, unitLabel, unitPriceValue!!) { ok, msg, item ->
                                    proceedBusy = false
                                    if (ok && item != null) {
                                        detailsLocked = true
                                        savedCatalogItem = item
                                        proceedError = null
                                    } else {
                                        proceedError = msg ?: "Could not save item"
                                    }
                                }
                            } else {
                                val item = savedCatalogItem
                                if (item == null) {
                                    proceedBusy = false
                                    return@Button
                                }
                                onUpdateCatalog(item, n, latin, unitType, unitLabel, unitPriceValue!!) { ok, msg ->
                                    proceedBusy = false
                                    if (ok) {
                                        savedCatalogItem = item.copy(
                                            nameLocal = n.ifBlank { item.nameLocal },
                                            nameLatin = latin.ifBlank { item.nameLatin },
                                            unitType = unitType,
                                            unitLabel = unitLabel,
                                            pricePerUnit = unitPriceValue!!
                                        )
                                        proceedError = null
                                    } else {
                                        proceedError = msg ?: "Could not update item"
                                    }
                                }
                            }
                        },
                        enabled = canProceed && (!detailsLocked || savedCatalogItem != null),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (proceedBusy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (detailsLocked) "Update" else "Save", fontSize = 13.sp)
                        }
                    }
                }
                proceedError?.let {
                    Text(it, color = LoanColor, style = MaterialTheme.typography.bodySmall)
                }
                if (!detailsLocked) {
                    Text(
                        "Fill name, type and price, then tap Save to add quantity or amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        "Item saved — edit name, type or price above, tap Update, then pick quantity or amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Quantity", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                key(resetSignal) {
                    QuantityPicker(
                        unitType = unitType,
                        unitLabel = unitLabel,
                        selectedQty = if (detailsLocked) pickerQty else -1.0,
                        showSelectedLine = detailsLocked && pickMode == PickMode.QUANTITY,
                        compact = true,
                        enabled = detailsLocked,
                        resetSignal = resetSignal,
                        onQtyChange = {
                            selectedQty = it
                            pickMode = PickMode.QUANTITY
                            customTotal = ""
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Or enter total amount (\u20B9) without quantity:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = customTotal,
                    onValueChange = { v ->
                        customTotal = v.filter { c -> c.isDigit() || c == '.' }
                        if (customTotal.isNotBlank()) pickMode = PickMode.AMOUNT
                        else if (parsed.parsedQuantity != null) pickMode = PickMode.QUANTITY
                        else pickMode = PickMode.QUANTITY
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Total amount (\u20B9)") },
                    singleLine = true,
                    readOnly = !detailsLocked,
                    enabled = detailsLocked,
                    modifier = Modifier.fillMaxWidth()
                )
                if (detailsLocked && effectiveLineTotal > 0) {
                    Text(
                        when (pickMode) {
                            PickMode.QUANTITY ->
                                "${formatQty(selectedQty)} $unitLabel \u00d7 ${formatRs(unitPriceValue ?: 0.0)} = ${formatRs(effectiveLineTotal)}"
                            PickMode.AMOUNT -> "Line total: ${formatRs(effectiveLineTotal)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (!canAdd) return@Button
                            val written = parsed.lineTotal?.takeIf { it > 0 }
                            val catalogTotal = selectedQty * (unitPriceValue ?: 0.0)
                            if (savedCatalogItem != null && pickMode == PickMode.QUANTITY &&
                                written != null && !totalsMatch(written, catalogTotal)
                            ) {
                                showPriceSync = true
                            } else {
                                performAdd()
                            }
                        },
                        enabled = canAdd
                    ) {
                        Text("Add  ${formatRs(effectiveLineTotal)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onDot: () -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "\u232B")
    )
    Column(modifier = Modifier.padding(top = 8.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "." -> onDot()
                                "\u232B" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text(key, fontSize = 18.sp) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LoanDialog(
    total: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val nameError = InputValidators.customerNameError(name)
    val phoneError = InputValidators.phoneError(phone)
    val canSave = nameError == null && phoneError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { if (canSave) onConfirm(name.trim(), phone.trim(), false) }, enabled = canSave) {
                Text("Save Kadan")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { if (canSave) onConfirm(name.trim(), phone.trim(), true) },
                    enabled = canSave
                ) { Text("Save & Print") }
            }
        },
        title = { Text("Kadan \u2014 ${formatRs(total)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = InputValidators.filterCustomerName(it) },
                    label = { Text("Customer name") },
                    singleLine = true,
                    isError = nameError != null && name.isNotBlank(),
                    supportingText = nameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = InputValidators.filterPhone(it) },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    isError = phoneError != null && phone.isNotBlank(),
                    supportingText = phoneError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
