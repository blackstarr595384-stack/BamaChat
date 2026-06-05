package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PersonaCategory(val label: String) {
    PRODUCTIVITY("Produktivität"),
    CREATIVE("Kreativ"),
    TECHNICAL("Technisch"),
    EDUCATION("Bildung"),
    HEALTH("Gesundheit"),
    ENTERTAINMENT("Unterhaltung"),
    CUSTOM("Benutzerdefiniert")
}

data class PersonaListing(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PersonaCategory,
    val downloads: Int,
    val rating: Float,
    val empathyLevel: Int,
    val creativityLevel: Int,
    val directnessLevel: Int,
    val installed: Boolean = false
)

data class PersonaMarketplaceState(
    val listings: List<PersonaListing> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: PersonaCategory? = null,
    val selectedPersona: PersonaListing? = null,
    val showDetail: Boolean = false
)

@HiltViewModel
class PersonaMarketplaceViewModel @Inject constructor() : ViewModel() {

    private val samplePersonas = listOf(
        PersonaListing(
            id = "projektmanager", name = "Projektmanager",
            description = "Strukturierte Projektplanung mit Meilenstein-Tracking, Ressourcenmanagement und Risikoanalyse. Der ideale Assistent für komplexe Projekte.",
            author = "Bama Labs", category = PersonaCategory.PRODUCTIVITY,
            downloads = 28400, rating = 4.7f,
            empathyLevel = 6, creativityLevel = 5, directnessLevel = 8
        ),
        PersonaListing(
            id = "code-mentor", name = "Code Mentor",
            description = "Pair-Programming mit Fokus auf Best Practices, Clean Code und Architektur-Entscheidungen. Begleitet dich bei der täglichen Entwicklung.",
            author = "DevCommunity", category = PersonaCategory.TECHNICAL,
            downloads = 22100, rating = 4.8f,
            empathyLevel = 5, creativityLevel = 6, directnessLevel = 9
        ),
        PersonaListing(
            id = "kreativdirektor", name = "Kreativdirektor",
            description = "Brainstorming und kreative Konzeptentwicklung für Design, Marketing und Produktinnovation. Denkt ausserhalb der Box.",
            author = "Creative AI", category = PersonaCategory.CREATIVE,
            downloads = 18300, rating = 4.5f,
            empathyLevel = 7, creativityLevel = 10, directnessLevel = 4
        ),
        PersonaListing(
            id = "sprachtrainer", name = "Sprachtrainer",
            description = "Interaktives Sprachtraining mit Korrektur, Aussprachehilfe und kulturellen Kontextinformationen für 20+ Sprachen.",
            author = "LinguaAI", category = PersonaCategory.EDUCATION,
            downloads = 31200, rating = 4.6f,
            empathyLevel = 8, creativityLevel = 4, directnessLevel = 7
        ),
        PersonaListing(
            id = "datenanalyst", name = "Datenanalyst",
            description = "Dateninterpretation, Mustererkennung und Visualisierungsvorschläge für datengetriebene Entscheidungen.",
            author = "DataViz Inc", category = PersonaCategory.TECHNICAL,
            downloads = 15900, rating = 4.4f,
            empathyLevel = 3, creativityLevel = 5, directnessLevel = 9
        ),
        PersonaListing(
            id = "schreibcoach", name = "Schreibcoach",
            description = "Texte verfeinern mit Stil-, Grammatik- und Strukturfeedback. Ideal für Autoren, Journalisten und Content Creator.",
            author = "WriteWell", category = PersonaCategory.CREATIVE,
            downloads = 19700, rating = 4.5f,
            empathyLevel = 7, creativityLevel = 8, directnessLevel = 5
        ),
        PersonaListing(
            id = "mindfulness-guide", name = "Mindfulness Guide",
            description = "Geführte Meditationen, Achtsamkeitsübungen und Stressbewältigungstechniken für mehr innere Ruhe.",
            author = "WellBeing AI", category = PersonaCategory.HEALTH,
            downloads = 26800, rating = 4.9f,
            empathyLevel = 10, creativityLevel = 6, directnessLevel = 2
        ),
        PersonaListing(
            id = "debattierpartner", name = "Debattierpartner",
            description = "Strukturierte Debatten mit Pro/Contra-Argumenten, Quellenangaben und logischer Schlussfolgerung.",
            author = "LogicLabs", category = PersonaCategory.ENTERTAINMENT,
            downloads = 12100, rating = 4.3f,
            empathyLevel = 4, creativityLevel = 7, directnessLevel = 8
        )
    )

    private val _state = MutableStateFlow(PersonaMarketplaceState())
    val state: StateFlow<PersonaMarketplaceState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(listings = samplePersonas)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setCategory(category: PersonaCategory?) {
        _state.value = _state.value.copy(selectedCategory = if (_state.value.selectedCategory == category) null else category)
    }

    fun selectPersona(persona: PersonaListing) {
        _state.value = _state.value.copy(selectedPersona = persona, showDetail = true)
    }

    fun dismissDetail() {
        _state.value = _state.value.copy(selectedPersona = null, showDetail = false)
    }

    fun toggleInstall(personaId: String) {
        val updated = _state.value.listings.map { p ->
            if (p.id == personaId) p.copy(installed = !p.installed) else p
        }
        _state.value = _state.value.copy(listings = updated)
        _state.value.selectedPersona?.let { selected ->
            if (selected.id == personaId) {
                _state.value = _state.value.copy(
                    selectedPersona = selected.copy(installed = !selected.installed)
                )
            }
        }
    }
}
