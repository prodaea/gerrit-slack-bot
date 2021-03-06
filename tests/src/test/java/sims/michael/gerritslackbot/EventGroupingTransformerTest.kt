package sims.michael.gerritslackbot

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.toFlowable
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sims.michael.gerritslackbot.container.ObjectMappers
import sims.michael.gerritslackbot.container.defaultModule
import sims.michael.gerritslackbot.model.*

class EventGroupingTransformerTest {

    private lateinit var objectMapper: ObjectMapper

    private lateinit var stringToEvent: StringToEventTransformer

    @Before
    fun setUp() {
        val container = Kodein {
            import(defaultModule)
        }
        stringToEvent = container.instance()
        objectMapper = container.instance(ObjectMappers.JSON)
    }

    @Test
    fun can_exclude_events_based_on_subject() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("*", "*", "^WIP: ", null),
                ChangeMatcher("*", "*", "*", "channel")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertFalse(groups.any { it.events.any { it.change.id == "0" } })
    }

    @Test
    fun can_match_events_based_on_project() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("Froboztic", "*", "*", "otherChannel"),
                ChangeMatcher("*", "*", "*", "channel")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertNotNull(groups.firstOrNull { it.project == "Froboztic" })
        assertEquals("otherChannel", groups.first { it.project == "Froboztic" }.channel)
    }

    @Test
    fun can_match_events_based_on_branch() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("*", "feature-two", "*", "otherChannel"),
                ChangeMatcher("*", "*", "*", "channel")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertNotNull(groups.firstOrNull { it.branch == "feature-two" })
        assertEquals("otherChannel", groups.first { it.branch == "feature-two" }.channel)
    }

    @Test
    fun can_match_events_that_are_only_verifications() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("*", "*", "*", channel = null, isVerificationOnly = true),
                ChangeMatcher("*", "*", "*", "channel")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertFalse("Change with only a verification vote was matched",
                groups.any { it.events.any { it.change.id == "2" } })
        assertTrue("Change with both a verification vote and a code review vote was not matched",
                groups.any { it.events.any { it.change.id == "3" } })
    }

    @Test
    fun can_match_events_based_on_change_kind() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("*", "*", "*", "channel", changeKind = "rework")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertFalse("Some of the changes are not reworks",
                groups.any { it.events.any { it is PatchSetCreatedEvent && it.patchSet.kind != ChangeKind.REWORK } })
    }

    @Test
    fun change_kind_matches_for_patch_set_created_events_only() {
        val eventMatchingTransformer = EventGroupingTransformer(listOf(
                ChangeMatcher("*", "*", "*", "channel", changeKind = "rework")
        ))
        val groups = getEventGroupsWithTransformer(eventMatchingTransformer)
        assertTrue("Change kind matcher should be applied to PatchSetCreatedEvent only",
                groups.any { it.events.any { it.change.id == "6" } })
    }

    private fun getEventGroupsWithTransformer(eventGroupingTransformer: EventGroupingTransformer): List<EventGroup<*>> =
            this::class.java.getResourceAsStream("test/json/events-for-matching.txt")
                    .bufferedReader().lineSequence().toFlowable()
                    .compose(stringToEvent)
                    .ofType<ChangeEvent>()
                    .buffer(Int.MAX_VALUE)
                    .compose(eventGroupingTransformer).blockingIterable().toList()
}
