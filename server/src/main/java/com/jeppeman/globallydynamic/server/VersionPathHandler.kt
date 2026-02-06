package com.jeppeman.globallydynamic.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.eclipse.jetty.http.HttpStatus
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
            throw HttpException(HttpStatus.BAD_REQUEST_400, "Missing required query parameter: application-id")
        }

        if (variantParam == null || variantParam.isEmpty()) {
            logger.e("Missing variant parameter")
            throw HttpException(HttpStatus.BAD_REQUEST_400, "Missing required query parameter: variant")
        }

        val applicationId = applicationIdParam.first()
        val variant = variantParam.first()

        logger.i("Consultando versiones para applicationId='$applicationId', variant='$variant'")
        logger.i("StorageBackend type: ${storageBackend.javaClass.simpleName}")

        val availableVersions = getAvailableVersions(applicationId, variant)
        val latestVersion = if (availableVersions.isEmpty()) 0 else availableVersions.max()
        val latestBuildId = if (latestVersion == 0) null else getBuildIdFromMeta(applicationId, variant, latestVersion)

        logger.i("Versiones encontradas: ${availableVersions.size} -> $availableVersions")
        logger.i("Última versión: $latestVersion, latestBuildId=$latestBuildId")

        val responseBody = mapOf(
            "applicationId" to applicationId,
            "variant" to variant,
            "latestVersion" to latestVersion,
            "availableVersions" to availableVersions.sorted(),
            "latestBuildId" to latestBuildId
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
                is LocalStorageBackend -> getVersionsFromLocalStorage(storageBackend, applicationId, variant)
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.e("Error obteniendo versiones: ${e.message}", e)
            emptyList()
        }
    }

    private fun getBuildIdFromMeta(applicationId: String, variant: String, version: Int?): String? {
        return try {
            val metaFileName = "${applicationId}_${variant}_$version.meta.json"
            val metaPath = storageBackend.retrieveFile(metaFileName) ?: return null
            val text = metaPath.toFile().readText()

            val json = gson.fromJson(text, JsonObject::class.java)
            if (json.has("buildId") && !json.get("buildId").isJsonNull) json.get("buildId").asString else null
        } catch (e: Exception) {
            logger.e("Error leyendo meta.json buildId: ${e.message}", e)
            null
        }
    }

    private fun getVersionsFromLocalStorage(
        storageBackend: LocalStorageBackend,
        applicationId: String,
        variant: String
    ): List<Int> {
        val basePathFile = storageBackend.baseStoragePath.toFile()
        if (!basePathFile.exists()) {
            basePathFile.mkdirs()
            return emptyList()
        }

        val versions = mutableSetOf<Int>()
        val pattern = "${applicationId}_${variant}_"

        basePathFile.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(pattern) && file.name.endsWith(".apks")) {
                val versionStr = file.name.removePrefix(pattern).removeSuffix(".apks")
                versionStr.toIntOrNull()?.let { versions.add(it) }
            }
        }

        return versions.sorted()
    }
}