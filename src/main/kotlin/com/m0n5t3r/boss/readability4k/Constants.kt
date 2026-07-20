package com.m0n5t3r.boss.readability4k

// Flags for different processing modes
const val FLAG_STRIP_UNLIKELYS = 0x1
const val FLAG_WEIGHT_CLASSES = 0x2
const val FLAG_CLEAN_CONDITIONALLY = 0x4

// Node types (from DOM specification)
const val ELEMENT_NODE = 1
const val TEXT_NODE = 3

// Default configuration values
const val DEFAULT_MAX_ELEMS_TO_PARSE = 0 // 0 means no limit
const val DEFAULT_N_TOP_CANDIDATES = 5
const val DEFAULT_CHAR_THRESHOLD = 500

// Element tags to score by default
val DEFAULT_TAGS_TO_SCORE = setOf("SECTION", "H2", "H3", "H4", "H5", "H6", "P", "TD", "PRE")

// Roles that are unlikely to contain article content
val UNLIKELY_ROLES =
    setOf("menu", "menubar", "complementary", "navigation", "alert", "alertdialog", "dialog")

// Elements that can be converted from DIV to P
val DIV_TO_P_ELEMS = setOf("BLOCKQUOTE", "DL", "DIV", "IMG", "OL", "P", "PRE", "TABLE", "UL")

// Exceptions when altering elements to DIV
val ALTER_TO_DIV_EXCEPTIONS = setOf("DIV", "ARTICLE", "SECTION", "P", "OL", "UL")

// Presentational attributes that should be removed
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

// Elements with deprecated size attributes
val DEPRECATED_SIZE_ATTRIBUTE_ELEMS = setOf("TABLE", "TH", "TD", "HR", "PRE")

// Phrasing elements (inline content)
val PHRASING_ELEMS =
    setOf(
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

// Classes that readability sets itself and should be preserved
val CLASSES_TO_PRESERVE = setOf("page")

// HTML entities that need to be escaped
val HTML_ESCAPE_MAP = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'")
