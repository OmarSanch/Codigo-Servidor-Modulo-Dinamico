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

        val appPath = File(basePathFile, applicationId)
        logger.i("App path: ${appPath.absolutePath}")

        if (!appPath.exists()) {
            logger.i("ADVERTENCIA: No existe directorio para applicationId='$applicationId'")
            logger.i("Path completo buscado: ${appPath.absolutePath}")
            return emptyList()
        }

        if (!appPath.isDirectory) {
            logger.e("El path de la app existe pero NO es un directorio")
            return emptyList()
        }

        logger.i("Directorio de la app encontrado. Contenido:")
        val files = appPath.listFiles()
        if (files == null || files.isEmpty()) {
            logger.i("ADVERTENCIA: Directorio de app vacío o no se pudo leer")
            return emptyList()
        }

        files.forEach { file ->
            logger.i("  - ${file.name} (${if (file.isDirectory) "DIR" else "FILE"})")
        }

        val versions = mutableListOf<Int>()

        for (versionDir in files) {
            if (!versionDir.isDirectory) {
                logger.i("Saltando '${versionDir.name}' - no es directorio")
                continue
            }

            val versionNumber = versionDir.name.toIntOrNull()
            if (versionNumber == null) {
                logger.i("Saltando directorio '${versionDir.name}' - no es un número válido")
                continue
            }

            logger.i("Revisando versión $versionNumber...")
            val variantPath = File(versionDir, variant)

            if (!variantPath.exists()) {
                logger.i("  Variante '$variant' NO existe en versión $versionNumber")
                continue
            }

            if (!variantPath.isDirectory) {
                logger.i("  ADVERTENCIA: Path de variante existe pero NO es directorio")
                continue
            }

            logger.i("  ✓ Versión $versionNumber tiene variante '$variant'")

            // Verificar que tenga archivos
            val variantFiles = variantPath.listFiles()
            if (variantFiles != null && variantFiles.isNotEmpty()) {
                logger.i("    Archivos encontrados: ${variantFiles.size}")
                variantFiles.take(5).forEach { file ->
                    logger.i("      - ${file.name}")
                }
                versions.add(versionNumber)
            } else {
                logger.i("    ADVERTENCIA: Directorio de variante vacío")
            }
        }

        logger.i("Total versiones válidas encontradas: ${versions.size}")
        return versions
    }
}