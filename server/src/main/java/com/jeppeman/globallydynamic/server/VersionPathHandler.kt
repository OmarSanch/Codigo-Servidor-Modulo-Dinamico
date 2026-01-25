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
        val applicationIdParam = request.getQueryParam("application-id")
        val variantParam = request.getQueryParam("variant")

        if (applicationIdParam == null) {
            throw HttpException(
                HttpStatus.BAD_REQUEST_400,
                "Missing required query parameter: application-id"
            )
        }

        if (variantParam == null) {
            throw HttpException(
                HttpStatus.BAD_REQUEST_400,
                "Missing required query parameter: variant"
            )
        }

        val applicationId = applicationIdParam.first()
        val variant = variantParam.first()

        logger.i("Consultando versiones para applicationId=$applicationId, variant=$variant")

        val availableVersions = getAvailableVersions(applicationId, variant)
        val latestVersion = if (availableVersions.isEmpty()) 0 else availableVersions.max() ?: 0

        logger.i("Versiones disponibles: $availableVersions, ultima: $latestVersion")

        val responseBody = mapOf(
            "applicationId" to applicationId,
            "variant" to variant,
            "latestVersion" to latestVersion,
            "availableVersions" to availableVersions.sorted()
        )

        response?.apply {
            status = HttpStatus.OK_200
            contentType = "application/json"
            writer.write(gson.toJson(responseBody))
        }
    }

    private fun getAvailableVersions(applicationId: String, variant: String): List<Int> {
        return try {
            when (storageBackend) {
                is LocalStorageBackend -> {
                    getVersionsFromLocalStorage(storageBackend, applicationId, variant)
                }
                else -> {
                    logger.i("Storage backend ${storageBackend.javaClass.simpleName} no soporta consulta de versiones")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.e("Error obteniendo versiones disponibles: ${e.message}")
            emptyList()
        }
    }

    private fun getVersionsFromLocalStorage(
        storageBackend: LocalStorageBackend,
        applicationId: String,
        variant: String
    ): List<Int> {
        val basePath = storageBackend.baseStoragePath
        val appPath = File(basePath.toFile(), applicationId)

        if (!appPath.exists() || !appPath.isDirectory) {
            logger.i("No se encontro el directorio para applicationId: $applicationId")
            return emptyList()
        }

        val files = appPath.listFiles()
        if (files == null) {
            return emptyList()
        }

        val versions = mutableListOf<Int>()
        for (versionDir in files) {
            if (versionDir.isDirectory) {
                val versionNumber = versionDir.name.toIntOrNull()
                if (versionNumber != null) {
                    val variantPath = File(versionDir, variant)
                    if (variantPath.exists() && variantPath.isDirectory) {
                        versions.add(versionNumber)
                    }
                }
            }
        }

        return versions
    }
}