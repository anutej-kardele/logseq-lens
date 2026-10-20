package com.anutej.logseqpocket.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.BlockTagRow
import com.anutej.logseqpocket.data.db.CardRow
import com.anutej.logseqpocket.data.db.ReviewProgressEntity
import com.anutej.logseqpocket.data.repo.Deck
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.data.repo.StudyRepository
import com.anutej.logseqpocket.domain.PropChip
import com.anutej.logseqpocket.domain.PropertyFormatter
import com.anutej.logseqpocket.domain.RefIndex
import com.anutej.logseqpocket.domain.cards.CardRules
import com.anutej.logseqpocket.domain.cards.ReviewResult
import com.anutej.logseqpocket.domain.cards.StudySession
import com.anutej.logseqpocket.domain.markdown.Inline
import com.anutej.logseqpocket.domain.markdown.MarkdownParser
import com.anutej.logseqpocket.domain.markdown.MdBlock
import com.anutej.logseqpocket.ui.reader.BlockRowUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardView(
    val row: CardRow,
    val front: List<MdBlock>,
    val tags: List<BlockTagRow>,
    val answer: List<BlockRowUi>,
    val reviewInfo: List<PropChip>,
    val hasCloze: Boolean,
    val progress: ReviewProgressEntity?,
)

data class StudyUi(
    val loading: Boolean = true,
    val deck: Deck? = null,
    val session: StudySession? = null,
    val card: CardView? = null,
    val flipped: Boolean = false,
    val refs: RefIndex = RefIndex.EMPTY,
)

class StudyViewModel(
    private val study: StudyRepository,
    private val notes: NotesRepository,
    private val deckKey: String,
) : ViewModel() {
    private val _ui = MutableStateFlow(StudyUi())
    val ui: StateFlow<StudyUi> = _ui.asStateFlow()
    private var cards: Map<String, CardRow> = emptyMap()

    init {
        viewModelScope.launch {
            val deck = study.deck(deckKey)
            cards = study.cards()
            val refs = notes.refIndex.first()
            if (deck == null) {
                _ui.value = StudyUi(loading = false, refs = refs)
                return@launch
            }
            val restored = study.loadSession(deckKey)?.retain(deck.cardKeys.toSet())?.takeIf { it.total > 0 }
            val session = restored ?: StudySession.start(deckKey, deck.cardKeys, shuffle = false)
            _ui.value = StudyUi(loading = false, deck = deck, session = session, refs = refs)
            show(session)
        }
    }

    private suspend fun show(session: StudySession) {
        study.saveSession(session)
        val key = session.current
        val row = key?.let { cards[it] }
        val view = row?.let { load(it) }
        _ui.update { it.copy(session = session, card = view, flipped = false) }
    }

    private suspend fun load(row: CardRow): CardView {
        val block = notes.blocksByUuid(listOf(row.blockUuid))[row.blockUuid]
        val answer = block?.let { notes.subtree(it) }.orEmpty()
        val tags = notes.tagsForBlocks(answer.map { it.uuid } + row.blockUuid)
        val refs = _ui.value.refs
        val front = MarkdownParser.parse(row.content)
        return CardView(
            row = row,
            front = front,
            tags = tags[row.blockUuid].orEmpty().filterNot { CardRules.isCardTag(it.name) },
            answer = answer.map { b ->
                BlockRowUi(
                    block = b,
                    md = MarkdownParser.parse(b.content),
                    tags = tags[b.uuid].orEmpty(),
                    props = PropertyFormatter.format(b.propertiesJson, refs),
                    hasChildren = b.childCount > 0,
                    expanded = true,
                )
            },
            reviewInfo = PropertyFormatter.format(row.propertiesJson, refs).filter { it.kind == PropChip.Kind.Review },
            hasCloze = front.any { md -> md is MdBlock.Paragraph && md.inlines.any { it is Inline.Cloze } },
            progress = study.progress(row.cardKey),
        )
    }

    fun flip() = _ui.update { it.copy(flipped = !it.flipped) }

    fun next() = move { it.next() }
    fun previous() = move { it.previous() }

    fun mark(result: ReviewResult) {
        val s = _ui.value.session ?: return
        val card = _ui.value.card ?: return
        viewModelScope.launch {
            study.record(card.row, result, card.row.answerHash)
            show(s.mark(result))
        }
    }

    fun restart() = move { it.restart() }

    fun shuffle() {
        val deck = _ui.value.deck ?: return
        move { StudySession.start(deckKey, deck.cardKeys, shuffle = true) }
    }

    fun inOrder() {
        val deck = _ui.value.deck ?: return
        move { StudySession.start(deckKey, deck.cardKeys, shuffle = false) }
    }

    /** Starts a pass over cards marked Review Again (this session's, or previously saved ones). */
    fun reviewAgain() {
        val s = _ui.value.session ?: return
        viewModelScope.launch {
            val keys = s.againKeys.ifEmpty {
                _ui.value.deck?.cardKeys.orEmpty().filter { study.progress(it)?.lastResult == ReviewResult.Again.name }
            }
            if (keys.isNotEmpty()) show(StudySession.start(deckKey, keys, shuffle = s.shuffled, againOnly = true))
        }
    }

    private fun move(f: (StudySession) -> StudySession) {
        val s = _ui.value.session ?: return
        viewModelScope.launch { show(f(s)) }
    }
}
