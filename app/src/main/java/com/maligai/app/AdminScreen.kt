package com.maligai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val adminDateFmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)

/**
 * PIN entry gate for the admin section.  Gets settings from [AdminViewModel]
 * internally so it can be called directly from MainActivity without passing
 * settings down manually.
 */
@Composable
internal fun AdminPinEntry(
    modifier: Modifier = Modifier,
    onUnlock: () -> Unit
) {
    val adminVm: AdminViewModel = hiltViewModel()
    val settings by adminVm.settings.collectAsStateWithLifecycle()

    val s = settings ?: run {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecovery by remember { mutableStateOf(false) }

    // Auto-submit when 4 digits entered
      LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (Security.sha256(pin) == s.adminPinHash) onUnlock()
            else { error = "Wrong PIN"; pin = "" }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Admin PIN", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        VisibleOutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
            label = { Text("4-digit PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (Security.sha256(pin) == s.adminPinHash) onUnlock() else error = "Wrong PIN"
            },
            enabled = pin.length == 4
        ) { Text("Unlock") }
        if (s.securityQuestion.isNotBlank()) {
            TextButton(onClick = { showRecovery = true }) { Text("Forgot PIN?") }
        }
    }

    if (showRecovery) {
        var answer by remember { mutableStateOf("") }
        var recError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showRecovery = false },
            confirmButton = {
                Button(onClick = {
                    if (Security.sha256(answer.lowercase().trim()) == s.securityAnswerHash) onUnlock()
                    else recError = "Wrong answer"
                }) { Text("Verify") }
            },
            dismissButton = { TextButton(onClick = { showRecovery = false }) { Text("Cancel") } },
            title = { Text("PIN Recovery") },
            text = {
                Column {
                    Text(
                        s.securityQuestion,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    VisibleOutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Answer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    recError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

/* ------------------------------------------------------------------ Items */

@Composable
internal fun ItemsSection(vm: AdminViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MenuItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<MenuItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    val filteredItems = remember(items, debouncedQuery) {
        val q = debouncedQuery.trim().lowercase(Locale.US)
        if (q.isEmpty()) items
        else items.filter {
            it.nameLocal.lowercase(Locale.US).contains(q) ||
                it.nameLatin.lowercase(Locale.US).contains(q)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Item") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search items") },
            placeholder = { Text("Name (local script or English)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("No items yet. Add your shop's products.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (filteredItems.isEmpty()) {
            Text("No matches for \"$debouncedQuery\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(filteredItems, key = { it.id }) { item ->
                val index = items.indexOf(item)
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.nameLocal + if (!item.available) "  (hidden)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${formatRs(item.pricePerUnit)}/${item.unitLabel}  \u2022  ${unitTypeDisplayLabel(item.unitType)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.moveItem(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.ArrowUpward, "Up")
                        }
                        IconButton(onClick = { vm.moveItem(index, index + 1) }, enabled = index < items.size - 1) {
                            Icon(Icons.Filled.ArrowDownward, "Down")
                        }
                        IconButton(onClick = { editing = item }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { itemToDelete = item }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = LoanColor)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ItemEditDialog(
            existing = null,
            saveError = addError,
            onDismiss = { showAdd = false; addError = null },
            onSave = { item ->
                vm.addItem(item) { ok, msg ->
                    if (ok) {
                        showAdd = false
                        addError = null
                    } else {
                        addError = msg
                    }
                }
            }
        )
    }
    editing?.let { item ->
        ItemEditDialog(item, onDismiss = { editing = null }, onSave = { vm.updateItem(it); editing = null })
    }
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete item?") },
            text = { Text("Delete ${item.nameLocal}? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { vm.deleteItem(item); itemToDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ItemEditDialog(
    existing: MenuItem?,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit,
    saveError: String? = null
) {
    var nameLocal by remember { mutableStateOf(existing?.nameLocal ?: "") }
    var unitType by remember { mutableStateOf(existing?.unitType ?: UnitType.COUNT) }
    var price by remember { mutableStateOf(existing?.pricePerUnit?.let { formatQty(it) } ?: "") }
    var available by remember { mutableStateOf(existing?.available ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    if (nameLocal.isNotBlank()) {
                        onSave(
                            (existing ?: MenuItem(nameLocal = "")).copy(
                                nameLocal = nameLocal.trim(),
                                nameLatin = existing?.nameLatin
                                    ?: if (isMostlyLatin(nameLocal.trim())) nameLocal.trim() else "",
                                unitType = unitType,
                                unitLabel = defaultUnitLabel(unitType),
                                pricePerUnit = p,
                                available = available
                            )
                        )
                    }
                },
                enabled = nameLocal.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (existing == null) "Add Item" else "Edit Item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                saveError?.let { err ->
                    Text(err, color = LoanColor, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = nameLocal, onValueChange = { nameLocal = it },
                    label = { Text("Item name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Unit type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UnitType.WEIGHT, UnitType.VOLUME, UnitType.COUNT).forEach { type ->
                        FilterChip(
                            selected = unitType == type,
                            onClick = { unitType = type },
                            label = { Text(unitTypeDisplayLabel(type)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Price per ${defaultUnitLabel(unitType)} (\u20B9)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = available, onCheckedChange = { available = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Available")
                }
            }
        }
    )
}

/* ----------------------------------------------------------------- Period tabs */

@Composable
private fun PeriodTabsRow(selected: PeriodTab, onSelect: (PeriodTab) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            listOf(PeriodTab.TODAY to "Today", PeriodTab.WEEK to "Week", PeriodTab.MONTH to "Month", PeriodTab.ALL to "All")
        ) { (tab, label) ->
            FilterChip(selected = selected == tab, onClick = { onSelect(tab) }, label = { Text(label) })
        }
    }
}

/* ----------------------------------------------------------------- Ledger */

@Composable
internal fun LedgerSection(
    vm: LedgerViewModel = hiltViewModel(),
    onUpdateBill: (Long) -> Unit = {},
    onPrintBill: (Long) -> Unit = {}
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val revenue by vm.revenue.collectAsStateWithLifecycle()
    var detailBill by remember { mutableStateOf<Bill?>(null) }
    val detailItems by vm.detailItems.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        PeriodTabsRow(tab, vm::setTab)
        SummaryRow("Orders" to bills.size.toString(), "Revenue" to formatRs(revenue))
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(bills, key = { it.id }) { bill ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    detailBill = bill; vm.loadDetail(bill.id)
                }) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                bill.name + if (bill.isLoan) "  (kadan)" else "",
                                fontWeight = FontWeight.Medium,
                                color = if (bill.isLoan) LoanColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                adminDateFmt.format(Date(bill.completedAt ?: bill.createdAt)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatRs(bill.total), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.deleteBill(bill) }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = LoanColor)
                        }
                    }
                }
            }
        }
    }

    detailBill?.let { bill ->
        BillDetailDialog(
            bill = bill,
            items = detailItems,
            onDismiss = { detailBill = null },
            onUpdate = {
                detailBill = null
                onUpdateBill(bill.id)
            },
            onPrint = { onPrintBill(bill.id) }
        )
    }
}

@Composable
private fun SummaryRow(vararg pairs: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pairs.forEach { (label, value) ->
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- Loans */

@Composable
internal fun LoansSection(vm: LoanViewModel = hiltViewModel()) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val total by vm.totalOutstanding.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CustomerOutstanding?>(null) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        SummaryRow("Outstanding" to formatRs(total), "Customers" to customers.size.toString())
        Spacer(Modifier.height(8.dp))
        if (customers.isEmpty()) {
            Text("No outstanding kadan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(customers, key = { it.customerId }) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    selected = c; vm.loadCustomer(c.customerId)
                }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Medium)
                            if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(formatRs(c.outstanding), color = LoanColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    selected?.let { c ->
        val loans by vm.loans.collectAsStateWithLifecycle()
        val payments by vm.payments.collectAsStateWithLifecycle()
        var payAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) { vm.recordPayment(c.customerId, amt); payAmount = "" }
                    },
                    enabled = (payAmount.toDoubleOrNull() ?: 0.0) > 0
                ) { Text("Record Payment") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            title = { Text("${c.name} \u2014 ${formatRs(c.outstanding)}") },
            text = {
                Column {
                    Text("Loans", fontWeight = FontWeight.Bold)
                    loans.forEach {
                        Text("\u2022 ${formatRs(it.amount)}  (left ${formatRs(it.outstanding)})  ${adminDateFmt.format(Date(it.createdAt))}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (payments.isNotEmpty()) {
                        Text("Payments", fontWeight = FontWeight.Bold)
                        payments.forEach {
                            Text("\u2022 ${formatRs(it.amount)}  ${adminDateFmt.format(Date(it.paidAt))}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = payAmount,
                        onValueChange = { payAmount = it.filter { c2 -> c2.isDigit() || c2 == '.' } },
                        label = { Text("Payment amount (\u20B9)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

/* ----------------------------------------------------------------- Spending */

@Composable
internal fun SpendingSection(vm: SpendingViewModel = hiltViewModel()) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val spends by vm.spends.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShopSpend?>(null) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Spend") }
        PeriodTabsRow(tab, vm::setTab)
        SummaryRow("Spent" to formatRs(total), "Entries" to spends.size.toString())
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(spends, key = { it.id }) { spend ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = spend }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(spend.name, fontWeight = FontWeight.Medium)
                            Text(adminDateFmt.format(Date(spend.spentAt)), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatRs(spend.amount))
                        IconButton(onClick = { vm.delete(spend) }) { Icon(Icons.Filled.Delete, "Delete", tint = LoanColor) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        SpendDialog(null, onDismiss = { showAdd = false }, onSave = { name, amt -> vm.add(name, amt, System.currentTimeMillis()); showAdd = false })
    }
    editing?.let { spend ->
        SpendDialog(spend, onDismiss = { editing = null }, onSave = { name, amt ->
            vm.update(spend.copy(name = name, amount = amt)); editing = null
        })
    }
}

@Composable
private fun SpendDialog(existing: ShopSpend?, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.let { formatQty(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { val a = amount.toDoubleOrNull() ?: 0.0; if (name.isNotBlank() && a > 0) onSave(name.trim(), a) },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (existing == null) "Add Spend" else "Edit Spend") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item / reason") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (\u20B9)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

/* ----------------------------------------------------------------- Analysis */

@Composable
internal fun AnalysisSection(vm: AnalysisViewModel = hiltViewModel()) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
        PeriodTabsRow(tab, vm::setTab)
        SummaryRow("Revenue" to formatRs(state.revenue), "Spending" to formatRs(state.spending))
        SummaryRow("Net" to formatRs(state.net), "Outstanding" to formatRs(state.outstanding))
        Spacer(Modifier.height(12.dp))
        Text("Items sold", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (state.itemSales.isEmpty()) {
            Text("No sales in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val maxQty = state.itemSales.maxOf { it.totalQty }.coerceAtLeast(1.0)
            state.itemSales.take(20).forEach { sale ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sale.itemName, style = MaterialTheme.typography.bodyMedium)
                        Text("${formatQty(sale.totalQty)}  \u2022  ${formatRs(sale.totalRevenue)}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (sale.totalQty / maxQty).toFloat().coerceIn(0.05f, 1f))
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- CSV */

@Composable
internal fun CsvSection(vm: AdminViewModel = hiltViewModel()) {
    var status by remember { mutableStateOf<String?>(null) }
    var internalOk by remember { mutableStateOf<Boolean?>(null) }
    var externalOk by remember { mutableStateOf<Boolean?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingSection by remember { mutableStateOf<CsvManager.ImportSection?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }
    var sectionResults by remember {
        mutableStateOf<Map<CsvManager.ImportSection, CsvManager.SectionImportResult>>(emptyMap())
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val section = pendingSection
        pendingSection = null
        if (uri != null && section != null) {
            importing = true
            vm.importCsv(section, uri) { result ->
                sectionResults = sectionResults + (section to result)
                status = result.message
                importing = false
            }
        }
    }

    LaunchedEffect(Unit) { vm.csvHealth { i, e -> internalOk = i; externalOk = e } }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("CSV Backup", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Data auto-exports on every completed bill and spend. Room DB stays the source of truth.")
        Spacer(Modifier.height(16.dp))
        Text("Internal storage: ${health(internalOk)}")
        Text("External storage: ${health(externalOk)}")
        vm.backupPaths().forEach { path ->
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.exportCsv { ok -> status = if (ok) "Exported successfully" else "Export failed" } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export Now") }

        Spacer(Modifier.height(24.dp))
        Text("Import", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import replaces all data in the selected section. Use maligai_*_latest.csv files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        listOf(
            CsvManager.ImportSection.ITEMS to "Import Items",
            CsvManager.ImportSection.SPENDING to "Import Spending"
        ).forEach { (section, label) ->
            OutlinedButton(
                onClick = {
                    pendingSection = section
                    showReplaceWarning = true
                },
                enabled = !importing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (importing && pendingSection == section) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
            sectionResults[section]?.let { result ->
                Text(
                    result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.success) MaterialTheme.colorScheme.primary else LoanColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        status?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (showReplaceWarning) {
        val section = pendingSection
        AlertDialog(
            onDismissRequest = { showReplaceWarning = false; pendingSection = null },
            title = { Text("Replace ${section?.name?.lowercase() ?: "data"}?") },
            text = {
                Text(
                    "Importing will REPLACE all existing ${section?.name?.lowercase() ?: "data"} " +
                        "with the CSV file you select. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceWarning = false
                    filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                }) { Text("Continue", color = LoanColor) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceWarning = false; pendingSection = null }) { Text("Cancel") }
            }
        )
    }
}

private fun health(b: Boolean?): String = when (b) {
    true -> "OK"
    false -> "Not writable"
    null -> "Checking\u2026"
}

@Composable
private fun DotSpacerPicker(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { if (value > 0) onValueChange(value - 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
        }
        Text(
            "$value",
            modifier = Modifier.widthIn(min = 28.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { if (value < 10) onValueChange(value + 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
        }
    }
}

/* ----------------------------------------------------------------- Settings */

@Composable
internal fun SettingsSection(
    vm: AdminViewModel = hiltViewModel(),
    setupVm: SetupViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return

    var biometricUnlock by remember(s.biometricUnlockEnabled) { mutableStateOf(s.biometricUnlockEnabled) }
    var themeMode by remember(s.themeMode) { mutableStateOf(s.themeMode) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("App", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = biometricUnlock,
                onCheckedChange = {
                    biometricUnlock = it
                    vm.saveSettings(s.copy(biometricUnlockEnabled = it))
                }
            )
            Spacer(Modifier.width(8.dp))
            Text("Require unlock on app open")
        }
        Text(
            "Uses biometric or device PIN when available on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark"
            ).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            vm.saveSettings(s.copy(themeMode = mode))
                        }
                    )
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Handwriting language", style = MaterialTheme.typography.titleMedium)
        Text(
            "Current: ${ScriptLanguages.displayNameForTag(s.primaryScriptTag)}",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = { showLanguagePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Change handwriting language")
        }
        Text(
            "Switching clears learned corrections and may require re-downloading the regional pack.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showLanguagePicker) {
        var pick by remember(s.primaryScriptTag) {
            mutableStateOf(ScriptLanguages.byMlKitTag(s.primaryScriptTag))
        }
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        val lang = pick ?: return@Button
                        setupVm.changePrimaryLanguage(lang) { downloaded ->
                            if (!downloaded) {
                                setupVm.downloadAllModels { }
                            }
                            showLanguagePicker = false
                        }
                    },
                    enabled = pick != null
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLanguagePicker = false }) { Text("Cancel") } },
            title = { Text("Change handwriting language") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ScriptLanguages.pickerOptions().forEach { lang ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = pick?.id == lang.id,
                                onClick = { pick = lang }
                            )
                            Column {
                                Text(lang.displayName)
                                lang.sharedModelNote?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/* ----------------------------------------------------------------- GST & Receipt */

@Composable
internal fun ReceiptSection(vm: AdminViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fields by vm.receiptFields.collectAsStateWithLifecycle()
    val s = settings ?: return

    var gstEnabled by remember(s.gstEnabled) { mutableStateOf(s.gstEnabled) }
    var gstPercent by remember(s.gstPercent) {
        mutableStateOf(if (s.gstPercent > 0) formatQty(s.gstPercent) else formatQty(s.cgstPercent + s.sgstPercent))
    }
    var cgst by remember(s.cgstPercent) { mutableStateOf(formatQty(s.cgstPercent)) }
    var sgst by remember(s.sgstPercent) { mutableStateOf(formatQty(s.sgstPercent)) }
    var gstCustomSplit by remember(s.gstPercent, s.cgstPercent, s.sgstPercent) {
        mutableStateOf(
            s.gstPercent > 0 && kotlin.math.abs(s.cgstPercent - s.gstPercent / 2.0) > 0.01
        )
    }
    var dotsTop by remember(s.receiptDotsTop) { mutableStateOf(s.receiptDotsTop) }
    var dotsBottom by remember(s.receiptDotsBottom) { mutableStateOf(s.receiptDotsBottom) }
    var localImage by remember(s.receiptNameMode) { mutableStateOf(s.usesLocalScriptReceipt()) }
    val canLocalReceipt = ScriptLanguages.supportsLocalScriptReceipt(s.primaryScriptTag)
    var footer by remember(s.footerText) { mutableStateOf(s.footerText) }
    var showAddField by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("GST", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = gstEnabled, onCheckedChange = { gstEnabled = it })
            Spacer(Modifier.width(8.dp))
            Text("Enable GST (prices are GST-inclusive)")
        }
        if (gstEnabled) {
            OutlinedTextField(
                value = gstPercent,
                onValueChange = { input ->
                    val filtered = input.filter { c -> c.isDigit() || c == '.' }
                    val value = filtered.toDoubleOrNull()
                    if (value == null || value <= 100) {
                        gstPercent = filtered
                        if (!gstCustomSplit && value != null) {
                            val half = value / 2.0
                            cgst = formatQty(half)
                            sgst = formatQty(half)
                        }
                    }
                },
                label = { Text("GST %") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = gstCustomSplit, onCheckedChange = { gstCustomSplit = it })
                Spacer(Modifier.width(8.dp))
                Text("Custom CGST/SGST split")
            }
            if (gstCustomSplit) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cgst,
                        onValueChange = { input ->
                            val filtered = input.filter { c -> c.isDigit() || c == '.' }
                            cgst = filtered
                            val total = gstPercent.toDoubleOrNull() ?: 0.0
                            val c = filtered.toDoubleOrNull() ?: 0.0
                            sgst = formatQty((total - c).coerceAtLeast(0.0))
                        },
                        label = { Text("CGST %") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sgst,
                        onValueChange = { },
                        label = { Text("SGST %") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Paper spacing", style = MaterialTheme.typography.titleMedium)
        DotSpacerPicker("Dots above header", dotsTop) { dotsTop = it }
        DotSpacerPicker("Dots below footer", dotsBottom) { dotsBottom = it }

        Spacer(Modifier.height(16.dp))
        Text("Receipt item names", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = localImage,
                onCheckedChange = { if (canLocalReceipt) localImage = it },
                enabled = canLocalReceipt
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (localImage && canLocalReceipt) "Local script (printed as image)"
                else "English / transliteration"
            )
        }
        if (!canLocalReceipt) {
            Text(
                "Local script receipt printing is available for Tamil shops in this version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = footer, onValueChange = { footer = it }, label = { Text("Footer text") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)

        Spacer(Modifier.height(16.dp))
        Text("Header fields", style = MaterialTheme.typography.titleMedium)
        fields.forEach { field ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(field.label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(field.value, fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = field.enabled, onCheckedChange = { vm.updateReceiptField(field.copy(enabled = it)) })
                    IconButton(onClick = { vm.deleteReceiptField(field) }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = LoanColor)
                    }
                }
            }
        }
        OutlinedButton(onClick = { showAddField = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add header field") }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.saveSettings(
                    s.copy(
                        gstEnabled = gstEnabled,
                        gstPercent = gstPercent.toDoubleOrNull() ?: (cgst.toDoubleOrNull() ?: 0.0) + (sgst.toDoubleOrNull() ?: 0.0),
                        cgstPercent = cgst.toDoubleOrNull() ?: 0.0,
                        sgstPercent = sgst.toDoubleOrNull() ?: 0.0,
                        receiptDotsTop = dotsTop.coerceIn(0, 10),
                        receiptDotsBottom = dotsBottom.coerceIn(0, 10),
                        receiptNameMode = if (localImage && canLocalReceipt) {
                            ReceiptNameMode.LOCAL_IMAGE
                        } else {
                            ReceiptNameMode.ENGLISH
                        },
                        footerText = footer
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Settings") }

        Spacer(Modifier.height(12.dp))
        ReceiptPreview(fields, footer)
    }

    if (showAddField) {
        var label by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddField = false },
            confirmButton = {
                Button(onClick = { if (label.isNotBlank() && value.isNotBlank()) { vm.addReceiptField(label, value); showAddField = false } }) {
                    Text("Add")
                }
            },
            dismissButton = { TextButton(onClick = { showAddField = false }) { Text("Cancel") } },
            title = { Text("Add header field") },
            text = {
                Column {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. Shop Name)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, singleLine = true)
                }
            }
        )
    }
}

@Composable
private fun ReceiptPreview(fields: List<ReceiptField>, footer: String) {
    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Receipt preview", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            fields.filter { it.enabled }.forEach { Text(it.value, color = Color.Black, fontWeight = FontWeight.Bold) }
            Text("------------------------------", color = Color.Black)
            Text("Item               qty   amount", color = Color.Black, fontSize = 12.sp)
            Text("------------------------------", color = Color.Black)
            if (footer.isNotBlank()) Text(footer, color = Color.Black)
        }
    }
}

/* ----------------------------------------------------------------- Printer */

@Composable
internal fun PrinterSection(vm: PrinterViewModel = hiltViewModel()) {
    val connected by vm.connected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var devices by remember { mutableStateOf<List<PrinterDevice>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val s = settings

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(if (connected) Color(0xFF2E7D32) else LoanColor, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(8.dp))
            Text(if (connected) "Connected" else "Not connected", style = MaterialTheme.typography.titleMedium)
        }
        s?.printerName?.takeIf { it.isNotBlank() }?.let { Text("Saved printer: $it") }
        Spacer(Modifier.height(12.dp))

        Button(onClick = { devices = vm.pairedDevices(); if (devices.isEmpty()) message = "No paired Bluetooth devices. Pair in Android settings first." },
            modifier = Modifier.fillMaxWidth()) { Text("Scan paired devices") }
        Spacer(Modifier.height(8.dp))
        devices.forEach { device ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                vm.connect(device) { message = it.message }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(device.name, fontWeight = FontWeight.Medium)
                    Text(device.mac, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Paper width", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(58, 80).forEach { mm ->
                FilterChip(
                    selected = (s?.paperWidthMm ?: 80) == mm,
                    onClick = { vm.savePaperWidth(mm) },
                    label = { Text("${mm}mm") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s?.rupeeFix ?: false, onCheckedChange = { vm.saveRupeeFix(it) })
            Spacer(Modifier.width(8.dp))
            Text("\u20B9 symbol fix (WPC1252)")
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { vm.forget() }, modifier = Modifier.fillMaxWidth()) { Text("Forget printer") }

        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
    }
}
