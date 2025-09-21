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
val DEFAULT_TAGS_TO_SCORE = "section,h2,h3,h4,h5,h6,p,td,pre"
    .uppercase()
    .split(",")
    .toSet()

// The default number of chars an article must have in order to return a result
const val DEFAULT_CHAR_THRESHOLD = 500
