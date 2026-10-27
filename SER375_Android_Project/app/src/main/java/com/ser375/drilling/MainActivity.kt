package com.ser375.drilling

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RunEntry(
    val id: Long = 0,
    val reportDate: String,
    val shift: String,
    val runNo: Int,
    val depthFrom: Double,
    val depthTo: Double,
    val bit: String,
    val casingSize: String,
    val cr: String,
    val rqd: String,
    val startTime: String,
    val endTime: String
)

class ReportDb(ctx: Context) : SQLiteOpenHelper(ctx, "ser375.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE runs (
            id INTEGER PRIMARY KEY AUTOINCREMENT, reportDate TEXT, shift TEXT, runNo INTEGER,
            depthFrom REAL, depthTo REAL, bit TEXT, casingSize TEXT, cr TEXT, rqd TEXT,
            startTime TEXT, endTime TEXT)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(e: RunEntry) {
        val v = ContentValues().apply {
            put("reportDate",e.reportDate); put("shift",e.shift); put("runNo",e.runNo)
            put("depthFrom",e.depthFrom); put("depthTo",e.depthTo); put("bit",e.bit)
            put("casingSize",e.casingSize); put("cr",e.cr); put("rqd",e.rqd)
            put("startTime",e.startTime); put("endTime",e.endTime)
        }
        writableDatabase.insert("runs", null, v)
    }
    fun all(): List<RunEntry> {
        val out=mutableListOf<RunEntry>()
        readableDatabase.rawQuery("SELECT * FROM runs ORDER BY reportDate, runNo",null).use { c ->
            while(c.moveToNext()) out += RunEntry(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),
                c.getDouble(4),c.getDouble(5),c.getString(6),c.getString(7),c.getString(8),
                c.getString(9),c.getString(10),c.getString(11))
        }
        return out
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var db: ReportDb
    private var nextRun = 1
    private var lastDepth = 0.0
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ReportDb(this)
        val today = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
        val root = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18)
        }
        root.addView(TextView(this).apply {
            text="SER-375  |  DRILLING DAILY REPORT"; textSize=20f; gravity=Gravity.CENTER
        }, LinearLayout.LayoutParams(-1,-2))
        root.addView(TextView(this).apply {
            text="Offline data entry • Excel-like run grid"; textSize=13f; gravity=Gravity.CENTER
        })
        val date = field("Report date (yyyy.MM.dd)", today); root.addView(date)
        val shift = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("DAY","NIGHT")) }
        root.addView(label("SHIFT")); root.addView(shift)
        val run = field("RUN", "1"); root.addView(run)
        val from = field("DEPTH FROM", "0"); root.addView(from)
        val to = field("DEPTH TO", ""); root.addView(to)
        val bit = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("NQ","HQ")) }
        root.addView(label("BIT")); root.addView(bit)
        root.addView(TextView(this).apply { text="NQ: hole 74.6 mm / core 47.5 mm   •   HQ: hole 96.3 mm / core 53.5 mm"; textSize=12f })
        val casing = field("CASING SIZE", ""); root.addView(casing)
        val cr = field("CR (%)", ""); root.addView(cr)
        val rqd = field("RQD (%)", ""); root.addView(rqd)
        val start = field("START TIME (24h)", "07:00"); root.addView(start)
        val end = field("END TIME (24h)", "19:00"); root.addView(end)
        val save = Button(this).apply { text="SAVE RUN" }; root.addView(save)
        val export = Button(this).apply { text="EXPORT MONTHLY EXCEL" }; root.addView(export)
        status = TextView(this).apply { text="Saved reports stay on this device."; textSize=13f }; root.addView(status)
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        val existing = db.all().filter { it.reportDate == today }
        if(existing.isNotEmpty()) {
            val latest=existing.maxByOrNull { it.runNo }!!
            nextRun=latest.runNo+1
            lastDepth=latest.depthTo
            run.setText(nextRun.toString())
            from.setText(lastDepth.toString())
        }
        save.setOnClickListener {
            try {
                val depthFrom=from.text.toString().toDouble()
                val depthTo=to.text.toString().toDouble()
                require(depthTo >= depthFrom) { "DEPTH TO must be >= DEPTH FROM." }
                val reportDate = date.text.toString().trim()
                require(reportDate.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}"))) { "Use date format yyyy.MM.dd, for example 1405.07.01." }
                require(start.text.toString().matches(Regex("\\d{2}:\\d{2}")) && end.text.toString().matches(Regex("\\d{2}:\\d{2}"))) { "Use 24-hour time HH:mm." }
                require(start.text.toString().substring(0,2).toInt() in 0..23 && start.text.toString().substring(3,5).toInt() in 0..59) { "Invalid START TIME." }
                require(end.text.toString().substring(0,2).toInt() in 0..23 && end.text.toString().substring(3,5).toInt() in 0..59) { "Invalid END TIME." }
                require(cr.text.toString().isBlank() || (cr.text.toString().toDoubleOrNull()?.let { it in 0.0..100.0 } == true)) { "CR must be between 0 and 100." }
                require(rqd.text.toString().isBlank() || (rqd.text.toString().toDoubleOrNull()?.let { it in 0.0..100.0 } == true)) { "RQD must be between 0 and 100." }
                val e=RunEntry(reportDate=reportDate,shift=shift.selectedItem.toString(),
                    runNo=run.text.toString().toInt(),depthFrom=depthFrom,depthTo=depthTo,
                    bit=bit.selectedItem.toString(),casingSize=casing.text.toString(),cr=cr.text.toString(),
                    rqd=rqd.text.toString(),startTime=start.text.toString(),endTime=end.text.toString())
                db.add(e); nextRun=e.runNo+1; lastDepth=e.depthTo
                run.setText(nextRun.toString()); from.setText(lastDepth.toString()); to.setText("")
                cr.setText(""); rqd.setText("")
                status.text="Saved RUN ${e.runNo}. Next RUN: $nextRun • DEPTH FROM: $lastDepth"
            } catch(ex: Exception) { Toast.makeText(this, ex.message ?: "Check the entered values.", Toast.LENGTH_LONG).show() }
        }
        export.setOnClickListener { exportWorkbook() }
    }
    private fun label(s:String)=TextView(this).apply { text=s; textSize=12f; setPadding(2,5,2,0) }
    private fun field(hintText:String, value:String): EditText = EditText(this).apply {
        hint=hintText; setSingleLine(true); setText(value); textSize=15f
        layoutParams=LinearLayout.LayoutParams(-1, (44*resources.displayMetrics.density).toInt())
    }
    private fun exportWorkbook() {
        try {
            val entries = db.all()
            if (entries.isEmpty()) {
                status.text = "No runs saved yet."
                return
            }

            val templateFile = File(cacheDir, "SER-375_Template.xlsx")
            assets.open("SER-375_Template.xlsx").use { input ->
                templateFile.outputStream().use { input.copyTo(it) }
            }
            val workbook = WorkbookFactory.create(templateFile)

            // Keep the original template sheet untouched as the master.
            // One sheet is created for each report date, based on the original layout.
            val templateSheet = workbook.getSheetAt(0)
            val entriesByDate = entries.groupBy { it.reportDate }
            entriesByDate.forEach { (date, dayEntries) ->
                val safeName = date.take(31).replace(Regex("[\\\\/*?:\\[\\]]"), "_")
                val existingIndex = workbook.getSheetIndex(safeName)
                val sheet = if (existingIndex >= 0) workbook.getSheetAt(existingIndex)
                            else workbook.cloneSheet(0, safeName)

                // Clear only input rows in the cloned daily sheet; retain formatting and formulas.
                (8..18).plus(20..28).forEach { rowNum ->
                    val row = sheet.getRow(rowNum - 1) ?: sheet.createRow(rowNum - 1)
                    for (col in 0..25) {
                        val cell = row.getCell(col)
                        if (cell != null) {
                            cell.setCellType(org.apache.poi.ss.usermodel.CellType.BLANK)
                        }
                    }
                }

                // The source sheet has 11 DAY slots (rows 8-18) and 9 NIGHT slots (20-28).
                // Store runs by shift in the exact input rows used by the original template.
                val dayRuns = dayEntries.filter { it.shift.equals("DAY", true) }.sortedBy { it.runNo }
                val nightRuns = dayEntries.filter { it.shift.equals("NIGHT", true) }.sortedBy { it.runNo }
                require(dayRuns.size <= 11) { "$date has more than 11 DAY runs; template has only 11 rows." }
                require(nightRuns.size <= 9) { "$date has more than 9 NIGHT runs; template has only 9 rows." }

                fun put(rowNumber: Int, colNumber: Int, value: Any?) {
                    val row = sheet.getRow(rowNumber - 1) ?: sheet.createRow(rowNumber - 1)
                    val cell = row.getCell(colNumber - 1) ?: row.createCell(colNumber - 1)
                    when (value) {
                        null -> cell.setBlank()
                        is Int -> cell.setCellValue(value.toDouble())
                        is Double -> cell.setCellValue(value)
                        is String -> cell.setCellValue(value)
                        else -> cell.setCellValue(value.toString())
                    }
                }

                fun fillRun(e: RunEntry, row: Int) {
                    put(row, 1, e.reportDate)            // A: Date
                    put(row, 2, e.shift.uppercase())     // B: Shift
                    put(row, 4, e.runNo)                 // D: Run
                    put(row, 5, e.depthFrom)             // E: Depth From
                    put(row, 6, e.depthTo)               // F: Depth To
                    put(row, 7, e.startTime)             // G: Start
                    put(row, 8, e.endTime)               // H: Finish
                    e.cr.toDoubleOrNull()?.let { put(row, 9, it) }   // I: CR
                    e.rqd.toDoubleOrNull()?.let { put(row, 10, it) } // J: RQD
                    put(row, 14, "DB")                   // N: Bit Type default from template
                    put(row, 15, e.bit.uppercase())      // O: Bit Size selector HQ/NQ
                    put(row, 16, if (e.bit.equals("HQ", true)) 96.3 else 74.6) // P: user-confirmed hole diameter
                    put(row, 17, if (e.bit.equals("HQ", true)) 53.5 else 47.5) // Q: user-confirmed core diameter
                    if (e.casingSize.isNotBlank()) put(row, 18, e.casingSize) // R: Casing Size
                    // Derived fields matching the template headings: K=L(cm), L=elapsed minutes, M=L/T.
                    val excelRow = row
                    val kCell = sheet.getRow(excelRow - 1).getCell(10) ?: sheet.getRow(excelRow - 1).createCell(10)
                    kCell.cellFormula = "IF(OR(E$excelRow=\"\",F$excelRow=\"\"),\"\",(F$excelRow-E$excelRow)*100)"
                    val lCell = sheet.getRow(excelRow - 1).getCell(11) ?: sheet.getRow(excelRow - 1).createCell(11)
                    lCell.cellFormula = "IF(OR(G$excelRow=\"\",H$excelRow=\"\"),\"\",MOD(TIMEVALUE(H$excelRow)-TIMEVALUE(G$excelRow),1)*1440)"
                    val mCell = sheet.getRow(excelRow - 1).getCell(12) ?: sheet.getRow(excelRow - 1).createCell(12)
                    mCell.cellFormula = "IF(OR(K$excelRow=\"\",L$excelRow=\"\",L$excelRow=0),\"\",K$excelRow/L$excelRow)"
                    put(row, 20, 0.7)                    // T: template default
                    put(row, 21, "GRAY")                 // U: template default
                    put(row, 22, "S.MIX")                // V: template default
                }
                dayRuns.forEachIndexed { idx, e -> fillRun(e, 8 + idx) }
                nightRuns.forEachIndexed { idx, e -> fillRun(e, 20 + idx) }
            }

            val month = entries.last().reportDate.take(7).replace('.', '_')
            val fileName = "SER375_Monthly_$month.xlsx"
            val tempOut = File(cacheDir, fileName)
            FileOutputStream(tempOut).use { workbook.write(it) }
            workbook.close()

            // Save a user-accessible copy in Downloads on Android 10+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create file in Downloads.")
                contentResolver.openOutputStream(uri).use { output ->
                    requireNotNull(output) { "Could not open output stream." }
                    tempOut.inputStream().use { input -> input.copyTo(output) }
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
                status.text = "Excel saved to Downloads/$fileName"
            } else {
                val out = File(getExternalFilesDir(null), fileName)
                tempOut.copyTo(out, overwrite = true)
                status.text = "Excel saved: ${out.absolutePath}"
            }
            Toast.makeText(this, "Monthly Excel exported.", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            status.text = "Export failed: ${ex.message}"
            Toast.makeText(this, "Export error: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}
