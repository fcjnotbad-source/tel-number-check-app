package com.fcj.telnumbercheck.factory

/** Compares dotted release numbers without turning 0.3.10 into 0.3.1. */
object AppVersion {
    private val releasePattern = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

    fun isSupported(version: String): Boolean =
        version.trim().length in 1..64 && releasePattern.matches(version.trim())

    fun isNewer(remoteVersion: String, installedVersion: String): Boolean {
        if (!isSupported(remoteVersion) || !isSupported(installedVersion)) return false
        val remote = releasePattern.matchEntire(remoteVersion.trim()) ?: return false
        val installed = releasePattern.matchEntire(installedVersion.trim()) ?: return false
        val remoteNumbers = remote.groupValues[1].split('.').map(String::toBigInteger)
        val installedNumbers = installed.groupValues[1].split('.').map(String::toBigInteger)
        for (index in 0 until maxOf(remoteNumbers.size, installedNumbers.size)) {
            val comparison = (remoteNumbers.getOrNull(index) ?: java.math.BigInteger.ZERO)
                .compareTo(installedNumbers.getOrNull(index) ?: java.math.BigInteger.ZERO)
            if (comparison != 0) return comparison > 0
        }

        val remotePrerelease = remote.groupValues[2]
        val installedPrerelease = installed.groupValues[2]
        if (remotePrerelease == installedPrerelease || installedPrerelease.isBlank()) return false
        if (remotePrerelease.isBlank()) return true
        val remoteParts = remotePrerelease.split('.')
        val installedParts = installedPrerelease.split('.')
        for (index in 0 until maxOf(remoteParts.size, installedParts.size)) {
            val remotePart = remoteParts.getOrNull(index) ?: return false
            val installedPart = installedParts.getOrNull(index) ?: return true
            val remoteNumber = remotePart.toBigIntegerOrNull()
            val installedNumber = installedPart.toBigIntegerOrNull()
            val comparison = when {
                remoteNumber != null && installedNumber != null -> remoteNumber.compareTo(installedNumber)
                remoteNumber != null -> -1
                installedNumber != null -> 1
                else -> remotePart.compareTo(installedPart)
            }
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}
