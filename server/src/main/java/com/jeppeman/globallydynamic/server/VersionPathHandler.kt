package com.jeppeman.globallydynamic.server

import com.google.gson.Gson
import org.eclipse.jetty.http.HttpStatus
import java.io.File
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * PathHandler para obtener información de versiones disponibles
 */
class VersionPathHandler(
    private val storageBackend: StorageBackend,
    private val logger: Logger,
    private val gson: Gson
) : PathHandler {
    override val path: String = "version"
    override val authRequired: Boolean = false

    override fun handle(request: HttpServletRequest?, response: HttpServletResponse?) {
        logger.i("=== VERSION ENDPOINT CALLED ===")

        val applicationIdParam = request.getQueryParam("application-id")
        val variantParam = request.getQueryParam("variant")

        if (applicationIdParam == null || applicationIdParam.isEmpty()) {
            logger.e("Missing application-id parameter")
            throw HttpException(
                HttpStatus.BAD_REQUEST_400,
                "Missing required query parameter: application-id"
            )
        }

        if (variantParam == null || variantParam.isEmpty()) {
            logger.e("Missing variant parameter")
            throw HttpException(
                HttpStatus.BAD_REQUEST_400,
                "Missing required query parameter: variant"
            )
        }

        val applicationId = applicationIdParam.first()
        val variant = variantParam.first()

        logger.i("Consultando versiones para applicationId='$applicationId', variant='$variant'")
        logger.i("StorageBackend type: ${storageBackend.javaClass.simpleName}")

        val availableVersions = getAvailableVersions(applicationId, variant)
        val latestVersion = if (availableVersions.isEmpty()) 0 else availableVersions.max()

        logger.i("Versiones encontradas: ${availableVersions.size} -> $availableVersions")
        logger.i("Última versión: $latestVersion")

        val responseBody = mapOf(
            "applicationId" to applicationId,
            "variant" to variant,
            "latestVersion" to latestVersion,
            "availableVersions" to availableVersions.sorted()
        )

        response?.apply {
            status = HttpStatus.OK_200
            contentType = "application/json; charset=UTF-8"
            writer.write(gson.toJson(responseBody))
            writer.flush()
        }

        logger.i("=== VERSION ENDPOINT RESPONSE SENT ===")
    }

    private fun getAvailableVersions(applicationId: String, variant: String): List<Int> {
        return try {
            when (storageBackend) {
                is LocalStorageBackend -> {
                    logger.i("Usando LocalStorageBackend para búsqueda")
                    getVersionsFromLocalStorage(storageBackend, applicationId, variant)
                }
                else -> {
                    logger.i("Storage backend '${storageBackend.javaClass.simpleName}' no implementa consulta de versiones")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.e("Error obteniendo versiones: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    private fun getVersionsFromLocalStorage(
        storageBackend: LocalStorageBackend,
        applicationId: String,
        variant: String
    ): List<Int> {
        val basePath = storageBackend.baseStoragePath
        logger.i("Base storage path: ${basePath.toAbsolutePath()}")

        val basePathFile = basePath.toFile()
        if (!basePathFile.exists()) {
            logger.e("Base path NO existe: ${basePath.toAbsolutePath()}")
            logger.i("Intentando crear directorio base...")
            basePathFile.mkdirs()
            return emptyList()
        }

        logger.i("Base path existe: ${basePathFile.absolutePath}")
        logger.i("Contenido del directorio base:")
        basePathFile.listFiles()?.forEach { file ->
            logger.i("  - ${file.name} (${if (file.isDirectory) "DIR" else "FILE"})")
        }

        val versions = mutableSetOf<Int>()

        // Método 1: Busca en estructura de directorios (para compatibilidad futura)
        val appPath = File(basePathFile, applicationId)
        if (appPath.exists() && appPath.isDirectory) {
            logger.i("Buscando en estructura de directorios: ${appPath.absolutePath}")
            appPath.listFiles()?.forEach { versionDir ->
                if (versionDir.isDirectory) {
                    versionDir.name.toIntOrNull()?.let { versionNumber ->
                        val variantPath = File(versionDir, variant)
                        if (variantPath.exists() && variantPath.isDirectory) {
                            logger.i("  ✓ Encontrada versión $versionNumber en directorio")
                            versions.add(versionNumber)
                        }
                    }
                }
            }
        }

        // Método 2: Busca archivos con patrón {applicationId}_{variant}_{version}.apks
        // Este es el formato que usa BundleManager actualmente
        logger.i("Buscando archivos con patrón de nombre...")
        val pattern = "${applicationId}_${variant}_"
        basePathFile.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(pattern) && file.name.endsWith(".apks")) {
                val versionStr = file.name
                    .removePrefix(pattern)
                    .removeSuffix(".apks")

                versionStr.toIntOrNull()?.let { versionNumber ->
                    logger.i("  ✓ Encontrada versión $versionNumber en archivo ${file.name}")
                    versions.add(versionNumber)
                }
            }
        }

        logger.i("Total versiones encontradas: ${versions.size} -> ${versions.sorted()}")
        return versions.sorted()
    }
}