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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(onClick = onPrint) { Text("Print") }
                Button(onClick = onUpdate) { Text("Update") }
            }
        },
        title = {
            Column {
                Text(
                    bill.name + if (bill.isLoan) "  (kadan)" else "",
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
                        val qtyLabel = if (line.showsQtyBreakdown()) {
                            "${formatQty(line.quantity)} ${line.unitLabel}"
                        } else ""
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
                        "Subtotal ${formatRs(bill.subtotal)}  CGST ${formatRs(bill.cgst)}  SGST ${formatRs(bill.sgst)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Total ${formatRs(bill.total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
