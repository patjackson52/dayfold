package com.sloopworks.dayfold.client
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineNavTest {
  @Test fun `open sets the detail scale`() {
    val s = rootReducer(AppState(route = Route.Hubs, currentHubId = "h1"), OpenTimelineDetail(TimelineScale.Day))
    assertEquals(TimelineScale.Day, s.timelineDetail)
  }
  @Test fun `close clears it`() {
    val s = rootReducer(AppState(route = Route.Hubs, currentHubId = "h1", timelineDetail = TimelineScale.Hub), CloseTimelineDetail)
    assertEquals(null, s.timelineDetail)
  }
  @Test fun `back closes the timeline detail before the hub`() {
    val open = AppState(route = Route.Hubs, currentHubId = "h1", timelineDetail = TimelineScale.Day)
    assertEquals(CloseTimelineDetail, backAction(open))
    val closed = AppState(route = Route.Hubs, currentHubId = "h1")
    assertEquals(CloseHub, backAction(closed))
  }
}
