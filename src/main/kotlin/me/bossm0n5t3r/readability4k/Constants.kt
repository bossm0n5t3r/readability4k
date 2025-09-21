package me.bossm0n5t3r.readability4k

const val FLAG_STRIP_UNLIKELYS = 0x1
const val FLAG_WEIGHT_CLASSES = 0x2
const val FLAG_CLEAN_CONDITIONALLY = 0x4

// Node types
const val ELEMENT_NODE = 1
const val TEXT_NODE = 3

// Max number of nodes supported by this parser. Default: 0 (no limit)
const val DEFAULT_MAX_ELEMS_TO_PARSE = 0

// The number of top candidates to consider when analysing how
// tight the competition is among candidates.
const val DEFAULT_N_TOP_CANDIDATES = 5

// Element tags to score by default.
val DEFAULT_TAGS_TO_SCORE =
    "section,h2,h3,h4,h5,h6,p,td,pre"
        .uppercase()
        .split(",")
        .toSet()

// The default number of chars an article must have in order to return a result
const val DEFAULT_CHAR_THRESHOLD = 500

val UNLIKELY_ROLES =
    setOf(
        "menu",
        "menubar",
        "complementary",
        "navigation",
        "alert",
        "alertdialog",
        "dialog",
    )

val DIV_TO_P_ELEMS =
    setOf(
        "BLOCKQUOTE",
        "DL",
        "DIV",
        "IMG",
        "OL",
        "P",
        "PRE",
        "TABLE",
        "UL",
    )

val ALTER_TO_DIV_EXCEPTIONS = setOf("DIV", "ARTICLE", "SECTION", "P", "OL", "UL")

val PRESENTATIONAL_ATTRIBUTES =
    setOf(
        "align",
        "background",
        "bgcolor",
        "border",
        "cellpadding",
        "cellspacing",
        "frame",
        "hspace",
        "rules",
        "style",
        "valign",
        "vspace",
    )

val DEPRECATED_SIZE_ATTRIBUTE_ELEMS = setOf("TABLE", "TH", "TD", "HR", "PRE")

// The commented-out elements qualify as phrasing content but tend to be
// removed by readability when put into paragraphs, so we ignore them here.
val PHRASING_ELEMS =
    setOf(
//    "CANVAS", "IFRAME", "SVG", "VIDEO",
        "ABBR",
        "AUDIO",
        "B",
        "BDO",
        "BR",
        "BUTTON",
        "CITE",
        "CODE",
        "DATA",
        "DATALIST",
        "DFN",
        "EM",
        "EMBED",
        "I",
        "IMG",
        "INPUT",
        "KBD",
        "LABEL",
        "MARK",
        "MATH",
        "METER",
        "NOSCRIPT",
        "OBJECT",
        "OUTPUT",
        "PROGRESS",
        "Q",
        "RUBY",
        "SAMP",
        "SCRIPT",
        "SELECT",
        "SMALL",
        "SPAN",
        "STRONG",
        "SUB",
        "SUP",
        "TEXTAREA",
        "TIME",
        "VAR",
        "WBR",
    )

// These are the classes that readability sets itself.
val CLASSES_TO_PRESERVE = setOf("page")

// These are the list of HTML entities that need to be escaped.
val HTML_ESCAPE_MAP =
    mapOf(
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
    )
