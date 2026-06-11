package com.maligai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.maligai.app.localization.LocalAppLocale
import com.maligai.app.localization.StringKey
import com.maligai.app.localization.string
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)

@Composable
fun BillDetailDialog(
    bill: Bill,
    items: List<BillItem>,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onPrint: () -> Unit
) {
    val localeTag = LocalAppLocale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(string(StringKey.Close)) }
                Button(onClick = onPrint) { Text(string(StringKey.Print)) }
                Button(onClick = onUpdate) { Text(string(StringKey.Update)) }
            }
        },
        title = {
            Column {
                Text(
                    bill.name + if (bill.isLoan) string(StringKey.KadanSuffix) else "",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    detailDateFmt.format(Date(bill.completedAt ?: bill.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                items.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val qtyLabel = if (line.showsQtyBreakdown()) line.displayQuantityLabel(localeTag) else ""
                        Text(
                            if (qtyLabel.isNotBlank()) "${line.itemName} ($qtyLabel)"
                            else line.itemName,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatRs(line.lineTotal))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (bill.cgst + bill.sgst > 0) {
                    Text(
                        string(
                            StringKey.SubtotalCgstSgst,
                            formatRs(bill.subtotal),
                            formatRs(bill.cgst),
                            formatRs(bill.sgst)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    string(StringKey.TotalLabel, formatRs(bill.total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun LoanBillDetailDialog(
    detail: LoanBillDetail,
    onDismiss: () -> Unit
) {
    val localeTag = LocalAppLocale.current
    val loan = detail.loan
    val bill = detail.bill
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(string(StringKey.Close)) }
        },
        title = {
            Column {
                Text(detail.customerName, fontWeight = FontWeight.Bold)
                Text(
                    detailDateFmt.format(Date(loan.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    string(StringKey.OutstandingSlash, formatRs(loan.outstanding), formatRs(loan.amount)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LoanColor
                )
            }
        },
        text = {
            Column {
                if (bill == null) {
                    Text(
                        string(StringKey.BillNoLongerAvailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (detail.items.isEmpty()) {
                    Text(
                        string(StringKey.NoItemsForLoan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    detail.items.forEach { line ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(line.itemName, fontWeight = FontWeight.Medium)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val qtyLabel = if (line.showsQtyBreakdown()) line.displayQuantityLabel(localeTag)
                                else line.displayQuantityLabel(localeTag).takeIf { it.isNotBlank() } ?: "\u2014"
                                Text(qtyLabel, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatRs(line.unitPrice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(formatRs(line.lineTotal), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    if (bill.cgst + bill.sgst > 0) {
                        Text(
                            string(
                                StringKey.SubtotalCgstSgst,
                                formatRs(bill.subtotal),
                                formatRs(bill.cgst),
                                formatRs(bill.sgst)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        string(StringKey.BillTotalLabel, formatRs(bill.total)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
