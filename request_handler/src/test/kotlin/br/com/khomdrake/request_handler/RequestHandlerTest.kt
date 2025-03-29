package br.com.khomdrake.request_handler

import app.cash.turbine.test
import br.com.khomdrake.request_handler.cache.MemoryVault
import br.com.khomdrake.request_handler.data.ResponseStatus
import br.com.khomdrake.request_handler.data.responseData
import br.com.khomdrake.request_handler.data.responseLoading
import br.com.khomdrake.request_handler.log.LogHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RequestHandlerTest {

    @Before
    fun setup() {
        mockkObject(LogHandler)
    }

    @After
    fun shutdown() {
        unmockkAll()
        MemoryVault.clearCache()
    }

    @Test
    fun `ExecuteData success, should return correct data`() {
        runBlocking { 
            val requestHandler = requestHandler<List<String>> { 
                request { 
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
            }
            
            val data = requestHandler.executeData()
            
            assertEquals(
                expected = listOf(
                    "Home",
                    "Apartment"
                ),
                result = data
            )
        }
    }

    @Test
    fun `ExecuteData error, should throw exception`() {
        runBlocking {
            val requestHandler = requestHandler<List<String>> {
                request {

                    throw NotImplementedError()

                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
            }

            kotlin.runCatching {
                requestHandler.executeData()
            }
                .onFailure {
                    assert(it is NotImplementedError)
                }
                .onSuccess {
                    throw Exception("Didn't fail!")
                }
        }
    }

    @Test
    fun `ExecuteData should work using async`() {
        runBlocking {
            val requestHandler = requestHandler {
                request {
                    val a = async {
                        listOf(
                            "Home",
                            "Apartment"
                        )
                    }

                    val b = async {
                        listOf(
                            "Office",
                            "Shopping"
                        )
                    }

                    Pair(a.await(), b.await())
                }
            }

            val data = requestHandler.executeData()

            assertEquals(
                expected = listOf(
                    "Home",
                    "Apartment"
                ),
                result = data.first
            )

            assertEquals(
                expected = listOf(
                    "Office",
                    "Shopping"
                ),
                result = data.second
            )
        }
    }

    @Test
    fun `ExecuteData with cache, first time should save into memory and second time use the cache`() {
        runBlocking {
            val cacheHelper = mockk<CacheHelper>(relaxed = true)

            every {
                cacheHelper.save<List<String>>(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test")
            } returns true

            every {
                cacheHelper.retrieve<List<String>>("Test")
            } returns listOf(
                "Home",
                "Apartment"
            )

            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            requestHandler.executeData()

            delay(50)

            requestHandler.executeData()

            verify(exactly = 1) {
                cacheHelper.save(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            }

            verify(exactly = 1) {
                cacheHelper.retrieve<List<String>>(
                    "Test"
                )
            }

            verify(exactly = 1) {
                cacheHelper.remove("Test")
            }
        }
    }

    @Test
    fun `ExecuteData with cache already saved, should return use the cache directly`() {
        runBlocking {
            val cacheHelper = mockk<CacheHelper>(relaxed = true)

            every {
                cacheHelper.save<List<String>>(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test")
            } returns true

            every {
                cacheHelper.retrieve<List<String>>("Test")
            } returns listOf(
                "Home",
                "Apartment"
            )

            mockkObject(MemoryVault)

            coEvery {
                MemoryVault.getDataLong("Test")
            } returns System.currentTimeMillis() + 5.minutes.inWholeMilliseconds

            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            requestHandler.executeData()

            delay(50)

            requestHandler.executeData()

            verify(exactly = 0) {
                cacheHelper.save(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            }

            verify(exactly = 2) {
                cacheHelper.retrieve<List<String>>(
                    "Test"
                )
            }

            verify(exactly = 0) {
                cacheHelper.remove("Test")
            }
        }
    }

    @Test
    fun `ExecuteData with cache already saved for one key and try to access another, should not use cache`() {
        runBlocking {
            val cacheHelper = mockk<CacheHelper>(relaxed = true)

            every {
                cacheHelper.save<List<String>>(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test")
            } returns true

            every {
                cacheHelper.retrieve<List<String>>("Test")
            } returns listOf(
                "Home",
                "Apartment"
            )

            every {
                cacheHelper.save<List<String>>(
                    "Test2",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test2")
            } returns true

            every {
                cacheHelper.retrieve<List<String>>("Test2")
            } returns listOf(
                "Home",
                "Apartment"
            )

            mockkObject(MemoryVault)

            coEvery {
                MemoryVault.getDataLong("Test")
            } returns System.currentTimeMillis() + 5.minutes.inWholeMilliseconds

            coEvery {
                MemoryVault.getDataLong("Test2")
            } returns System.currentTimeMillis() - 5.minutes.inWholeMilliseconds

            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            val requestHandler2 = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test2")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            requestHandler.executeData()

            delay(50)

            requestHandler.executeData()

            verify(exactly = 0) {
                cacheHelper.save(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            }

            verify(exactly = 2) {
                cacheHelper.retrieve<List<String>>(
                    "Test"
                )
            }

            verify(exactly = 0) {
                cacheHelper.remove("Test")
            }

            requestHandler2.executeData()

            verify(exactly = 1) {
                cacheHelper.save(
                    "Test2",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            }

            verify(exactly = 0) {
                cacheHelper.retrieve<List<String>>(
                    "Test2"
                )
            }

            verify(exactly = 1) {
                cacheHelper.remove("Test2")
            }
        }
    }

    @Test
    fun `Execute with success, should emit first loading and then data`() {
        runTest {
            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
            }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    val loading = awaitItem()
                    assertEquals(
                        expected = responseLoading<List<String>>(),
                        result = loading,
                    )

                    val data = awaitItem()
                    assertEquals(
                        expected = responseData(
                            listOf(
                                "Home",
                                "Apartment"
                            )
                        ),
                        result = data,
                    )
                }
        }
    }

    @Test
    fun `Execute with error, should emit first loading and then error`() {
        runBlocking {
            val requestHandler = requestHandler<List<String>> {
                request {

                    throw NotImplementedError()

                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
            }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    val loading = awaitItem()
                    assertEquals(
                        expected = responseLoading<List<String>>(),
                        result = loading,
                    )

                    val error = awaitItem()
                    assertEquals(
                        expected = ResponseStatus.ERROR,
                        result = error.state,
                    )
                    assert(error.error is NotImplementedError)
                }
        }
    }

    @Test
    fun `Execute with cache, first time should save into memory and second time use the cache`() {
        runTest {
            val cacheHelper = mockk<CacheHelper>(relaxed = true)

            every {
                cacheHelper.save<List<String>>(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test")
            } returns true

            every {
                cacheHelper.retrieve<List<String>>("Test")
            } returns listOf(
                "Home",
                "Apartment"
            )

            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    awaitItem()
                    val data = awaitItem()
                    assertEquals(
                        expected = responseData(
                            listOf(
                                "Home",
                                "Apartment"
                            )
                        ),
                        result = data,
                    )

                    delay(50)
                }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    awaitItem()
                    awaitItem()

                    verify(exactly = 1) {
                        cacheHelper.save(
                            "Test",
                            listOf(
                                "Home",
                                "Apartment"
                            )
                        )
                    }

                    verify(exactly = 1) {
                        cacheHelper.retrieve<List<String>>(
                            "Test"
                        )
                    }

                    verify(exactly = 1) {
                        cacheHelper.remove("Test")
                    }
                }
        }
    }

    @Test
    fun `Execute with cache already saved, should return use the cache directly`() {
        runBlocking {
            val cacheHelper = mockk<CacheHelper>(relaxed = true)

            every {
                cacheHelper.save<List<String>>(
                    "Test",
                    listOf(
                        "Home",
                        "Apartment"
                    )
                )
            } returns true

            every {
                cacheHelper.remove("Test")
            } returns true

            mockkObject(MemoryVault)

            coEvery {
                MemoryVault.getDataLong("Test")
            } returns System.currentTimeMillis() + 5.minutes.inWholeMilliseconds

            every {
                cacheHelper.retrieve<List<String>>("Test")
            } returns listOf(
                "Home",
                "Apartment"
            )

            val requestHandler = requestHandler<List<String>> {
                request {
                    listOf(
                        "Home",
                        "Apartment"
                    )
                }
                cache {
                    setCacheKey("Test")
                    timeout(10.minutes, CacheType.MEMORY)
                    save { key, any ->
                        cacheHelper.save<List<String>>(key, any)
                    }
                    remove { key ->
                        cacheHelper.remove(key)
                    }
                    retrieve { key ->
                        cacheHelper.retrieve<List<String>>(key)
                    }
                }
            }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    awaitItem()
                    val data = awaitItem()
                    assertEquals(
                        expected = responseData(
                            listOf(
                                "Home",
                                "Apartment"
                            )
                        ),
                        result = data,
                    )

                    delay(50)
                }

            requestHandler
                .execute()
                .responseStateFlow
                .test {
                    awaitItem()
                    awaitItem()

                    verify(exactly = 0) {
                        cacheHelper.save(
                            "Test",
                            listOf(
                                "Home",
                                "Apartment"
                            )
                        )
                    }

                    verify(exactly = 2) {
                        cacheHelper.retrieve<List<String>>(
                            "Test"
                        )
                    }

                    verify(exactly = 0) {
                        cacheHelper.remove("Test")
                    }
                }
        }
    }
    
    private fun assertEquals(
        expected: Any?,
        result: Any? 
    ) {
        Assert.assertEquals(expected, result)
    }

}