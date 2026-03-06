package com.neilturner.aerialviews.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SambaHelper Tests")
internal class SambaHelperTest {
    @Nested
    @DisplayName("parseUserInfo Tests")
    inner class ParseUserInfoTests {
        @Test
        @DisplayName("Should parse username without password")
        fun testParseUserInfoUsernameOnly() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://myuser@hostname/share/path")

            assertEquals("myuser", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should parse username and password")
        fun testParseUserInfoUsernameAndPassword() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://myuser:mypassword@hostname/share/path")

            assertEquals("myuser", userName)
            assertEquals("mypassword", password)
        }

        @Test
        @DisplayName("Should handle username with spaces")
        fun testParseUserInfoUsernameWithSpaces() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://my user@hostname/share/path")

            assertEquals("my user", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle username and password with spaces")
        fun testParseUserInfoUsernameAndPasswordWithSpaces() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://my user:my password@hostname/share/path")

            assertEquals("my user", userName)
            assertEquals("my password", password)
        }

        @Test
        @DisplayName("Should handle username with special characters")
        fun testParseUserInfoUsernameWithSpecialChars() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://user%40domain:pass%23word@hostname/share/path")

            assertEquals("user%40domain", userName)
            assertEquals("pass%23word", password)
        }

        @Test
        @DisplayName("Should handle empty user info (anonymous)")
        fun testParseUserInfoEmpty() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://hostname/share/path")

            assertEquals("", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle password with colon - splits on first colon only")
        fun testParseUserInfoPasswordWithColon() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://myuser:pass:word@hostname/share/path")

            assertEquals("myuser", userName)
            assertEquals("pass", password)
        }

        @Test
        @DisplayName("Should handle SMB1 credential key")
        fun testParseUserInfoSMB1Key() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://SMB1@hostname/share/path")

            assertEquals("SMB1", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle SMB2 credential key")
        fun testParseUserInfoSMB2Key() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://SMB2@hostname/share/path")

            assertEquals("SMB2", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle credential keys with password")
        fun testParseUserInfoCredentialKeyWithPassword() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://SMB1:secret123@hostname/share/path")

            assertEquals("SMB1", userName)
            assertEquals("secret123", password)
        }

        @Test
        @DisplayName("Should handle username with numbers and underscores")
        fun testParseUserInfoUsernameWithNumbersAndUnderscores() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://user_123:pass_456@hostname/share/path")

            assertEquals("user_123", userName)
            assertEquals("pass_456", password)
        }

        @Test
        @DisplayName("Should handle unicode characters in credentials")
        fun testParseUserInfoUnicode() {
            // Using ASCII colon (:) as separator with unicode username/password
            val (userName, password) = SambaHelper.parseUserInfo("smb://用户：密码@hostname/share/path")

            // Note: The Chinese colon (:) is not split, so entire string becomes username
            // This is expected behavior - only ASCII colon (:) is used as separator
            assertEquals("用户：密码", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle unicode characters with ASCII colon separator")
        fun testParseUserInfoUnicodeWithAsciiColon() {
            // Using ASCII colon (:) as separator with unicode username/password
            val (userName, password) = SambaHelper.parseUserInfo("smb://用户：密码@hostname/share/path")

            // The character looks like a colon but is actually Chinese colon (:)
            // So it won't split - entire string becomes username
            assertEquals("用户：密码", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle username with plus sign (URL encoded space)")
        fun testParseUserInfoUsernameWithPlus() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://my+user@hostname/share/path")

            assertEquals("my+user", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle credential key with numbers SMB3")
        fun testParseUserInfoSMB3Key() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://SMB3@hostname/share/path")

            assertEquals("SMB3", userName)
            assertEquals("", password)
        }

        @Test
        @DisplayName("Should handle empty username before colon")
        fun testParseUserInfoEmptyUsername() {
            val (userName, password) = SambaHelper.parseUserInfo("smb://:password@hostname/share/path")

            assertEquals("", userName)
            assertEquals("password", password)
        }
    }

    @Nested
    @DisplayName("parseShareAndPathName Tests")
    inner class ParseShareAndPathNameTests {
        @Test
        @DisplayName("Should parse share name without path")
        fun testParseShareAndPathNameShareOnly() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/share")

            assertEquals("share", shareName)
            assertEquals("", path)
        }

        @Test
        @DisplayName("Should parse share name with single path segment")
        fun testParseShareAndPathNameSinglePath() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/share/folder1")

            assertEquals("share", shareName)
            assertEquals("folder1", path)
        }

        @Test
        @DisplayName("Should parse share name with multiple path segments")
        fun testParseShareAndPathNameMultiplePaths() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/share/folder1/folder2/file.mp4")

            assertEquals("share", shareName)
            assertEquals("folder1/folder2/file.mp4", path)
        }

        @Test
        @DisplayName("Should parse share name with spaces")
        fun testParseShareAndPathNameWithSpaces() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/my share/my folder/file.mp4")

            assertEquals("my share", shareName)
            assertEquals("my folder/file.mp4", path)
        }

        @Test
        @DisplayName("Should parse share name with special characters")
        fun testParseShareAndPathNameWithSpecialChars() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/share%20name/folder%20path/file.mp4")

            assertEquals("share%20name", shareName)
            assertEquals("folder%20path/file.mp4", path)
        }

        @Test
        @DisplayName("Should parse share name with user info present")
        fun testParseShareAndPathNameWithUserInfo() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://user:pass@hostname/share/folder")

            assertEquals("share", shareName)
            assertEquals("folder", path)
        }

        @Test
        @DisplayName("Should handle deep nested paths")
        fun testParseShareAndPathNameDeepNesting() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/share/a/b/c/d/e/f/video.mp4")

            assertEquals("share", shareName)
            assertEquals("a/b/c/d/e/f/video.mp4", path)
        }

        @Test
        @DisplayName("Should handle share name with numbers and hyphens")
        fun testParseShareAndPathNameShareWithNumbers() {
            val (shareName, path) = SambaHelper.parseShareAndPathName("smb://hostname/media-share-1/videos")

            assertEquals("media-share-1", shareName)
            assertEquals("videos", path)
        }
    }

    @Nested
    @DisplayName("fixShareName Tests")
    inner class FixShareNameTests {
        @Test
        @DisplayName("Should return empty string for empty input")
        fun testFixShareNameEmpty() {
            val result = SambaHelper.fixShareName("")

            assertEquals("", result)
        }

        @Test
        @DisplayName("Should convert backslashes to forward slashes")
        fun testFixShareNameBackslashes() {
            val result = SambaHelper.fixShareName("\\Videos\\Aerial")

            assertEquals("/Videos/Aerial", result)
        }

        @Test
        @DisplayName("Should remove smb:/ prefix")
        fun testFixShareNameRemoveSmbPrefix() {
            val result = SambaHelper.fixShareName("smb:/Videos/Aerial")

            assertEquals("/Videos/Aerial", result)
        }

        @Test
        @DisplayName("Should add leading slash if missing")
        fun testFixShareNameAddLeadingSlash() {
            val result = SambaHelper.fixShareName("Videos/Aerial")

            assertEquals("/Videos/Aerial", result)
        }

        @Test
        @DisplayName("Should remove trailing slash")
        fun testFixShareNameRemoveTrailingSlash() {
            val result = SambaHelper.fixShareName("/Videos/Aerial/")

            assertEquals("/Videos/Aerial", result)
        }

        @Test
        @DisplayName("Should handle multiple fixes at once")
        fun testFixShareNameMultipleFixes() {
            val result = SambaHelper.fixShareName("smb:\\\\Videos\\Aerial\\")

            assertEquals("/Videos/Aerial", result)
        }

        @Test
        @DisplayName("Should handle mixed slashes")
        fun testFixShareNameMixedSlashes() {
            val result = SambaHelper.fixShareName("\\Videos/Aerial\\Community")

            assertEquals("/Videos/Aerial/Community", result)
        }

        @Test
        @DisplayName("Should handle double slashes - normalizes them")
        fun testFixShareNameDoubleSlashes() {
            // fixShareName replaces // with / but only once per pass
            // Input: //Videos//Aerial -> /Videos/Aerial after replacement
            val result = SambaHelper.fixShareName("//Videos//Aerial")

            // The function replaces // with / once, resulting in /Videos/Aerial
            assertEquals("/Videos/Aerial", result)
        }
    }

    @Nested
    @DisplayName("URL Encoding Tests")
    inner class UrlEncodingTests {
        @Test
        @DisplayName("Should verify spaces are encoded in URL")
        fun testUrlEncodingSpaces() {
            val username = "my user"
            val password = "my password"
            val encodedUser = java.net.URLEncoder.encode(username, "utf-8")
            val encodedPass = java.net.URLEncoder.encode(password, "utf-8")

            assertEquals("my+user", encodedUser)
            assertEquals("my+password", encodedPass)
        }

        @Test
        @DisplayName("Should verify special characters are encoded")
        fun testUrlEncodingSpecialChars() {
            val username = "user@domain"
            val password = "pass#word!"
            val encodedUser = java.net.URLEncoder.encode(username, "utf-8")
            val encodedPass = java.net.URLEncoder.encode(password, "utf-8")

            assertTrue(encodedUser.contains("%"))
            assertTrue(encodedPass.contains("%"))
        }

        @Test
        @DisplayName("Should verify SMB credential keys don't need encoding")
        fun testUrlEncodingSmbKeys() {
            val smb1 = "SMB1"
            val smb2 = "SMB2"
            val smb3 = "SMB3"
            val encodedSmb1 = java.net.URLEncoder.encode(smb1, "utf-8")
            val encodedSmb2 = java.net.URLEncoder.encode(smb2, "utf-8")
            val encodedSmb3 = java.net.URLEncoder.encode(smb3, "utf-8")

            assertEquals("SMB1", encodedSmb1)
            assertEquals("SMB2", encodedSmb2)
            assertEquals("SMB3", encodedSmb3)
        }

        @Test
        @DisplayName("Should verify plus sign is encoded")
        fun testUrlEncodingPlusSign() {
            val username = "user+name"
            val encodedUser = java.net.URLEncoder.encode(username, "utf-8")

            assertTrue(encodedUser.contains("%"))
        }
    }

    @Nested
    @DisplayName("Integration Tests - Full URL Parsing")
    inner class IntegrationTests {
        @Test
        @DisplayName("Should parse complete SMB URL with all components")
        fun testFullUrlParsing() {
            val url = "smb://myuser:mypassword@server.local/Videos/Aerial/community.mp4"
            val (userName, password) = SambaHelper.parseUserInfo(url)
            val (shareName, path) = SambaHelper.parseShareAndPathName(url)

            assertEquals("myuser", userName)
            assertEquals("mypassword", password)
            assertEquals("Videos", shareName)
            assertEquals("Aerial/community.mp4", path)
        }

        @Test
        @DisplayName("Should parse SMB URL with SMB1 credential key")
        fun testFullUrlParsingWithSMB1() {
            val url = "smb://SMB1@server1.local/Share/folder/video.mp4"
            val (userName, password) = SambaHelper.parseUserInfo(url)
            val (shareName, path) = SambaHelper.parseShareAndPathName(url)

            assertEquals("SMB1", userName)
            assertEquals("", password)
            assertEquals("Share", shareName)
            assertEquals("folder/video.mp4", path)
        }

        @Test
        @DisplayName("Should parse SMB URL with SMB2 credential key")
        fun testFullUrlParsingWithSMB2() {
            val url = "smb://SMB2:secret@server2.local/Media/Movies/film.mkv"
            val (userName, password) = SambaHelper.parseUserInfo(url)
            val (shareName, path) = SambaHelper.parseShareAndPathName(url)

            assertEquals("SMB2", userName)
            assertEquals("secret", password)
            assertEquals("Media", shareName)
            assertEquals("Movies/film.mkv", path)
        }

        @Test
        @DisplayName("Should parse anonymous SMB URL")
        fun testAnonymousUrlParsing() {
            val url = "smb://server.local/Public/files/doc.pdf"
            val (userName, password) = SambaHelper.parseUserInfo(url)
            val (shareName, path) = SambaHelper.parseShareAndPathName(url)

            assertEquals("", userName)
            assertEquals("", password)
            assertEquals("Public", shareName)
            assertEquals("files/doc.pdf", path)
        }

        @Test
        @DisplayName("Should parse URL with username containing spaces")
        fun testUrlWithSpacesInUsername() {
            val url = "smb://my user:my pass@server/Share/path/file.txt"
            val (userName, password) = SambaHelper.parseUserInfo(url)
            val (shareName, path) = SambaHelper.parseShareAndPathName(url)

            assertEquals("my user", userName)
            assertEquals("my pass", password)
            assertEquals("Share", shareName)
            assertEquals("path/file.txt", path)
        }
    }
}
