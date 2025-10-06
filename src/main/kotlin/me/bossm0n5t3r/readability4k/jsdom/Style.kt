package me.bossm0n5t3r.readability4k.jsdom

class Style(
    private val node: Element,
) {
    companion object {
        private val STYLE_MAP =
            mapOf(
                "alignmentBaseline" to "alignment-baseline",
                "background" to "background",
                "backgroundAttachment" to "background-attachment",
                "backgroundClip" to "background-clip",
                "backgroundColor" to "background-color",
                "backgroundImage" to "background-image",
                "backgroundOrigin" to "background-origin",
                "backgroundPosition" to "background-position",
                "backgroundPositionX" to "background-position-x",
                "backgroundPositionY" to "background-position-y",
                "backgroundRepeat" to "background-repeat",
                "backgroundRepeatX" to "background-repeat-x",
                "backgroundRepeatY" to "background-repeat-y",
                "backgroundSize" to "background-size",
                "baselineShift" to "baseline-shift",
                "border" to "border",
                "borderBottom" to "border-bottom",
                "borderBottomColor" to "border-bottom-color",
                "borderBottomLeftRadius" to "border-bottom-left-radius",
                "borderBottomRightRadius" to "border-bottom-right-radius",
                "borderBottomStyle" to "border-bottom-style",
                "borderBottomWidth" to "border-bottom-width",
                "borderCollapse" to "border-collapse",
                "borderColor" to "border-color",
                "borderImage" to "border-image",
                "borderImageOutset" to "border-image-outset",
                "borderImageRepeat" to "border-image-repeat",
                "borderImageSlice" to "border-image-slice",
                "borderImageSource" to "border-image-source",
                "borderImageWidth" to "border-image-width",
                "borderLeft" to "border-left",
                "borderLeftColor" to "border-left-color",
                "borderLeftStyle" to "border-left-style",
                "borderLeftWidth" to "border-left-width",
                "borderRadius" to "border-radius",
                "borderRight" to "border-right",
                "borderRightColor" to "border-right-color",
                "borderRightStyle" to "border-right-style",
                "borderRightWidth" to "border-right-width",
                "borderSpacing" to "border-spacing",
                "borderStyle" to "border-style",
                "borderTop" to "border-top",
                "borderTopColor" to "border-top-color",
                "borderTopLeftRadius" to "border-top-left-radius",
                "borderTopRightRadius" to "border-top-right-radius",
                "borderTopStyle" to "border-top-style",
                "borderTopWidth" to "border-top-width",
                "borderWidth" to "border-width",
                "bottom" to "bottom",
                "boxShadow" to "box-shadow",
                "boxSizing" to "box-sizing",
                "captionSide" to "caption-side",
                "clear" to "clear",
                "clip" to "clip",
                "clipPath" to "clip-path",
                "clipRule" to "clip-rule",
                "color" to "color",
                "colorInterpolation" to "color-interpolation",
                "colorInterpolationFilters" to "color-interpolation-filters",
                "colorProfile" to "color-profile",
                "colorRendering" to "color-rendering",
                "content" to "content",
                "counterIncrement" to "counter-increment",
                "counterReset" to "counter-reset",
                "cursor" to "cursor",
                "direction" to "direction",
                "display" to "display",
                "dominantBaseline" to "dominant-baseline",
                "emptyCells" to "empty-cells",
                "enableBackground" to "enable-background",
                "fill" to "fill",
                "fillOpacity" to "fill-opacity",
                "fillRule" to "fill-rule",
                "filter" to "filter",
                "cssFloat" to "float",
                "floodColor" to "flood-color",
                "floodOpacity" to "flood-opacity",
                "font" to "font",
                "fontFamily" to "font-family",
                "fontSize" to "font-size",
                "fontStretch" to "font-stretch",
                "fontStyle" to "font-style",
                "fontVariant" to "font-variant",
                "fontWeight" to "font-weight",
                "glyphOrientationHorizontal" to "glyph-orientation-horizontal",
                "glyphOrientationVertical" to "glyph-orientation-vertical",
                "height" to "height",
                "imageRendering" to "image-rendering",
                "kerning" to "kerning",
                "left" to "left",
                "letterSpacing" to "letter-spacing",
                "lightingColor" to "lighting-color",
                "lineHeight" to "line-height",
                "listStyle" to "list-style",
                "listStyleImage" to "list-style-image",
                "listStylePosition" to "list-style-position",
                "listStyleType" to "list-style-type",
                "margin" to "margin",
                "marginBottom" to "margin-bottom",
                "marginLeft" to "margin-left",
                "marginRight" to "margin-right",
                "marginTop" to "margin-top",
                "marker" to "marker",
                "markerEnd" to "marker-end",
                "markerMid" to "marker-mid",
                "markerStart" to "marker-start",
                "mask" to "mask",
                "maxHeight" to "max-height",
                "maxWidth" to "max-width",
                "minHeight" to "min-height",
                "minWidth" to "min-width",
                "opacity" to "opacity",
                "orphans" to "orphans",
                "outline" to "outline",
                "outlineColor" to "outline-color",
                "outlineOffset" to "outline-offset",
                "outlineStyle" to "outline-style",
                "outlineWidth" to "outline-width",
                "overflow" to "overflow",
                "overflowX" to "overflow-x",
                "overflowY" to "overflow-y",
                "padding" to "padding",
                "paddingBottom" to "padding-bottom",
                "paddingLeft" to "padding-left",
                "paddingRight" to "padding-right",
                "paddingTop" to "padding-top",
                "page" to "page",
                "pageBreakAfter" to "page-break-after",
                "pageBreakBefore" to "page-break-before",
                "pageBreakInside" to "page-break-inside",
                "pointerEvents" to "pointer-events",
                "position" to "position",
                "quotes" to "quotes",
                "resize" to "resize",
                "right" to "right",
                "shapeRendering" to "shape-rendering",
                "size" to "size",
                "speak" to "speak",
                "src" to "src",
                "stopColor" to "stop-color",
                "stopOpacity" to "stop-opacity",
                "stroke" to "stroke",
                "strokeDasharray" to "stroke-dasharray",
                "strokeDashoffset" to "stroke-dashoffset",
                "strokeLinecap" to "stroke-linecap",
                "strokeLinejoin" to "stroke-linejoin",
                "strokeMiterlimit" to "stroke-miterlimit",
                "strokeOpacity" to "stroke-opacity",
                "strokeWidth" to "stroke-width",
                "tableLayout" to "table-layout",
                "textAlign" to "text-align",
                "textAnchor" to "text-anchor",
                "textDecoration" to "text-decoration",
                "textIndent" to "text-indent",
                "textLineThrough" to "text-line-through",
                "textLineThroughColor" to "text-line-through-color",
                "textLineThroughMode" to "text-line-through-mode",
                "textLineThroughStyle" to "text-line-through-style",
                "textLineThroughWidth" to "text-line-through-width",
                "textOverflow" to "text-overflow",
                "textOverline" to "text-overline",
                "textOverlineColor" to "text-overline-color",
                "textOverlineMode" to "text-overline-mode",
                "textOverlineStyle" to "text-overline-style",
                "textOverlineWidth" to "text-overline-width",
                "textRendering" to "text-rendering",
                "textShadow" to "text-shadow",
                "textTransform" to "text-transform",
                "textUnderline" to "text-underline",
                "textUnderlineColor" to "text-underline-color",
                "textUnderlineMode" to "text-underline-mode",
                "textUnderlineStyle" to "text-underline-style",
                "textUnderlineWidth" to "text-underline-width",
                "top" to "top",
                "unicodeBidi" to "unicode-bidi",
                "unicodeRange" to "unicode-range",
                "vectorEffect" to "vector-effect",
                "verticalAlign" to "vertical-align",
                "visibility" to "visibility",
                "whiteSpace" to "white-space",
                "widows" to "widows",
                "width" to "width",
                "wordBreak" to "word-break",
                "wordSpacing" to "word-spacing",
                "wordWrap" to "word-wrap",
                "writingMode" to "writing-mode",
                "zIndex" to "z-index",
                "zoom" to "zoom",
            )
    }

    fun getStyle(styleName: String): String? {
        val attr = node.getAttribute("style") ?: return null
        val styles = attr.split(";")

        for (style in styles) {
            val parts = style.split(":")
            if (parts.size >= 2 && parts[0].trim() == styleName) {
                return parts[1].trim()
            }
        }
        return null
    }

    fun setStyle(
        styleName: String,
        styleValue: String,
    ) {
        var value = node.getAttribute("style") ?: ""
        var index = 0

        do {
            val next = value.indexOf(";", index) + 1
            val length = if (next > 0) next - index - 1 else value.length - index
            val style = value.substring(index, index + length)

            if (style.substringBefore(":").trim() == styleName) {
                value = value.take(index).trim() +
                    if (next > 0) " " + value.substring(next).trim() else ""
                break
            }
            index = next
        } while (index > 0)

        value += " $styleName: $styleValue;"
        node.setAttribute("style", value.trim())
    }

    operator fun get(property: String): String? {
        val cssName = STYLE_MAP[property] ?: property
        return getStyle(cssName)
    }

    operator fun set(
        property: String,
        value: String,
    ) {
        val cssName = STYLE_MAP[property] ?: property
        setStyle(cssName, value)
    }
}
