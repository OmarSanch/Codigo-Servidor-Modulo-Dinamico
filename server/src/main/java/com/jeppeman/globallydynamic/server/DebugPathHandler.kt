package com.jeppeman.globallydynamic.server

import com.google.gson.Gson
import org.eclipse.jetty.http.HttpStatus
import java.io.File
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DebugPathHandler(
    private val storageBackend: StorageBackend,
    private val logger: Logger,
    private val gson: Gson
) : PathHandler {
    override val path: String = "debug/storage"
    override val authRequired: Boolean = false

    override fun handle(request: HttpServletRequest?, response: HttpServletResponse?) {
        val debugInfo = mutableMapOf<String, Any>()

        when (storageBackend) {
            is LocalStorageBackend -> {
                val basePath = storageBackend.baseStoragePath
                val baseFile = basePath.toFile()

                debugInfo["storageType"] = "LocalStorageBackend"
                debugInfo["basePath"] = basePath.toAbsolutePath().toString()
                debugInfo["basePathExists"] = baseFile.exists()
                debugInfo["basePathIsDirectory"] = baseFile.isDirectory

                if (baseFile.exists() && baseFile.isDirectory) {
                    debugInfo["structure"] = buildDirectoryTree(baseFile, 0, 3)
                }
            }
            else -> {
                debugInfo["storageType"] = storageBackend.javaClass.simpleName
                debugInfo["message"] = "No debug info available for this storage type"
            }
        }

        response?.apply {
            status = HttpStatus.OK_200
            contentType = "application/json; charset=UTF-8"
            writer.write(gson.toJson(debugInfo))
            writer.flush()
        }
    }

    private fun buildDirectoryTree(dir: File, level: Int, maxLevel: Int): Map<String, Any> {
        if (level >= maxLevel) {
            return mapOf("message" to "Max depth reached")
        }

        val result = mutableMapOf<String, Any>()
        result["path"] = dir.absolutePath
        result["name"] = dir.name

        val files = dir.listFiles()
        if (files != null) {
            val children = mutableListOf<Map<String, Any>>()
            files.sortedBy { it.name }.forEach { file ->
                val childInfo = mutableMapOf<String, Any>()
                childInfo["name"] = file.name
                childInfo["type"] = if (file.isDirectory) "directory" else "file"
                childInfo["size"] = file.length()

                if (file.isDirectory && level < maxLevel - 1) {
                    childInfo["children"] = buildDirectoryTree(file, level + 1, maxLevel)
                }

                children.add(childInfo)
            }
            result["children"] = children
            result["count"] = children.size
        }

        return result
    }
}