package com.amresalehin.emregallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryRepositoryStateTest {
    private val photo = MediaItem(1, "content://media/1", "Sunset.jpg", "image/jpeg", 1, 2, 512, 1200, 900, "DCIM/Camera/", false)
    private val video = MediaItem(2, "content://media/2", "Trip.mp4", "video/mp4", 3, 4, 1024, 1080, 1920, "Movies/Trips/", true, 1000)

    @Test fun queryMatchesNamesAndPathsCaseInsensitively() {
        assertEquals(listOf(photo), GalleryRepositoryState.filterByQuery(listOf(photo, video), "sunSET"))
        assertEquals(listOf(video), GalleryRepositoryState.filterByQuery(listOf(photo, video), "trips"))
    }

    @Test fun videoFilterOnlyReturnsVideosWhenEnabled() {
        assertEquals(listOf(video), GalleryRepositoryState.filterVideos(listOf(photo, video), true))
        assertEquals(listOf(photo, video), GalleryRepositoryState.filterVideos(listOf(photo, video), false))
    }

    @Test fun folderGroupingUsesRelativePathParent() {
        assertEquals(setOf("DCIM/Camera", "Movies/Trips"), GalleryRepositoryState.groupByFolder(listOf(photo, video)).keys)
    }

    @Test fun sortMediaCorrectlyOrdersByDateNameAndSize() {
        // Sunset.jpg: dateTaken=1, dateAdded=2, size=512
        // Trip.mp4: dateTaken=3, dateAdded=4, size=1024
        assertEquals(listOf(video, photo), GalleryRepositoryState.sortMedia(listOf(photo, video), "date_desc"))
        assertEquals(listOf(photo, video), GalleryRepositoryState.sortMedia(listOf(video, photo), "date_asc"))
        assertEquals(listOf(photo, video), GalleryRepositoryState.sortMedia(listOf(video, photo), "name_asc"))
        assertEquals(listOf(video, photo), GalleryRepositoryState.sortMedia(listOf(photo, video), "size_desc"))
    }
}
