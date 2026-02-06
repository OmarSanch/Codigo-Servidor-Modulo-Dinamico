package com.jeppeman.globallydynamic.server

import com.google.gson.Gson
import com.jeppeman.globallydynamic.server.dto.DeviceSpecDto
import com.jeppeman.globallydynamic.server.dto.toDeviceSpec
import com.jeppeman.globallydynamic.server.extensions.readString
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Request
import org.jose4j.json.internal.json_simple.JSONObject
import javax.servlet.MultipartConfigElement
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Part


interface PathHandler {
    val path: String
    val loggingEnabled: Boolean get() = true
    val authRequired: Boolean get() = true

    fun HttpServletRequest?.requireHeader(header: String): String {
        return this?.getHeader(header)
            ?: throw HttpException(HttpStatus.BAD_REQUEST_400, "Missing required header: $header")
    }

    fun HttpServletRequest?.requireQueryParam(queryParam: String): Array<String> {
        return this?.getQueryParam(queryParam)
            ?: throw HttpException(HttpStatus.BAD_REQUEST_400, "Missing required query parameter: $queryParam")
    }

    fun HttpServletRequest?.getQueryParam(queryParam: String): Array<String>? {
        return this?.parameterMap?.get(queryParam)
    }

    fun HttpServletRequest?.requirePart(
        name: String
    ): Part {
        return this?.parts?.find { part -> part.name == name }
            ?: throw HttpException(HttpStatus.BAD_REQUEST_400, "Missing required part: $name")
    }

    @Throws(HttpException::class)
    fun handle(request: HttpServletRequest?, response: HttpServletResponse?)
}

internal class DownloadSplitsPathHandler(
    private val bundleManager: BundleManager,
    private val validateSignature: Boolean,
    private val logger: Logger,
    private val gson: Gson
) : PathHandler {

    override val path: String = "download"
    override val authRequired: Boolean = false

    override fun handle(request: HttpServletRequest?, response: HttpServletResponse?) {
        val applicationIdParam = request.requireQueryParam("application-id")
        val variantParam = request.requireQueryParam("variant")
        val signatureParam = request.requireQueryParam("signature")

        // version opcional
        val versionParam = request.getQueryParam("version")

        val throttle = request.getQueryParam("throttle")
        val featuresToInstallParam = request.getQueryParam("features") ?: arrayOf()
        val languagesToInstallParam = request.getQueryParam("languages") ?: arrayOf()
        val includeMissingParam = request.getQueryParam("include-missing")

        val body = request?.inputStream?.use { it.readString() }
        logger.i("Request body: $body")

        val deviceSpec = try {
            gson.fromJson(body, DeviceSpecDto::class.java).toDeviceSpec()
        } catch (e: Exception) {
            logger.e("Invalid device spec json. Body=$body", e)
            throw HttpException(HttpStatus.BAD_REQUEST_400, "Invalid body, expected device spec json")
        }

        if (featuresToInstallParam.isEmpty() && languagesToInstallParam.isEmpty()) {
            throw HttpException(HttpStatus.BAD_REQUEST_400, "No features or languages included in the request")
        }

        val applicationId = applicationIdParam.first()
        val variant = variantParam.first()
        val signature = signatureParam.first()

        // 1) Resolver versión a servir
        val requestedVersion = versionParam?.firstOrNull()?.toIntOrNull() ?: 0
        val latestVersion = findLatestVersion(applicationId, variant)
        val resolvedVersion = if (requestedVersion <= 0) latestVersion else requestedVersion

        logger.i("Download request: appId=$applicationId variant=$variant requestedVersion=$requestedVersion resolvedVersion=$resolvedVersion")

        if (resolvedVersion <= 0) {
            throw HttpException(HttpStatus.BAD_REQUEST_400, "No versions available for $applicationId / $variant")
        }

        // 2) buildId (del meta) para ESA versión resuelta
        val clientBuildId = request.getQueryParam("buildId")?.firstOrNull()
        val serverBuildId = getBuildIdFromMeta(applicationId, variant, resolvedVersion)
        val buildIdMismatch = clientBuildId != null && serverBuildId != null && clientBuildId != serverBuildId

        // 3) Validar firma contra la versión resuelta
        if (validateSignature) {
            when (val validationResult = bundleManager.validateSignature(signature, applicationId, resolvedVersion, variant)) {
                is BundleManager.Result.Error ->
                    throw HttpException(HttpStatus.BAD_REQUEST_400, validationResult.message)
                else -> Unit
            }
        }

        val includeMissing = includeMissingParam?.first()?.toBoolean() ?: false

        // 4) Generar splits con resolvedVersion
        val compressedSplitsResult = bundleManager.generateCompressedSplits(
            applicationId = applicationId,
            version = resolvedVersion,
            variant = variant,
            deviceSpec = deviceSpec,
            features = featuresToInstallParam.flatMap { it.split(",") }.toTypedArray(),
            languages = languagesToInstallParam.flatMap { it.split(",") }.toTypedArray(),
            includeMissing = includeMissing
        )

        val compressedSplits = when (compressedSplitsResult) {
            is BundleManager.Result.Success -> compressedSplitsResult.path
            else -> throw HttpException(HttpStatus.BAD_REQUEST_400, compressedSplitsResult.message)
        }

        val fileSize = compressedSplits.toFile().length().toInt()
        var byteOffset = 0
        val throttleBy = throttle?.firstOrNull()?.toLongOrNull() ?: 0
        val interval = (throttleBy / 30f).toLong()
        val byteInterval = (fileSize / 30f).toInt().coerceAtLeast(1)

        val buffer = ByteArray(byteInterval)

        response?.apply {
            // headers buildId
            setHeader("X-GloballyDynamic-BuildId", serverBuildId ?: "")
            setHeader("X-GloballyDynamic-BuildId-Mismatch", buildIdMismatch.toString())
            setHeader("X-GloballyDynamic-Resolved-Version", resolvedVersion.toString())

            contentType = "application/zip"
            setHeader("Content-Disposition", "attachment; filename=splits.zip")
            setContentLength(fileSize)

            compressedSplits.toFile().inputStream().use { inputStream ->
                while (byteOffset < fileSize) {
                    val bytesLeftToWrite = fileSize - byteOffset
                    val writeLength = if (byteOffset + byteInterval > fileSize) bytesLeftToWrite else byteInterval
                    inputStream.read(buffer, 0, writeLength)
                    outputStream.write(buffer, 0, writeLength)
                    byteOffset += writeLength

                    val percentageSent = Math.round((byteOffset / fileSize.toFloat()) * 100)
                    logger.i("Sent $byteOffset / $fileSize ($percentageSent%)", false)

                    if (interval > 0) Thread.sleep(interval)
                }
            }
        }

        logger.i("Finished sending splits. resolvedVersion=$resolvedVersion buildIdMismatch=$buildIdMismatch", prefix = "\n")
    }


    private fun getBuildIdFromMeta(applicationId: String, variant: String, version: Int): String? {
        return try {
            val metaFileName = "${applicationId}_${variant}_$version.meta.json"
            val metaPath = (bundleManager as? BundleManagerImpl)?.storageBackend?.retrieveFile(metaFileName)
                ?: return null
            val text = metaPath.toFile().readText()
            val json = gson.fromJson(text, com.google.gson.JsonObject::class.java)
            if (json.has("buildId") && !json.get("buildId").isJsonNull) json.get("buildId").asString else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ✅ Devuelve la última versión disponible en storage (patrón: {applicationId}_{variant}_{version}.apks)
     */
    private fun findLatestVersion(applicationId: String, variant: String): Int  {
        return try {
            val sb = (bundleManager as? BundleManagerImpl)?.storageBackend
            if (sb is LocalStorageBackend) {
                val base = sb.baseStoragePath.toFile()
                val pattern = "${applicationId}_${variant}_"
                val versions = base.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && it.name.startsWith(pattern) && it.name.endsWith(".apks") }
                    ?.mapNotNull { f ->
                        f.name.removePrefix(pattern).removeSuffix(".apks").toIntOrNull()
                    }
                    ?.toList()
                    ?: emptyList()

                versions.max() ?: 0   // 👈 aquí está la clave
            } else 0
        } catch (_: Exception) {
            0
        }
    }
}

internal class UploadBundlePathHandler(
    private val bundleManager: BundleManager,
    private val logger: Logger
) : PathHandler {
    override val path: String = "upload"

    override fun handle(request: HttpServletRequest?, response: HttpServletResponse?) {
        if (request?.contentType?.contains(CONTENT_TYPE_MULTIPART_FORM_DATA) != true) {
            throw HttpException(HttpStatus.BAD_REQUEST_400, "Content-Type != $CONTENT_TYPE_MULTIPART_FORM_DATA")
        }

        val limit = 100 * Math.pow(10.0, 9.0).toLong()
        val threshold = 200 * Math.pow(10.0, 6.0).toInt()
        val multipartConfigElement = MultipartConfigElement("", limit, limit, threshold)
        request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfigElement)

        val bundlePart = request.requirePart("bundle")
        val versionPart = request.requirePart("version")
        val applicationIdPart = request.requirePart("application-id")
        val variantPart = request.requirePart("variant")
        val signingConfigPart = request.requirePart("signing-config")
        val keystorePart = request.requirePart("keystore")

        val applicationId = applicationIdPart.inputStream.use { it.readString() }
        val variant = variantPart.inputStream.use { it.readString() }
        val versionString = versionPart.inputStream.use { it.readString() }
        val signingConfig = signingConfigPart.inputStream.use { it.readString() }
        val version = try {
            versionString.toInt()
        } catch (exception: Exception) {
            throw HttpException(HttpStatus.BAD_REQUEST_400, "Expected version to be an integer, " +
                "got $versionString")
        }

        val result = bundleManager.storeBundle(
            applicationId = applicationId,
            version = version,
            variant = variant,
            signingConfig = signingConfig,
            bundleInputStream = bundlePart.inputStream,
            keyStoreInputStream = keystorePart.inputStream
        )

        when (result) {
            is BundleManager.Result.Error -> {
                logger.e(result.message)
                throw HttpException(HttpStatus.BAD_REQUEST_400, result.message)
            }
            else -> Unit
        }
    }

    companion object {
        internal const val CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data"
    }
}

/**
 * Used for GCP health checks
 */
class LivenessPathHandler : PathHandler {
    override val path: String = "liveness_check"
    override val loggingEnabled: Boolean = false
    override val authRequired: Boolean = false

    override fun handle(request: HttpServletRequest?, response: HttpServletResponse?) {
        response?.status = HttpStatus.OK_200
    }
}