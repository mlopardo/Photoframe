package com.zambiotica.photoframe

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Busca las fotos, ya sea en una carpeta elegida con el selector del sistema (SAF)
 * o en la carpeta fija del portarretrato.
 */
object PhotoScanner {

    /** Carpeta fija que usa la TabZambiótica y que llena el proyecto P2 desde Immich. */
    const val DEFAULT_DIR = "/sdcard/Portarretrato"

    private const val MAX_DEPTH = 3

    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Filtro de nombre de archivo. Kotlin puro: lo cubre el test del CI. */
    fun isSupported(name: String): Boolean {
        if (name.startsWith(".")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in EXTENSIONS
    }

    data class Result(val photos: List<Uri>, val fingerprint: String)

    fun scan(context: Context): Result {
        val treeUri = Prefs.folderUri(context)
        val includeSub = Prefs.subfolders(context)
        return if (treeUri != null) {
            scanTree(context, Uri.parse(treeUri), includeSub)
        } else {
            scanDir(File(DEFAULT_DIR), includeSub)
        }
    }

    private fun scanTree(context: Context, treeUri: Uri, includeSub: Boolean): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val out = ArrayList<Uri>()
        val fp = StringBuilder()
        if (root != null && root.isDirectory) walkTree(root, includeSub, 0, out, fp)
        return Result(out.sortedBy { it.toString() }, fp.toString().hashCode().toString())
    }

    private fun walkTree(
        dir: DocumentFile,
        includeSub: Boolean,
        depth: Int,
        out: MutableList<Uri>,
        fp: StringBuilder
    ) {
        if (depth > MAX_DEPTH) return
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (includeSub) walkTree(child, true, depth + 1, out, fp)
            } else if (isSupported(name)) {
                out.add(child.uri)
                fp.append(name).append(child.length()).append(child.lastModified()).append('|')
            }
        }
    }

    private fun scanDir(dir: File, includeSub: Boolean): Result {
        val out = ArrayList<Uri>()
        val fp = StringBuilder()
        if (dir.isDirectory) walkDir(dir, includeSub, 0, out, fp)
        val sorted = out.sortedBy { it.toString() }
        return Result(sorted, fp.toString().hashCode().toString())
    }

    private fun walkDir(
        dir: File,
        includeSub: Boolean,
        depth: Int,
        out: MutableList<Uri>,
        fp: StringBuilder
    ) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (includeSub) walkDir(child, true, depth + 1, out, fp)
            } else if (isSupported(child.name)) {
                out.add(Uri.fromFile(child))
                fp.append(child.name).append(child.length()).append(child.lastModified()).append('|')
            }
        }
    }
}
