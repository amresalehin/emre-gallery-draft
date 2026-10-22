package com.amresalehin.emregallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomAlbumTest {
    @Test
    fun rootAlbumFullPathIsName() {
        val album = CustomAlbum(id = "1", name = "Vacations", parentPath = "")
        assertEquals("Vacations", album.fullPath)
    }

    @Test
    fun nestedAlbumFullPathCombinesParentAndName() {
        val album = CustomAlbum(id = "2", name = "Beach", parentPath = "Vacations")
        assertEquals("Vacations / Beach", album.fullPath)
    }

    @Test
    fun mediaUrisAreMaintainedInAlbum() {
        val album = CustomAlbum(id = "3", name = "Family", mediaUris = setOf("content://media/1", "content://media/2"))
        assertEquals(2, album.mediaUris.size)
    }
}
