package com.vitorpamplona.amethyst.service.zaps

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.EventInterface
import com.vitorpamplona.amethyst.service.model.zaps.UserZaps
import io.mockk.*
import org.junit.Assert
import org.junit.Test

class UserZapsTest {
  @Test
  fun nothing() {
    Assert.assertEquals(1, 1)
  }

  @Test
  fun user_without_zaps() {
    val actual = UserZaps.groupByUser(zaps = null)

    Assert.assertEquals(emptyList<Pair<Note, Note>>(), actual)
  }

  @Test
  fun group_by_user_with_just_one_user() {
    val u1 = mockk<Note>()
    val z1 = mockk<Note>()
    val z2 = mockk<Note>()
    val zaps: Map<Note, Note?> = mapOf(u1 to z1, u1 to z2)
    val actual = UserZaps.groupByUser(zaps)

    Assert.assertEquals(listOf(Pair(u1, z2)), actual)
  }

  @Test
  fun group_by_user() {
    // FIXME: not working yet...
//    IDEA:
//    [ (u1 -> z1) (u1 -> z2) (u2 -> z3) ]
//    [ (u1 -> z1 + z2) (u2 -> z3)]
    val u1 = mockk<Note>()
    val u2 = mockk<Note>()

    val z1 = mockk<Note>()
    val z2 = mockk<Note>()
    val z3 = mockk<Note>()
    every { z3.event } returns mockk<EventInterface>()

    val zaps: Map<Note, Note?> = mapOf(u1 to z1, u1 to z2, u2 to z3)
    val actual = UserZaps.groupByUser(zaps)

    Assert.assertEquals(
      listOf(Pair(u1, z1), Pair(u1, z2), Pair(u2, z3)),
      actual
    )
  }
}
