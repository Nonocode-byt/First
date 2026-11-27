package com.copy2word.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private var pendingDoc: ByteArray? = null
    private var pendingName = "Copy2Word.docx"
    private val prefs by lazy { getSharedPreferences("copy2word", Context.MODE_PRIVATE) }
    private lateinit var folderLauncher: androidx.activity.result.ActivityResultLauncher<Uri?>
    private lateinit var exportLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    prefs.edit().putString("folder_uri", uri.toString()).apply()
                    Toast.makeText(this, "تم اختيار مجلد الحفظ", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "تعذر حفظ اختيار المجلد", Toast.LENGTH_LONG).show()
                }
            }
        }
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )) { uri: Uri? -> if (uri != null) writeDoc(uri) }
        setContent {
            Copy2WordScreen(
                folderChosen = prefs.getString("folder_uri", null) != null,
                onChooseFolder = { folderLauncher.launch(null) },
                onExport = { name, text ->
                    pendingName = safeName(name)
                    pendingDoc = DocxWriter.create(text)
                    val savedTree = prefs.getString("folder_uri", null)?.let(Uri::parse)
                    if (savedTree != null) saveIntoChosenFolder(savedTree)
                    else exportLauncher.launch(pendingName)
                }
            )
        }
    }

    private fun saveIntoChosenFolder(treeUri: Uri) {
        try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
            val uri = DocumentsContract.createDocument(
                contentResolver, parentDocumentUri,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                pendingName
            )
            if (uri != null) writeDoc(uri)
            else Toast.makeText(this, "تعذر إنشاء الملف في المجلد المختار", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر الحفظ؛ اختر مجلدًا آخر", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeDoc(uri: Uri) {
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
            output.use { it.write(pendingDoc ?: byteArrayOf()) }
            Toast.makeText(this, "تم حفظ ملف Word بنجاح", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally { pendingDoc = null }
    }

    private fun safeName(value: String): String {
        val cleaned = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return (cleaned.ifBlank { "Copy2Word" }).let {
            if (it.endsWith(".docx", true)) it else "$it.docx"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Copy2WordScreen(
    folderChosen: Boolean,
    onChooseFolder: () -> Unit,
    onExport: (String, String) -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("مستند جديد") }
    val snippets = remember { mutableStateListOf<String>() }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF245BDB), background = Color(0xFFF5F7FC), surface = Color.White)) {
        Scaffold(containerColor = Color(0xFFF5F7FC)) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text("Copy2Word", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B4D))
                        Text("من الموبايل إلى Word في ثواني", color = Color(0xFF68758B), fontSize = 14.sp)
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("النص الجديد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = draft, onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                                placeholder = { Text("الصق هنا أي نص نسخته من واتساب أو أي تطبيق...") },
                                minLines = 5
                            )
                            OutlinedButton(onClick = {
                                val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (copied.isNotBlank()) draft = copied
                                else Toast.makeText(context, "مفيش نص منسوخ في Clipboard", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Text("لصق من Clipboard") }
                            OutlinedButton(onClick = {
                                if (draft.isBlank()) {
                                    Toast.makeText(context, "الصق الرسائل الأول", Toast.LENGTH_SHORT).show()
                                } else {
                                    draft = formatWhatsAppReport(draft)
                                    Toast.makeText(context, "تم تنسيق التقرير بدون ترقيم للأسطر", Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("تنسيق التقرير") }
                            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                                Text(if (folderChosen) "تغيير مجلد الحفظ" else "اختيار مجلد الحفظ")
                            }
                            Text(
                                if (folderChosen) "مكان الحفظ: المجلد الذي اخترته" else "مكان الحفظ: اختر مجلدًا أو حدده وقت الحفظ",
                                color = Color(0xFF68758B), fontSize = 12.sp
                            )
                            OutlinedTextField(value = fileName, onValueChange = { fileName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم ملف Word") })
                            Button(onClick = {
                                if (draft.isBlank()) Toast.makeText(context, "الصق نص الأول", Toast.LENGTH_SHORT).show()
                                else onExport(fileName, draft)
                            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ النص كملف Word مستقل") }
                            OutlinedButton(onClick = {
                                if (draft.isNotBlank()) {
                                    snippets.add(draft.trim()); draft = ""
                                    Toast.makeText(context, "اتضاف النص للتجميعة", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, "الصق نص الأول", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Text("إضافة النص إلى ملف مجمّع") }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("التجميعة الحالية", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${snippets.size} نص محفوظ مؤقتًا", color = Color(0xFF68758B), fontSize = 12.sp)
                        }
                        TextButton(onClick = { snippets.clear() }, enabled = snippets.isNotEmpty()) { Text("مسح الكل") }
                    }
                }
                if (snippets.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(20.dp), contentAlignment = Alignment.Center) { Text("أي نص تضيفه هنا هيتجمع في ملف Word واحد.", color = Color(0xFF68758B)) } }
                } else {
                    itemsIndexed(snippets) { index, item ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = { snippets.removeAt(index) }) { Text("حذف") }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = {
                        if (snippets.isNotEmpty()) onExport("مستند مجمع", snippets.joinToString("\n\n--------------------\n\n"))
                        else Toast.makeText(context, "أضف نص واحد على الأقل للتجميعة", Toast.LENGTH_SHORT).show()
                    }, enabled = snippets.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("حفظ كل النصوص في ملف Word واحد") }
                }
            }
        }
    }
}

private fun formatWhatsAppReport(raw: String): String {
    val headerPatterns = listOf(
        Regex("""^\s*\[[^\]]+\]\s*.*?:\s?(.*)$"""),
        Regex("""^\s*\d{1,2}[/-]\d{1,2}[/-]\d{2,4},?\s+\d{1,2}:\d{2}\s*(?:AM|PM|ص|م)?\s*-\s*[^:]+:\s?(.*)$""", RegexOption.IGNORE_CASE)
    )
    val cleaned = mutableListOf<String>()
    var current = StringBuilder()

    fun finishMessage() {
        val value = current.toString().trim()
        if (value.isNotBlank()) cleaned.add(value)
        current = StringBuilder()
    }

    raw.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { original ->
        val line = original.trimEnd()
        val match = headerPatterns.firstNotNullOfOrNull { it.matchEntire(line) }
        if (match != null) {
            finishMessage()
            val body = match.groupValues[1].trim()
            if (body.isNotBlank()) current.append(body)
        } else if (line.isBlank()) {
            if (current.isNotEmpty()) current.append('\n')
        } else {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
    }
    finishMessage()

    val groups = linkedMapOf(
        "أعمال الكهرباء" to mutableListOf<String>(),
        "أعمال الزراعة والتجميل" to mutableListOf<String>(),
        "أعمال الري والمياه" to mutableListOf<String>(),
        "أعمال النظافة ورفع المخلفات" to mutableListOf<String>(),
        "أعمال أخرى" to mutableListOf<String>()
    )

    cleaned.forEach { message ->
        val value = message.lowercase()
        val category = when {
            listOf("كشاف", "كهرب", "شبكه", "شبكة", "كابل", "عمود", "أعمدة", "اناره", "إنارة", "بورن", "صوديوم", "ليد").any { value.contains(it) } ->
                "أعمال الكهرباء"
            listOf("رى ", "ري ", "مياه", "عداد", "أحواض", "احواض", "رى", "ري").any { value.contains(it) } ->
                "أعمال الري والمياه"
            listOf("مخلفات", "نظافة", "نظافه", "تحميل").any { value.contains(it) } ->
                "أعمال النظافة ورفع المخلفات"
            listOf("شجر", "أشجار", "اشجار", "نخيل", "نخله", "نخلة", "فيكس", "أسيجة", "اسيجه", "قص الستارة", "تقليم", "تهذيب").any { value.contains(it) } ->
                "أعمال الزراعة والتجميل"
            else -> "أعمال أخرى"
        }
        groups.getValue(category).add(message)
    }

    return buildString {
        append("تقرير الأعمال\n\n")
        groups.forEach { (heading, messages) ->
            if (messages.isNotEmpty()) {
                append(heading).append('\n')
                messages.forEach { message ->
                    append(message).append('\n')
                }
                append('\n')
            }
        }
    }.trim()
}

private object DocxWriter {
    fun create(text: String): ByteArray {
        val paragraphs = text.split("\n").joinToString("") { line ->
            val escaped = xmlEscape(line)
            """<w:p><w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr><w:r><w:rPr><w:rtl/><w:lang w:val="ar-EG"/></w:rPr><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>"""
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>"""
        val types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", types); entry("_rels/.rels", rels); entry("word/document.xml", document)
        }
        return output.toByteArray()
    }
    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
