package com.example.extension.managed.model

import com.example.extension.managed.error.ExtensionError
import java.net.URI

object ManagedExtensionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ExtensionError) : ValidationResult()
    }

    private val PRIVATE_IP_REGEX = Regex(
        "^(" +
                "127(\\.[0-9]{1,3}){3}|" +
                "10(\\.[0-9]{1,3}){3}|" +
                "192\\.168(\\.[0-9]{1,3}){2}|" +
                "172\\.(1[6-9]|2[0-9]|3[0-1])(\\.[0-9]{1,3}){2}|" +
                "0\\.0\\.0\\.0|" +
                "localhost|" +
                "::1|" +
                "\\[::1\\]" +
                ")(:[0-9]+)?$",
        RegexOption.IGNORE_CASE
    )

    fun validate(extension: ManagedExtension): ValidationResult {
        if (extension.id.isBlank()) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(extension.baseUrl, "Extension ID cannot be blank")
            )
        }

        if (extension.scraperKey.isBlank()) {
            return ValidationResult.Invalid(
                ExtensionError.UnknownScraperKey("")
            )
        }

        val baseUrl = extension.baseUrl.trim()
        if (baseUrl.isBlank()) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Base URL cannot be blank")
            )
        }

        val uri = try {
            URI(baseUrl)
        } catch (e: Exception) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Malformed URI: ${e.message}")
            )
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Only HTTPS scheme is permitted; found '$scheme'")
            )
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Base URL host is missing or blank")
            )
        }

        if (PRIVATE_IP_REGEX.matches(host) || host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Private or local IP / hostname is rejected: $host")
            )
        }

        if (extension.priority < 0) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "Priority cannot be negative: ${extension.priority}")
            )
        }

        if (extension.minAppVersionCode < 0) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidBaseUrl(baseUrl, "minAppVersionCode cannot be negative: ${extension.minAppVersionCode}")
            )
        }

        if (extension.runtimeApiVersion < 1) {
            return ValidationResult.Invalid(
                ExtensionError.IncompatibleRuntime(1, extension.runtimeApiVersion)
            )
        }

        if (extension.contentTypes.isEmpty()) {
            return ValidationResult.Invalid(
                ExtensionError.ContentTypeUnsupported(ContentType.MOVIE)
            )
        }

        return ValidationResult.Valid
    }
}
