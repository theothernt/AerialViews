package com.neilturner.aerialviews.testconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class TestConfigSampleTest {
    @Test
    fun `every sample config parses and enables exactly one provider`() {
        val configs = sampleConfigs()
        assertTrue(configs.isNotEmpty(), "No sample configs found in $SAMPLE_CONFIG_DIR")

        configs.forEach { file ->
            val config = TestConfig.parse(file.readText())

            assertTrue(config.name.isNotBlank(), "${file.name} is missing a name")
            assertTrue(config.description.isNotBlank(), "${file.name} is missing a description")

            val enabled =
                listOfNotNull(
                    config.providers.customFeed?.takeIf { it.enabled },
                    config.providers.samba?.takeIf { it.enabled },
                    config.providers.samba2?.takeIf { it.enabled },
                    config.providers.webdav?.takeIf { it.enabled },
                    config.providers.webdav2?.takeIf { it.enabled },
                    config.providers.immich?.takeIf { it.enabled },
                    config.providers.ncMemories?.takeIf { it.enabled },
                )

            assertEquals(
                1,
                enabled.size,
                "${file.name} should enable exactly one provider but enabled ${enabled.size}",
            )
        }
    }

    @Test
    fun `sample configs contain the details needed to reach a server`() {
        val configs = sampleConfigs()
        assertTrue(configs.isNotEmpty(), "No sample configs found in $SAMPLE_CONFIG_DIR")

        configs.forEach { file ->
            val config = TestConfig.parse(file.readText())
            val providers = config.providers

            providers.samba?.takeIf { it.enabled }?.let { samba ->
                assertNotNull(samba.hostName, "${file.name} is missing a samba host name")
                assertNotNull(samba.shareName, "${file.name} is missing a samba share name")
                assertEquals(
                    listOf("VIDEOS", "PHOTOS"),
                    samba.mediaSelection,
                    "${file.name} should test both videos and photos",
                )
            }

            providers.webdav?.takeIf { it.enabled }?.let { webDav ->
                assertNotNull(webDav.hostName, "${file.name} is missing a webdav host name")
                assertNotNull(webDav.pathName, "${file.name} is missing a webdav path")
                assertEquals(
                    listOf("VIDEOS", "PHOTOS"),
                    webDav.mediaSelection,
                    "${file.name} should test both videos and photos",
                )
            }

            providers.immich?.takeIf { it.enabled }?.let { immich ->
                assertNotNull(immich.url, "${file.name} is missing an immich url")
                assertNotNull(immich.apiKey, "${file.name} is missing an immich api key")
            }

            providers.ncMemories?.takeIf { it.enabled }?.let { memories ->
                assertNotNull(memories.url, "${file.name} is missing a nc memories url")
                assertNotNull(memories.username, "${file.name} is missing a nc memories user name")
            }

            providers.customFeed?.takeIf { it.enabled }?.let { feed ->
                assertNotNull(feed.urls, "${file.name} is missing a custom feed url")
            }
        }
    }

    @Test
    fun `sample configs turn off the playlist cache so every run refetches`() {
        sampleConfigs().forEach { file ->
            val config = TestConfig.parse(file.readText())
            assertEquals(
                false,
                config.general.playlistCache,
                "${file.name} should not use the playlist cache",
            )
        }
    }

    private fun sampleConfigs(): List<File> {
        val dir = File(SAMPLE_CONFIG_DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name } ?: emptyList()
    }

    private companion object {
        const val SAMPLE_CONFIG_DIR = "../docs/testing/configs"
    }
}
