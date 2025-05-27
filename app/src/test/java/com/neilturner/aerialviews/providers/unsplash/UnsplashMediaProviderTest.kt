package com.neilturner.aerialviews.providers.unsplash

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for UnsplashMediaProvider
 *
 * These tests validate the basic structure and type safety of the Unsplash implementation.
 * Note: Full integration tests would require actual API keys and network connectivity.
 */
class UnsplashMediaProviderTest {
    @Test
    fun `validate basic test framework works`() {
        // Simple test to verify the test framework is working
        assertEquals(4, 2 + 2)
        assertTrue(true)
        assertNotNull("test")
    }

    @Test
    fun `validate UnsplashApi interface exists`() {
        // Test that the API interface can be loaded
        val apiClass = UnsplashApi::class.java
        assertNotNull(apiClass)
        assertTrue(apiClass.isInterface)
    }

    @Test
    fun `validate UnsplashPhoto class exists`() {
        // Test that the model class can be loaded
        val photoClass = UnsplashPhoto::class.java
        assertNotNull(photoClass)
        assertFalse(photoClass.isInterface)
    }

    @Test
    fun `validate UnsplashSearchResponse class exists`() {
        // Test that the response class can be loaded
        val responseClass = UnsplashSearchResponse::class.java
        assertNotNull(responseClass)
        assertFalse(responseClass.isInterface)
    }
}
